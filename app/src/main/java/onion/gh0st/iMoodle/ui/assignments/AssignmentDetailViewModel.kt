package onion.gh0st.iMoodle.ui.assignments

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.usecase.assignment.GetAssignmentDetail
import javax.inject.Inject

/**
 * Estado observable de la pantalla de detalle de una tarea.
 *
 * ## Qué es un "estado observable"
 *
 * Es un objeto que contiene todos los datos que la UI necesita para
 * pintarse en un momento dado. La UI lo observa y se actualiza
 * automáticamente cuando cambia.
 *
 * ## Por qué solo contiene datos "persistibles"
 *
 * El estado **no incluye errores**. Los errores son eventos efímeros
 * (ver [AssignmentDetailUiEvent.ShowError]), no estados. El motivo
 * es que un error no es algo que "esté pasando": es algo que "ha
 * pasado" y que la UI debe mostrar una vez. Si estuviera en el
 * estado, se re-emitiría cada vez que la UI se re-suscribiera (por
 * ejemplo, al rotar), mostrando el mismo toast varias veces.
 *
 * @property isLoading `true` mientras se está cargando el detalle de
 *   la tarea desde la red.
 * @property assignment Detalle cargado, o `null` si aún no se ha
 *   cargado o si la carga falló.
 */
data class AssignmentDetailUiState(
    val isLoading: Boolean = true,
    val assignment: AssignmentDetail? = null,
)

/**
 * Eventos de un solo uso consumidos por
 * `AssignmentDetailFragment`.
 *
 * ## Por qué son eventos y no parte del estado
 *
 * Porque un toast de error no debe repetirse al rotar la pantalla ni
 * al volver del segundo plano. Los eventos se emiten una vez y se
 * consumen una vez. Los estados, en cambio, se re-emiten a cada
 * suscripción.
 *
 * ## Por qué se usa un `Channel` y no un `StateFlow`
 *
 * Porque un `Channel` con `receiveAsFlow` garantiza que cada evento
 * se entrega a **un** consumidor **una** vez. Un `StateFlow`, en
 * cambio, re-emite el último valor a cada nuevo suscriptor. Para
 * eventos efímeros, eso sería un desastre.
 */
sealed interface AssignmentDetailUiEvent {

