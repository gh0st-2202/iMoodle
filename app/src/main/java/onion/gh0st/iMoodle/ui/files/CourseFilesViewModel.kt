package onion.gh0st.iMoodle.ui.files

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
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseFiles
import javax.inject.Inject

/**
 * Estado observable de la pantalla de archivos de un módulo.
 *
 * ## Por qué solo contiene datos persistibles
 *
 * Los errores son eventos efímeros (`CourseFilesUiEvent.ShowError`),
 * no estados. Si vivieran en el estado, se re-emitirían en cada
 * re-suscripción (por ejemplo, al rotar), mostrando el mismo toast
 * varias veces.
 *
 * @property isLoading `true` mientras se cargan los archivos del
 *   módulo.
 * @property files Lista de archivos del módulo seleccionado.
 */
data class CourseFilesUiState(
    val isLoading: Boolean = true,
    val files: List<CourseFile> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `CourseFilesFragment`.
 *
 * Se emiten vía `Channel` (no `StateFlow`) porque un toast repetido
 * sería un bug.
 */
sealed interface CourseFilesUiEvent {

    /**
     * Muestra un toast de error.
     *
     * ## Por qué el mensaje es genérico
     *
     * Porque el detalle técnico del error va al log, no a la UI. El
     * usuario ve un mensaje amable; el desarrollador ve el detalle
     * en logcat.
     *
     * @property messageResId ID del recurso de string a mostrar.
     * @property arg Argumento opcional para formatear. Si es `null`,
     *   se usa el string tal cual.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : CourseFilesUiEvent
}

/**
 * ViewModel de la pantalla de archivos de un módulo.
 *
 * ## Qué hace
 *
 * 1. Al construirse, carga los archivos del módulo indicado con
 *    `GetCourseFiles`.
 * 2. Mientras carga, pone `isLoading = true`.
 * 3. Al terminar, publica la lista de archivos.
 * 4. Si falla, registra el error en el log y emite un `ShowError`.
 *
 * ## Dependencias
 *
 * Solo depende del caso de uso `GetCourseFiles`, que encapsula el
 * filtrado por módulo. El ViewModel no sabe cómo se estructuran las
 * secciones del curso: solo pide "los archivos del módulo X".
 *
 * @param savedStateHandle Handle con `COURSE_ID` y `MODULE_ID`.
 * @param getCourseFiles Caso de uso que resuelve los archivos.
 */
@HiltViewModel
class CourseFilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCourseFiles: GetCourseFiles,
) : ViewModel() {

    /**
     * ID del curso, extraído de los argumentos de navegación.
     *
     * ## Por qué `SavedStateHandle`
     *
     * Porque los argumentos de navegación los pasa el Fragment al
     * construirse. Al rotar, el Fragment se recrea y los argumentos
     * se perderían. `SavedStateHandle` los conserva.
     */
    private val courseId: Int = savedStateHandle.get<Int>("COURSE_ID") ?: 0

    /** ID del módulo, extraído de los argumentos de navegación. */
    private val moduleId: Int = savedStateHandle.get<Int>("MODULE_ID") ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(CourseFilesUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<CourseFilesUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<CourseFilesUiEvent> = _events.receiveAsFlow()

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * Lanza la primera carga de archivos.
     */
    init {
        loadFiles()
    }

    /**
     * Vuelve a cargar los archivos del módulo.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init` (primera carga).
     * - Desde el pull-to-refresh del Fragment.
     */
    fun refresh() = loadFiles()

    // --- Métodos internos ---

    /**
     * Lanza la carga de archivos en `viewModelScope`.
     *
     * ## Qué hace paso a paso
     *
     * 1. Marca `isLoading = true`.
     * 2. Llama al caso de uso `GetCourseFiles`.
     * 3. Si va bien, publica los archivos y apaga `isLoading`.
     * 4. Si falla, registra el error en el log, apaga `isLoading` y
     *    emite un `ShowError`.
     *
     * ## Por qué `viewModelScope`
     *
     * Porque es el `CoroutineScope` ligado al ciclo de vida del
     * ViewModel. Cuando el ViewModel se destruye, las corrutinas se
     * cancelan automáticamente.
     */
    private fun loadFiles() = viewModelScope.launch {
        setLoading()
        runCatching { getCourseFiles(courseId, moduleId) }
            .onSuccess { loadedFiles -> onFilesLoaded(loadedFiles) }
            .onFailure { exception ->
                Logging.logError(
                    TAG,
                    "getCourseFiles(courseId=$courseId, moduleId=$moduleId)",
                    exception,
                )
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(
                    CourseFilesUiEvent.ShowError(messageResId = R.string.error_unexpected),
                )
            }
    }

    /**
     * Actualiza el estado con la lista de archivos cargada.
     *
     * @param loadedFiles Archivos del módulo.
     */
    private fun onFilesLoaded(loadedFiles: List<CourseFile>) = _state.update {
        it.copy(isLoading = false, files = loadedFiles)
    }

    /**
     * Marca el estado como "cargando".
     *
     * ## Por qué no borra la lista anterior
     *
     * Porque si el usuario recarga, es mejor mantener la lista
     * anterior visible mientras llega la nueva (evita el parpadeo).
     */
    private fun setLoading() = _state.update { it.copy(isLoading = true) }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "CourseFilesViewModel"
    }
}