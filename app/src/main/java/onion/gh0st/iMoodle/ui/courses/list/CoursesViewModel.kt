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
import onion.gh0st.iMoodle.domain.usecase.course.GetCourses
import onion.gh0st.iMoodle.domain.usecase.course.GetFavouriteCourseIds
import onion.gh0st.iMoodle.domain.usecase.course.RefreshCourses
import onion.gh0st.iMoodle.domain.usecase.course.ToggleFavouriteCourse
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import javax.inject.Inject

/**
 * Estado observable de la pantalla de listado de cursos.
 */
data class CoursesUiState(
    val isLoading: Boolean = true,
    val courses: List<CourseUi> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `CoursesFragment`.
 */
sealed interface CoursesUiEvent {
    /**
     * Mostrar un mensaje de error en pantalla.
     *
     * @property messageResId Recurso del texto del mensaje.
     * @property arg Valor que sustituye a `%1$s` en el texto, si lo usa.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : CoursesUiEvent
}

/**
 * "Cerebro" del listado de cursos.
 *
 * ## Qué hace
 * 1. Carga los cursos de la base de datos local.
 * 2. Consulta los favoritos guardados en el teléfono.
 * 3. Ordena la lista para que los favoritos aparezcan primero.
 * 4. Gestiona el evento de pulsar la estrella para añadir o quitar
 *    un curso de favoritos.
 *
 * ## Capas
 *
 * No conoce el repositorio de cursos ni el de sesión: usa casos de
 * uso ([GetCourses], [RefreshCourses], [ObserveSession]). Así la
 * pantalla depende del contrato de dominio y no de la implementación
 * de Moodle ni de Room.
 *
 * @param getCourses Observa los cursos cacheados del usuario.
 * @param refreshCourses Descarga los cursos desde Moodle.
 * @param observeSession Lectura de la sesión activa.
 * @param getFavouriteCourseIds Lee los favoritos del dispositivo.
 * @param toggleFavouriteCourse Marca o desmarca un curso.
 */
@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val getCourses: GetCourses,
    private val refreshCourses: RefreshCourses,
    private val observeSession: ObserveSession,
    private val getFavouriteCourseIds: GetFavouriteCourseIds,
    private val toggleFavouriteCourse: ToggleFavouriteCourse,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoursesUiState(isLoading = true))
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    private val _events = Channel<CoursesUiEvent>(Channel.BUFFERED)
    val events: Flow<CoursesUiEvent> = _events.receiveAsFlow()

    private var rawCourses: List<Course> = emptyList()
    private var favouriteIds: Set<Int> = emptySet()

    /**
     * Cursos cuyo cambio de favorito está en vuelo.
     *
     * ## Por qué existe
     *
     * Si el usuario pulsa dos veces seguidas la estrella, la UI puede
     * no haber repintado todavía y ambas pulsaciones leen el mismo
     * `isFavorite` antiguo. Las dos corrutinas escribirían el **mismo**
     * valor (por ejemplo, `true`), así que dos toques rápidos para
     * desmarcar acabarían dejando el curso marcado. Este conjunto
     * ignora la segunda pulsación mientras la primera no termine.
     *
     * Se modifica solo desde el hilo principal (`viewModelScope`), así
     * que no necesita sincronización adicional.
     */
    private val favouriteUpdatesInFlight = mutableSetOf<Int>()

    init {
        observeSessionAndCourses()
    }

    /**
     * Recarga manualmente la lista de cursos.
     *
     * Se activa cuando el usuario desliza hacia abajo en la pantalla
     * (pull-to-refresh) para pedir los datos más recientes.
     */
    fun refresh() {
        val userId = observeSession.currentUserId ?: 0
        if (userId == 0) {
            viewModelScope.launch {
                _events.send(CoursesUiEvent.ShowError(messageResId = R.string.error_identify_user))
            }
            return
        }
        viewModelScope.launch {
            refreshForUser(userId)
        }
    }

    /**
     * Cambia el estado de favorito de un curso.
     *
     * ## Cómo funciona
     * Pide al caso de uso que guarde el cambio, y luego vuelve a
     * leer la lista de favoritos para actualizar la pantalla y
     * reordenar los cursos.
     *
     * ## Protección contra dobles pulsaciones
     *
     * Mientras hay un cambio en vuelo para un curso, las pulsaciones
     * adicionales sobre ese mismo curso se ignoran. Sin esta guarda,
     * dos toques rápidos calculaban el mismo valor desde el estado
     * antiguo y el segundo podía deshacer la intención del usuario.
     *
     * @param course El curso que se va a marcar o desmarcar.
     */
    fun toggleFavourite(course: CourseUi) {
        if (!favouriteUpdatesInFlight.add(course.id)) return

        viewModelScope.launch {
            try {
                toggleFavouriteCourse(course.id, !course.isFavorite)
                    .onSuccess {
                        getFavouriteCourseIds()
                            .onSuccess { ids ->
                                favouriteIds = ids
                                updateCoursesWithFavourites()
                            }
                            .onFailure { exception ->
                                Logging.logError(TAG, "getFavouriteCourseIds after toggle", exception)
                            }
                    }
                    .onFailure { exception ->
                        Logging.logError(TAG, "toggleFavourite(courseId=${course.id})", exception)
                        _events.send(
                            CoursesUiEvent.ShowError(messageResId = R.string.error_unexpected),
                        )
                    }
            } finally {
                favouriteUpdatesInFlight.remove(course.id)
            }
        }
    }

    /**
     * Vigila quién es el usuario actual.
     *
     * Su propósito es detectar cuándo un usuario inicia sesión o cambia
     * de cuenta. Si detecta un usuario válido, empieza a cargar sus cursos.
     */
    private fun observeSessionAndCourses() {
        viewModelScope.launch {
            observeSession.session
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
     * Maneja el caso en el que no hay un usuario logueado.
     *
     * Su propósito es limpiar la pantalla y mostrar un mensaje de error
     * para que el usuario sepa que algo ha fallado con su sesión.
     */
    private suspend fun onUserNotAvailable() {
        _uiState.update { it.copy(isLoading = false, courses = emptyList()) }
        _events.send(CoursesUiEvent.ShowError(messageResId = R.string.error_identify_user))
    }

    /**
     * Observa los cursos de un usuario específico.
     *
     * Primero carga los cursos favoritos (que viven en el dispositivo y
     * por tanto están disponibles sin red) y después lanza dos tareas al
     * mismo tiempo: una para descargar los cursos de internet y otra para
     * mostrarlos en la pantalla en cuanto estén listos.
     *
     * Los favoritos se cargan **antes** de tocar la red a propósito: antes
     * se cargaban solo si la descarga tenía éxito, así que sin conexión
     * la lista aparecía sin estrellas ni orden de favoritos.
     *
     * @param userId El identificador del usuario.
     */
    private suspend fun observeCoursesForUser(userId: Int) = coroutineScope {
        loadFavouriteIds()
        launch { refreshForUser(userId) }
        launch {
            getCourses(userId).collect { courses ->
                rawCourses = courses
                updateCoursesWithFavourites()
            }
        }
    }

    /**
     * Actualiza la lista de cursos aplicando el orden de favoritos.
     *
     * Su propósito es asegurarse de que los cursos con estrella
     * aparezcan siempre en la parte superior de la pantalla.
     */
    private fun updateCoursesWithFavourites() {
        val sortedAndMappedCourses = rawCourses
            .sortedWith(
                compareByDescending<Course> { it.id in favouriteIds }
                    .thenBy { it.fullname.lowercase() },
            )
            .map { course -> course.toUi(isFavourite = course.id in favouriteIds) }

        _uiState.update {
            it.copy(isLoading = false, courses = sortedAndMappedCourses)
        }
    }

    /**
     * Carga desde el dispositivo los identificadores de los cursos
     * favoritos y los guarda para ordenar y marcar la lista.
     *
     * Si la lectura falla se registra el error y se sigue con la lista
     * sin favoritos, en lugar de impedir ver los cursos.
     */
    private suspend fun loadFavouriteIds() {
        getFavouriteCourseIds()
            .onSuccess { ids -> favouriteIds = ids }
            .onFailure { exception ->
                Logging.logError(TAG, "getFavouriteCourseIds", exception)
            }
    }

    /**
     * Sincroniza los cursos de un usuario con los datos más recientes.
     *
     * Su propósito es pedir a internet que actualice la información
     * y avisar a la pantalla de que debe redibujarse.
     *
     * @param userId El identificador del usuario.
     */
    private suspend fun refreshForUser(userId: Int) {
        _uiState.update { it.copy(isLoading = true) }
        try {
            refreshCourses(userId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Logging.logError(TAG, "refreshCourses(userId=$userId)", exception)
            _events.send(CoursesUiEvent.ShowError(messageResId = R.string.error_unexpected))
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Transforma un curso de datos puros a un modelo de pantalla.
     *
     * Su propósito es adaptar la información para que la lista
     * pueda dibujarla fácilmente, añadiendo el estado de favorito.
     *
     * @param isFavourite Si el curso debe aparecer como favorito.
     * @return El curso listo para mostrarse.
     */
    private fun Course.toUi(isFavourite: Boolean) = CourseUi(
        id = id,
        name = fullname,
        pendingTasks = 0,
        imageUrl = imageUrl,
        isFavorite = isFavourite,
        progress = progress ?: 0,
    )

    private companion object {
        const val TAG = "CoursesViewModel"
    }
}