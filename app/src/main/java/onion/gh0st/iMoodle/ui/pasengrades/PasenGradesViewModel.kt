package onion.gh0st.iMoodle.ui.pasengrades

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
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade
import onion.gh0st.iMoodle.domain.usecase.pasen.GetPasenGrades
import javax.inject.Inject

/**
 * Estado observable de la pantalla de calificaciones de iPasen.
 *
 * @property isLoading `true` mientras se consultan las convocatorias.
 * @property convocatorias Lista de convocatorias cargadas.
 * @property selectedConvocatoria Convocatoria actualmente seleccionada.
 * @property hasError `true` si la última consulta falló.
 */
data class PasenGradesUiState(
    val isLoading: Boolean = true,
    val convocatorias: List<PasenConvocatoria> = emptyList(),
    val selectedConvocatoria: String? = null,
    val hasError: Boolean = false,
) {
    /** Asignaturas de la convocatoria seleccionada. */
    val subjects: List<PasenSubjectGrade>
        get() = convocatorias.firstOrNull { convocatoria -> convocatoria.course == selectedConvocatoria }?.subjects
            ?: emptyList()
}

/**
 * Eventos de un solo uso de la pantalla de calificaciones.
 */
sealed interface PasenGradesUiEvent {
    /** No se pudieron cargar las calificaciones. */
    data class ShowError(val messageResId: Int) : PasenGradesUiEvent
}

/**
 * ViewModel de la pantalla de calificaciones de iPasen.
 *
 * ## Qué hace
 *
 * Pide las convocatorias al caso de uso [GetPasenGrades] al abrirse y
 * cada vez que se refresca. La convocatoria activa se selecciona por
 * defecto; el usuario puede cambiarla con [setConvocatoria].
 *
 * @param getPasenGrades Caso de uso de calificaciones.
 */
@HiltViewModel
class PasenGradesViewModel @Inject constructor(
    private val getPasenGrades: GetPasenGrades,
) : ViewModel() {

    private val _state = MutableStateFlow(PasenGradesUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    private val _events = Channel<PasenGradesUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso. */
    val events: Flow<PasenGradesUiEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Vuelve a consultar las calificaciones a iPasen. */
    fun refresh() = load(forceRefresh = true)

    /**
     * Cambia la convocatoria seleccionada.
     *
     * @param course Nombre de la convocatoria.
     */
    fun setConvocatoria(course: String) {
        _state.update { currentState -> currentState.copy(selectedConvocatoria = course) }
    }

    /**
     * Decide qué convocatoria debe quedar seleccionada tras una carga.
     *
     * Se conserva la que el usuario tenía elegida si sigue existiendo.
     * Si no, se usa la activa y, como último recurso, la primera.
     *
     * @param loaded Convocatorias recién cargadas.
     * @param current Convocatoria seleccionada antes de la carga.
     * @return El nombre de la convocatoria a seleccionar, o `null` si no
     *   hay ninguna.
     */
    private fun selectedConvocatoriaFor(
        loaded: List<PasenConvocatoria>,
        current: String?,
    ): String? = loaded.firstOrNull { convocatoria -> convocatoria.course == current }?.course
        ?: loaded.firstOrNull { convocatoria -> convocatoria.isActive }?.course
        ?: loaded.firstOrNull()?.course

    /**
     * Lanza la carga en `viewModelScope`.
     *
     * @param forceRefresh Si `true`, ignora la caché.
     */
    private fun load(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { currentState -> currentState.copy(isLoading = true, hasError = false) }
        runCatchingCancellable { getPasenGrades(forceRefresh) }
            .onSuccess { loaded ->
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        convocatorias = loaded,
                        selectedConvocatoria = selectedConvocatoriaFor(loaded, currentState.selectedConvocatoria),
                        hasError = false,
                    )
                }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getPasenGrades()", exception)
                _state.update { currentState -> currentState.copy(isLoading = false, hasError = true) }
                _events.send(PasenGradesUiEvent.ShowError(R.string.error_pasen_grades_load))
            }
    }

    private companion object {
        const val TAG = "PasenGradesViewModel"
    }
}
