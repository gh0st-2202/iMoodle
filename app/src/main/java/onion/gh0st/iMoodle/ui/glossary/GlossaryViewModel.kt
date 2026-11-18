package onion.gh0st.iMoodle.ui.glossary

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
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.domain.usecase.glossary.GetGlossaryEntries
import onion.gh0st.iMoodle.domain.usecase.glossary.ManageGlossaryEntries
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado observable de un glosario.
 *
 * @property isLoading `true` mientras se cargan las entradas.
 * @property isSaving `true` mientras se guarda, edita o borra una
 *   entrada.
 * @property entries Entradas del glosario, ordenadas por concepto.
 */
data class GlossaryUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val entries: List<GlossaryEntry> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `GlossaryFragment`.
 */
sealed interface GlossaryUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     * @property arg Argumento opcional para formatear.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : GlossaryUiEvent

    /** Una entrada se ha guardado (creada o modificada). */
    data object EntrySaved : GlossaryUiEvent

    /** Una entrada se ha borrado. */
    data object EntryDeleted : GlossaryUiEvent
}

/**
 * ViewModel de un glosario.
 *
 * ## Qué hace
 *
 * - Carga las entradas al abrir la pantalla y al hacer pull-to-refresh.
 * - Crea, modifica y borra entradas.
 * - Después de cada escritura correcta vuelve a pedir la lista, para
 *   mostrar lo que el servidor tiene realmente (por ejemplo, si una
 *   entrada queda pendiente de aprobación).
 *
 * ## Errores
 *
 * Cuando Moodle rechaza una escritura, el repositorio lanza
 * `IllegalStateException` con su mensaje y aquí se traduce a un aviso
 * que incluye el motivo. Los fallos de red se muestran como aviso de
 * red y el resto como error genérico.
 *
 * @param savedStateHandle Handle con `GLOSSARY_ID`.
 * @param getGlossaryEntries Caso de uso de lectura.
 * @param manageGlossaryEntries Caso de uso de escritura.
 */
@HiltViewModel
class GlossaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGlossaryEntries: GetGlossaryEntries,
    private val manageGlossaryEntries: ManageGlossaryEntries,
) : ViewModel() {

    /** ID del glosario, extraído de los argumentos de navegación. */
    private val glossaryId: Int = savedStateHandle.get<Int>(FragmentArgs.GLOSSARY_ID) ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(GlossaryUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<GlossaryUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<GlossaryUiEvent> = _events.receiveAsFlow()

    init {
        loadEntries()
    }

    /**
     * Vuelve a cargar las entradas.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init`.
     * - Desde el pull-to-refresh del Fragment.
     */
    fun refresh() = loadEntries()

    /**
     * Guarda una entrada: la crea si [entryId] es `0` y la modifica si
     * tiene un identificador.
     *
     * ## Validación
     *
     * El diálogo comprueba que concepto y definición no estén vacíos
     * antes de llamar; aquí se recorta el texto para no enviar espacios
     * sobrantes a Moodle.
     *
     * @param entryId ID de la entrada, o `0` para crear una nueva.
     * @param concept Término.
     * @param definition Definición en HTML sencillo, ya construida por
     *   el diálogo a partir del texto que escribe el usuario.
     */
    fun saveEntry(entryId: Int, concept: String, definition: String) {
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isSaving = true) }
            val result = if (entryId == 0) {
                manageGlossaryEntries.add(glossaryId, concept.trim(), definition.trim())
            } else {
                manageGlossaryEntries.update(entryId, concept.trim(), definition.trim())
            }
            result
                .onSuccess { onWriteSucceeded(GlossaryUiEvent.EntrySaved) }
                .onFailure { exception -> onWriteFailed("saveEntry(entryId=$entryId)", exception) }
        }
    }

    /**
     * Borra una entrada.
     *
     * La confirmación la pide el Fragment antes de llamar aquí.
     *
     * @param entryId ID de la entrada.
     */
    fun deleteEntry(entryId: Int) {
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isSaving = true) }
            manageGlossaryEntries.delete(entryId)
                .onSuccess { onWriteSucceeded(GlossaryUiEvent.EntryDeleted) }
                .onFailure { exception -> onWriteFailed("deleteEntry(entryId=$entryId)", exception) }
        }
    }

    /**
     * Lanza la carga de entradas en `viewModelScope`.
     */
    private fun loadEntries() = viewModelScope.launch {
        _state.update { currentState -> currentState.copy(isLoading = true) }
        getGlossaryEntries(glossaryId)
            .onSuccess { loadedEntries ->
                _state.update { currentState ->
                    currentState.copy(isLoading = false, entries = loadedEntries)
                }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getGlossaryEntries(glossaryId=$glossaryId)", exception)
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(exception.toErrorEvent())
            }
    }

    /**
     * Termina una escritura correcta: quita el estado de guardado,
     * avisa y recarga la lista.
     *
     * @param event Evento de éxito que consume el Fragment.
     */
    private fun onWriteSucceeded(event: GlossaryUiEvent) {
        _state.update { currentState -> currentState.copy(isSaving = false) }
        viewModelScope.launch { _events.send(event) }
        loadEntries()
    }

    /**
     * Termina una escritura fallida: quita el estado de guardado,
     * registra el fallo y avisa al usuario.
     *
     * @param operation Descripción de la operación para el log.
     * @param exception Fallo ocurrido.
     */
    private fun onWriteFailed(operation: String, exception: Throwable) {
        Logging.logError(TAG, operation, exception)
        _state.update { currentState -> currentState.copy(isSaving = false) }
        viewModelScope.launch { _events.send(exception.toErrorEvent()) }
    }

    /**
     * Traduce un fallo en el mensaje que se enseña al usuario.
     *
     * @receiver Fallo ocurrido.
     * @return Evento de error listo para emitir.
     */
    private fun Throwable.toErrorEvent(): GlossaryUiEvent.ShowError = when {
        this is AuthException.Network -> GlossaryUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            GlossaryUiEvent.ShowError(R.string.error_glossary_operation, message)
        else -> GlossaryUiEvent.ShowError(R.string.error_unexpected)
    }

    private companion object {
        /** Etiqueta de logcat. */
        const val TAG = "GlossaryViewModel"
    }
}
