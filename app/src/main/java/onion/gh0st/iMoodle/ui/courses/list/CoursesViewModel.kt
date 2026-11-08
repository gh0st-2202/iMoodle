package onion.gh0st.iMoodle.ui.courses.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Estado observable de la pantalla de listado de cursos.
 *
 * ## Por qué no incluye errores
 *
 * Porque los errores son eventos efímeros
 * ([CoursesUiEvent.ShowError]), no estados. Si estuvieran en el
 * estado, se re-emitirían en cada re-suscripción (por ejemplo, al
 * rotar), mostrando el mismo toast varias veces.
 *
 * @property isLoading `true` mientras hay una sincronización en
 *   curso. El `SwipeRefreshLayout` lo usa para mostrar la rueda.
 * @property courses Lista de cursos ya mapeados a `CourseUi`,
 *   ordenados alfabéticamente por nombre.
 */
data class CoursesUiState(
    val isLoading: Boolean = true,
    val courses: List<CourseUi> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `CoursesFragment`.
 *
 * ## Por qué se usa un `Channel` y no un `StateFlow`
 *
 * Porque un `Channel` con `receiveAsFlow` garantiza que cada evento
 * se entrega a **un** consumidor **una** vez. Un `StateFlow`, en
 * cambio, re-emite el último valor a cada nuevo suscriptor. Para
 * eventos efímeros, eso sería un desastre (toasts repetidos).
 */
sealed interface CoursesUiEvent {

    /**
     * Muestra un toast de error.
     *
     * ## Por qué el mensaje es genérico
     *
     * Porque el detalle técnico del error va al log, no a la UI. El
     * usuario ve un mensaje amable; el desarrollador ve el detalle
     * en logcat.
     *
     * ## Por qué `@param:StringRes`
     *
     * Porque `R.string.error_*` es un recurso de string, no un
     * string literal. El prefijo `param:` es necesario para que
     * Kotlin no avise sobre la ubicación de la anotación.
     *
     * @property messageResId ID del recurso de string a mostrar.
     * @property arg Argumento opcional para formatear. Si es `null`,
     *   se usa el string tal cual.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : CoursesUiEvent
}

/**
 * ViewModel de la pantalla de listado de cursos.
 *
 * ## Estrategia offline-first
 *
 * El listado de cursos sigue el patrón offline-first:
 *
 * - La UI **nunca** depende de la red para pintarse.
 * - La red solo **actualiza** los datos locales.
 * - La UI observa Room (a través de `getCoursesFlow`) y se actualiza
 *   sola cuando cambian los datos.
 *
 * ## Sincronización con la sesión
 *
 * El `userId` **no** se captura en el constructor. En su lugar, se
 * observa `sessionRepo.session` y se re-suscribe al `Flow` de cursos
 * cuando cambia el `userId`. El motivo es que, en auto-login
 * offline, la sesión puede recuperarse sin perfil (`user = null`), y
 * en ese caso el `userId` sería 0. Observar la sesión permite
 * re-suscribirse cuando llegue el `userId` real.
 *
 * ## Errores
 *
 * Los errores se loguean con `Logging.logError` y se emiten como
 * `ShowError`. El detalle técnico no llega a la UI.
 *
 * @param courseRepository Repositorio con persistencia local.
 * @param sessionRepo Fuente de la sesión activa (para el `userId`).
 */
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val sessionRepo: SessionRepository,
) : ViewModel() {

    /** Estado interno de la pantalla. */
    private val _uiState = MutableStateFlow(CoursesUiState(isLoading = true))

    /** Estado observable de la pantalla. */
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<CoursesUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<CoursesUiEvent> = _events.receiveAsFlow()

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * Empieza a observar la sesión y los cursos.
     */
    init {
        observeSessionAndCourses()
    }

    /**
     * Refresco manual de la lista de cursos.
     *
     * ## Comportamiento
     *
     * - Si el `userId` actual es 0 (sesión aún sin perfil), emite un
     *   `ShowError` y no consulta la red. No tiene sentido consultar
     *   el usuario 0.
     * - Si hay `userId`, lanza un refresco para ese usuario.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa pull-to-refresh.
     *
     * ## Sobre el paralelismo
     *
     * Si el usuario pulsa pull-to-refresh mientras el refresco
     * automático está en curso, ambos corren en paralelo. No es
     * óptimo, pero `refreshCourses` es idempotente y el estado
     * termina convergiendo. Se podría añadir un flag para evitarlo,
     * pero no es crítico.
     */
    fun refresh() {
        val userId = sessionRepo.session.value?.user?.id ?: 0
        if (userId == 0) {
            viewModelScope.launch {
                _events.send(
                    CoursesUiEvent.ShowError(messageResId = R.string.error_identify_user),
                )
            }
            return
        }
        viewModelScope.launch {
            refreshForUser(userId)
        }
    }

    // --- Métodos internos ---

    /**
     * Observa la sesión y re-suscribe al `Flow` de cursos cuando
     * cambia el `userId`.
     *
     * ## Qué hace
     *
     * 1. Observa `sessionRepo.session`.
     * 2. Mapea cada emisión al `userId` del usuario (o 0 si no hay
     *    usuario).
     * 3. Con `distinctUntilChanged`, evita re-suscripciones cuando
     *    la sesión emite con el mismo `userId`.
     * 4. Con `collectLatest`, cancela el bloque anterior cuando
     *    llega un `userId` nuevo. Así no quedan corrutinas
     *    huérfanas observando cursos del usuario viejo.
     * 5. Según el `userId`:
     *    - **0**: llama a `onUserNotAvailable` (error).
     *    - **> 0**: observa los cursos para ese usuario.
     */
    private fun observeSessionAndCourses() {
        viewModelScope.launch {
            sessionRepo.session
                .map { activeSession -> activeSession?.user?.id ?: 0 }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == 0) {
                        onUserNotAvailable()
                    } else {
                        observeCoursesForUser(userId)
                    }
                }
        }
    }

    /**
     * Maneja el caso "sin usuario disponible".
     *
     * ## Qué hace
     *
     * 1. Apaga `isLoading` (para que el `SwipeRefreshLayout` no se
     *    quede girando).
     * 2. Limpia la lista (por si había cursos de un usuario
     *    anterior).
     * 3. Emite un `ShowError`.
     *
     * ## Por qué no spamea el toast
     *
     * Porque se llama solo cuando cambia de `userId > 0` a
     * `userId == 0` (gracias a `distinctUntilChanged`). Si el
     * `userId` sigue siendo 0 en emisiones posteriores, no se
     * vuelve a llamar.
     */
    private suspend fun onUserNotAvailable() {
        _uiState.update { it.copy(isLoading = false, courses = emptyList()) }
        _events.send(
            CoursesUiEvent.ShowError(messageResId = R.string.error_identify_user),
        )
    }

    /**
     * Observa los cursos de un usuario concreto y lanza un refresco
     * automático en paralelo.
     *
     * ## Qué hace
     *
     * Dentro de un `coroutineScope`:
     *
     * 1. Lanza `refreshForUser` en paralelo (red → Room).
     * 2. Lanza la recolección de `getCoursesFlow` (Room → UI).
     *
     * Los dos `launch` corren en paralelo:
     *
     * - El primero actualiza Room con datos frescos.
     * - El segundo emite los datos de Room a la UI.
     *
     * El segundo puede emitir antes que el primero (con datos
     * cacheados o vacío); la UI se actualiza varias veces hasta
     * converger. Es lo esperado en offline-first.
     *
     * ## Por qué `coroutineScope`
     *
     * Porque garantiza que, si `collectLatest` cancela este bloque
     * (porque llegó otro `userId`), se cancelan también los `launch`
     * hijos. Sin esto, el refresco del usuario viejo seguiría
     * corriendo y podría emitir un toast de error después de que el
     * nuevo usuario ya esté cargado.
     *
     * @param userId ID del usuario logueado.
     */
    private suspend fun observeCoursesForUser(userId: Int) = coroutineScope {
        launch { refreshForUser(userId) }
        launch {
            courseRepository.getCoursesFlow(userId).collect { courses ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        courses = courses
                            .sortedBy { course -> course.fullname.lowercase() }
                            .map { course -> course.toUi() },
                    )
                }
            }
        }
    }

    /**
     * Sincroniza los cursos de un usuario con la red y actualiza
     * `isLoading`.
     *
     * ## Manejo de errores
     *
     * 1. Loguea el detalle con `Logging.logError` (logcat).
     * 2. Emite un `ShowError` con un mensaje genérico.
     * 3. **No limpia** `courses` para no borrar los datos cacheados
     *    que el usuario ya estaba viendo (patrón offline-first).
     *
     * @param userId ID del usuario logueado.
     */
    private suspend fun refreshForUser(userId: Int) {
        _uiState.update { it.copy(isLoading = true) }
        try {
            courseRepository.refreshCourses(userId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Logging.logError(TAG, "refreshCourses(userId=$userId)", exception)
            _events.send(
                CoursesUiEvent.ShowError(messageResId = R.string.error_unexpected),
            )
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Mapea un `Course` de dominio a un `CourseUi` de presentación.
     *
     * ## Qué hace
     *
     * - Renombra `fullname` a `name`.
     * - Normaliza `progress`: si es `null`, lo convierte a 0.
     * - Rellena `pendingTasks` con 0 (deuda: pendiente de
     *   implementar).
     * - Rellena `isFavorite` con `false` (deuda: pendiente de
     *   implementar).
     *
     * @receiver Curso de dominio.
     * @return Modelo de presentación listo para el adapter.
     */
    private fun Course.toUi() = CourseUi(
        id = id,
        name = fullname,
        pendingTasks = 0,
        imageUrl = imageUrl,
        isFavorite = false,
        progress = progress ?: 0,
    )

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "CoursesViewModel"
    }
}