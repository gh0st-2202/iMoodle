package onion.gh0st.iMoodle.ui.wiki

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
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.domain.usecase.wiki.GetWikiPage
import onion.gh0st.iMoodle.domain.usecase.wiki.ManageWikiPage
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado observable de una página de wiki.
 *
 * @property isLoading `true` mientras se carga el contenido.
 * @property isLoadingDraft `true` mientras se pide el texto original
 *   para editarlo.
 * @property isSaving `true` mientras se guarda una edición.
 * @property page Página cargada, o `null` si todavía no hay ninguna.
 * @property draft Contenido original listo para editar, o `null` si aún
 *   no se ha pedido.
 */
data class WikiPageUiState(
    val isLoading: Boolean = true,
    val isLoadingDraft: Boolean = false,
    val isSaving: Boolean = false,
    val page: WikiPage? = null,
    val draft: WikiPageDraft? = null,
)

/**
 * Eventos de un solo uso consumidos por `WikiPageFragment` y
 * `EditWikiPageDialog`.
 */
sealed interface WikiPageUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     * @property arg Argumento opcional para formatear.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : WikiPageUiEvent

    /** La página se ha guardado correctamente. */
    data object PageSaved : WikiPageUiEvent
}

/**
 * ViewModel del contenido de una página de wiki.
 *
 * ## Qué hace
 *
 * - Carga la página con su contenido renderizado.
 * - Cuando el usuario pulsa "Editar", pide el contenido original.
 * - Guarda los cambios y recarga la página para mostrar lo que Moodle
 *   tiene realmente.
 *
 * ## Sobre el borrador
 *
 * El contenido que se edita no es el renderizado, sino el original en
 * el formato de la wiki (ver `WikiPageDraft`). Por eso se pide aparte,
 * solo cuando hace falta, y se deja en [WikiPageUiState.draft].
 *
 * @param savedStateHandle Handle con `WIKI_PAGE_ID`.
 * @param getWikiPage Caso de uso de lectura.
 * @param manageWikiPage Caso de uso de edición.
 */
@HiltViewModel
class WikiPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWikiPage: GetWikiPage,
    private val manageWikiPage: ManageWikiPage,
) : ViewModel() {

    /** ID de la página, extraído de los argumentos de navegación. */
    private val pageId: Int = savedStateHandle.get<Int>(FragmentArgs.WIKI_PAGE_ID) ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(WikiPageUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<WikiPageUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<WikiPageUiEvent> = _events.receiveAsFlow()

    init {
        loadPage()
    }

    /**
     * Vuelve a cargar el contenido de la página.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init`.
     * - Desde el pull-to-refresh del Fragment.
     * - Tras guardar una edición.
     */
    fun refresh() = loadPage()

    /**
     * Pide el contenido original de la página para abrir el editor.
     *
     * Se ignora si ya hay una petición en curso o un borrador
     * disponible: el diálogo reutiliza el que ya está en el estado.
     */
    fun requestDraft() {
        if (_state.value.isLoadingDraft || _state.value.draft != null) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isLoadingDraft = true) }
            manageWikiPage.getDraft(pageId)
                .onSuccess { loadedDraft ->
                    _state.update { currentState ->
                        currentState.copy(isLoadingDraft = false, draft = loadedDraft)
                    }
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "getDraft(pageId=$pageId)", exception)
                    _state.update { currentState -> currentState.copy(isLoadingDraft = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Descarta el borrador en cuanto se cierra el editor, para que la
     * próxima edición vuelva a pedir el contenido actualizado.
     */
    fun discardDraft() = _state.update { currentState ->
        currentState.copy(draft = null, isLoadingDraft = false)
    }

    /**
     * Guarda el contenido editado de la página.
     *
     * @param content Contenido en el formato propio de la wiki.
     */
    fun savePage(content: String) {
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isSaving = true) }
            manageWikiPage.update(pageId, content)
                .onSuccess {
                    _state.update { currentState ->
                        currentState.copy(isSaving = false, draft = null)
                    }
                    _events.send(WikiPageUiEvent.PageSaved)
                    loadPage()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "update(pageId=$pageId)", exception)
                    _state.update { currentState -> currentState.copy(isSaving = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Lanza la carga de la página en `viewModelScope`.
     */
    private fun loadPage() = viewModelScope.launch {
        _state.update { currentState -> currentState.copy(isLoading = true) }
        getWikiPage(pageId)
            .onSuccess { loadedPage ->
                _state.update { currentState ->
                    currentState.copy(isLoading = false, page = loadedPage)
                }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getWikiPage(pageId=$pageId)", exception)
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(exception.toErrorEvent())
            }
    }

    /**
     * Traduce un fallo en el mensaje que se enseña al usuario.
     *
     * @receiver Fallo ocurrido.
     * @return Evento de error listo para emitir.
     */
    private fun Throwable.toErrorEvent(): WikiPageUiEvent.ShowError = when {
        this is AuthException.Network -> WikiPageUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            WikiPageUiEvent.ShowError(R.string.error_wiki_operation, message)
        else -> WikiPageUiEvent.ShowError(R.string.error_unexpected)
    }

    private companion object {
        /** Etiqueta de logcat. */
        const val TAG = "WikiPageViewModel"
    }
}