    /**
     * Muestra un toast de error.
     *
     * ## Por qué el mensaje es genérico
     *
     * Porque el detalle técnico del error (mensaje de excepción,
     * código HTTP, etc.) no debe mostrarse al usuario: sería confuso
     * y, en algunos casos, podría filtrar información sensible. El
     * detalle va al log con `Logging.logError`, y al usuario se le
     * muestra un mensaje genérico.
     *
     * ## Por qué se usa `@param:StringRes`
     *
     * Porque `R.string.error_unexpected` es un recurso de string,
     * no un string literal. El prefijo `param:` es necesario para
     * que Kotlin no avise sobre la ubicación de la anotación.
     *
     * @property messageResId ID del recurso de string a mostrar.
     * @property arg Argumento opcional para formatear el string. Si
     *   es `null`, se usa el string tal cual.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : AssignmentDetailUiEvent
}

/**
 * ViewModel de la pantalla de detalle de una tarea.
 *
 * ## Qué es un ViewModel
 *
 * Un ViewModel es una clase que **sobrevive a los cambios de
 * configuración** (como rotar la pantalla) y que **contiene el
 * estado** de una pantalla. La UI (Fragment, Activity) observa su
 * estado y se actualiza cuando cambia.
 *
 * ## Por qué el ViewModel y no el Fragment
 *
 * Porque el Fragment se destruye y se recrea al rotar, perdiendo su
 * estado. El ViewModel no: sobrevive a la recreación. Así, la carga
 * de datos no se reinicia cada vez que el usuario rota el móvil.
 *
 * ## Qué hace este ViewModel en concreto
 *
 * 1. Al construirse, carga el detalle de la tarea
 *    (`GetAssignmentDetail`).
 * 2. Mientras carga, pone `isLoading = true` (la UI muestra un
 *    spinner).
 * 3. Al terminar, publica el `AssignmentDetail` en el estado.
 * 4. Si falla, registra el error en el log y emite un evento
 *    `ShowError` (la UI muestra un toast genérico).
 *
 * ## Dependencias
 *
 * Solo depende de un caso de uso (`GetAssignmentDetail`). El
 * ViewModel **no sabe** que existe Moodle, ni que hay un
 * repositorio, ni que hay una base de datos. Solo pide "el detalle
 * de esta tarea" y recibe el resultado.
 *
 * @param savedStateHandle Handle con los argumentos de navegación
 *   (`COURSE_ID` y `ASSIGNMENT_ID`).
 * @param getAssignmentDetail Caso de uso del detalle de la tarea.
 */
@HiltViewModel
class AssignmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAssignmentDetail: GetAssignmentDetail,
) : ViewModel() {

    /**
     * ID del curso, extraído de los argumentos de navegación.
     *
     * ## Por qué se usa `SavedStateHandle`
     *
     * Porque los argumentos de navegación los pasa el Fragment al
     * construirse. Al rotar la pantalla, el Fragment se recrea y los
     * argumentos se pierden. `SavedStateHandle` los conserva y los
     * expone al ViewModel.
     *
     * ## Por qué el valor por defecto es 0
     *
     * Porque si no se ha pasado el argumento (por ejemplo, si alguien
     * navega a esta pantalla sin pasar los datos), es mejor tener un
     * valor por defecto que un crash.
     */
    private val courseId: Int = savedStateHandle.get<Int>("COURSE_ID") ?: 0

    /**
     * ID de la tarea, extraído de los argumentos de navegación.
     *
     * ## Por qué el valor por defecto es 0
     *
     * Mismo motivo que `courseId`.
     */
    private val assignmentId: Int = savedStateHandle.get<Int>("ASSIGNMENT_ID") ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(AssignmentDetailUiState())

    /**
     * Estado observable de la pantalla.
     *
     * La UI se suscribe a este `StateFlow` y se actualiza cada vez
     * que cambia el estado.
     */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<AssignmentDetailUiEvent>(Channel.BUFFERED)

    /**
     * Eventos de un solo uso, expuestos como `Flow`.
     *
     * La UI los consume con `collect`. Cada evento se entrega una
     * sola vez.
     */
    val events: Flow<AssignmentDetailUiEvent> = _events.receiveAsFlow()

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * Lanza la primera carga del detalle de la tarea.
     */
    init {
        loadAssignment()
    }

    /**
     * Recarga el detalle de la tarea.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init` (primera carga).
     * - Desde la UI, si en el futuro se añade un pull-to-refresh o un
     *   botón de "reintentar".
     *
     * ## Qué hace
     *
     * Lanza una corrutina en `viewModelScope` que:
     *
     * 1. Pone `isLoading = true`.
     * 2. Llama al caso de uso.
     * 3. Si va bien, publica el `AssignmentDetail` y apaga
     *    `isLoading`.
     * 4. Si falla, registra el error en el log, apaga `isLoading` y
     *    emite un `ShowError`.
     *
     * ## Por qué `viewModelScope`
     *
     * Porque es el `CoroutineScope` ligado al ciclo de vida del
     * ViewModel. Cuando el ViewModel se destruye (porque el usuario
     * cerró la pantalla), todas las corrutinas lanzadas en él se
     * cancelan automáticamente. Así no hay fugas de memoria.
     */
    fun loadAssignment() = viewModelScope.launch {
        setLoading()
        runCatching { getAssignmentDetail(courseId, assignmentId) }
            .onSuccess { assignmentDetail -> onLoaded(assignmentDetail) }
            .onFailure { exception ->
                Logging.logError(
                    TAG,
                    "getAssignmentDetail(courseId=$courseId, assignmentId=$assignmentId)",
                    exception,
                )
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(
                    AssignmentDetailUiEvent.ShowError(messageResId = R.string.error_unexpected),
                )
            }
    }

    // --- Métodos internos ---

    /**
     * Actualiza el estado con el detalle cargado.
     *
     * @param assignmentDetail Detalle de la tarea.
     */
    private fun onLoaded(assignmentDetail: AssignmentDetail) = _state.update {
        it.copy(isLoading = false, assignment = assignmentDetail)
    }

    /**
     * Marca el estado como "cargando".
     *
     * ## Por qué no borra el detalle anterior
     *
     * Porque si el usuario recarga, es mejor mantener el detalle
     * anterior visible mientras llega el nuevo (evita el parpadeo).
     * La UI puede mostrar el detalle viejo con un indicador de
     * "cargando" encima.
     */
    private fun setLoading() = _state.update { it.copy(isLoading = true) }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "AssignmentDetailViewModel"
    }
}