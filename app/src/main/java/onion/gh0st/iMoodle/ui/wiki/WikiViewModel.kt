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
import onion.gh0st.iMoodle.domain.usecase.wiki.GetWikiPages
import onion.gh0st.iMoodle.domain.usecase.wiki.ManageWikiPage
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado observable del listado de páginas de una wiki.
 *
 * @property isLoading `true` mientras se cargan las páginas.
 * @property isCreating `true` mientras se crea una página nueva.
 * @property pages Páginas de la wiki, ordenadas por título.
 */
data class WikiUiState(
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val pages: List<WikiPage> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `WikiFragment`.
 */
sealed interface WikiUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     * @property arg Argumento opcional para formatear.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : WikiUiEvent

    /**
     * Se ha creado una página nueva.
     *
     * @property pageId ID de la página creada, para abrirla.
     */
    data class PageCreated(val pageId: Int) : WikiUiEvent
}

/**
 * ViewModel del listado de páginas de una wiki.
 *
 * ## Qué hace
 *
 * - Carga las páginas al abrir la pantalla y al hacer pull-to-refresh.
 * - Crea páginas nuevas y, si todo va bien, avisa con el ID de la
 *   página creada para que el fragmento la abra.
 *
 * ## Errores
 *
 * Los mensajes de Moodle al rechazar una escritura llegan como
 * `IllegalStateException` desde el repositorio y se muestran con su
 * motivo. Los fallos de red se muestran como aviso de red y el resto
 * como error genérico.
 *
 * @param savedStateHandle Handle con `WIKI_ID`.
 * @param getWikiPages Caso de uso de lectura.
 * @param manageWikiPage Caso de uso de escritura.
 */
@HiltViewModel
class WikiViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWikiPages: GetWikiPages,
    private val manageWikiPage: ManageWikiPage,
) : ViewModel() {

    /** ID de la wiki, extraído de los argumentos de navegación. */
    private val wikiId: Int = savedStateHandle.get<Int>(FragmentArgs.WIKI_ID) ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(WikiUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<WikiUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<WikiUiEvent> = _events.receiveAsFlow()

    init {
        loadPages()
    }

    /**
     * Vuelve a cargar las páginas.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init`.
     * - Desde el pull-to-refresh del Fragment.
     * - Al volver de editar una página.
     */
    fun refresh() = loadPages()

    /**
     * Crea una página nueva en la wiki.
     *
     * @param title Título de la página.
     * @param content Contenido inicial, ya convertido a HTML.
     */
    fun createPage(title: String, content: String) {
        if (_state.value.isCreating) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isCreating = true) }
            manageWikiPage.create(wikiId, title.trim(), content)
                .onSuccess { createdPageId ->
                    _state.update { currentState -> currentState.copy(isCreating = false) }
                    _events.send(WikiUiEvent.PageCreated(createdPageId))
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "createPage(wikiId=$wikiId)", exception)
                    _state.update { currentState -> currentState.copy(isCreating = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Lanza la carga de páginas en `viewModelScope`.
     */
    private fun loadPages() = viewModelScope.launch {
        _state.update { currentState -> currentState.copy(isLoading = true) }
        getWikiPages(wikiId)
            .onSuccess { loadedPages ->
                _state.update { currentState ->
                    currentState.copy(isLoading = false, pages = loadedPages)
                }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getWikiPages(wikiId=$wikiId)", exception)
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
    private fun Throwable.toErrorEvent(): WikiUiEvent.ShowError = when {
        this is AuthException.Network -> WikiUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            WikiUiEvent.ShowError(R.string.error_wiki_operation, message)
        else -> WikiUiEvent.ShowError(R.string.error_unexpected)
    }

    private companion object {
        /** Etiqueta de logcat. */
        const val TAG = "WikiViewModel"
    }
}
