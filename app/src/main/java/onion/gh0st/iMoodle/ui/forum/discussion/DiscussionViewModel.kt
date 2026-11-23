package onion.gh0st.iMoodle.ui.forum.discussion

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
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.domain.usecase.forum.GetDiscussionPosts
import onion.gh0st.iMoodle.domain.usecase.forum.ManageForumPosts
import javax.inject.Inject
import onion.gh0st.iMoodle.ui.common.FragmentArgs

/**
 * Estado observable de una discusión.
 *
 * @property isLoading `true` mientras se cargan los posts.
 * @property posts Lista de posts cargados.
 * @property isPosting `true` mientras se publica una respuesta.
 */
data class DiscussionUiState(
    val isLoading: Boolean = true,
    val posts: List<ForumPost> = emptyList(),
    val isPosting: Boolean = false,
)

/**
 * Eventos de un solo uso consumidos por `DiscussionFragment`.
 */
sealed interface DiscussionUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     * @property arg Argumento opcional para formatear.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : DiscussionUiEvent

    /** La respuesta se ha publicado correctamente. */
    data object ReplyPosted : DiscussionUiEvent
}

/**
 * ViewModel de los posts de una discusión.
 *
 * ## Qué hace
 *
 * 1. Al construirse, carga los posts con `GetDiscussionPosts`.
 * 2. Mientras carga, pone `isLoading = true`.
 * 3. Al terminar, publica la lista de posts.
 * 4. Si falla, registra el error y emite un `ShowError`.
 *
 * ## Orden de los posts
 *
 * Los posts vienen ordenados por fecha ascendente desde el
 * repositorio. El primero es el mensaje inicial; los siguientes son
 * las respuestas. El adapter no reordena.
 *
 * ## Dependencias
 *
 * Depende de `GetDiscussionPosts` para leer y `ManageForumPosts` para
 * responder.
 *
 * @param savedStateHandle Handle con `DISCUSSION_ID`.
 * @param getDiscussionPosts Caso de uso de posts.
 * @param manageForumPosts Caso de uso de escritura en foros.
 */
@HiltViewModel
class DiscussionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDiscussionPosts: GetDiscussionPosts,
    private val manageForumPosts: ManageForumPosts,
) : ViewModel() {

    /** ID de la discusión, extraído de los argumentos. */
    private val discussionId: Int = savedStateHandle.get<Int>(FragmentArgs.DISCUSSION_ID) ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(DiscussionUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<DiscussionUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<DiscussionUiEvent> = _events.receiveAsFlow()

    init {
        loadPosts()
    }

    /**
     * Vuelve a cargar los posts.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init`.
     * - Desde el pull-to-refresh del Fragment.
     */
    fun refresh() = loadPosts()

    /**
     * Responde al mensaje inicial de la discusión.
     *
     * ## Cómo elige el destinatario
     *
     * Moodle pide el ID del post al que se responde. La app usa el
     * primer post (el mensaje inicial), de modo que las respuestas
     * quedan al mismo nivel en lugar de anidarse.
     *
     * Se ignora si no hay posts, si el asunto o el mensaje están
     * vacíos, o si ya hay una respuesta en curso.
     *
     * @param subject Asunto de la respuesta.
     * @param message Cuerpo de la respuesta.
     */
    fun reply(subject: String, message: String) {
        if (subject.isBlank() || message.isBlank() || _state.value.isPosting) return
        val parentPostId = _state.value.posts.firstOrNull()?.id ?: return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isPosting = true) }
            runCatchingCancellable { manageForumPosts.addReply(parentPostId, subject, message) }
                .onSuccess {
                    _state.update { currentState -> currentState.copy(isPosting = false) }
                    _events.send(DiscussionUiEvent.ReplyPosted)
                    loadPosts()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "addReply(parentPostId=$parentPostId)", exception)
                    _state.update { currentState -> currentState.copy(isPosting = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Traduce un fallo en el mensaje que se enseña al usuario.
     *
     * @receiver Fallo ocurrido.
     * @return Evento de error listo para emitir.
     */
    private fun Throwable.toErrorEvent(): DiscussionUiEvent.ShowError = when {
        this is AuthException.Network -> DiscussionUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            DiscussionUiEvent.ShowError(R.string.error_forum_operation, message)
        else -> DiscussionUiEvent.ShowError(R.string.error_unexpected)
    }

    /**
     * Lanza la carga de posts en `viewModelScope`.
     */
    private fun loadPosts() = viewModelScope.launch {
        setLoading()
        runCatchingCancellable { getDiscussionPosts(discussionId) }
            .onSuccess { loadedPosts -> onLoaded(loadedPosts) }
            .onFailure { exception ->
                Logging.logError(
                    TAG,
                    "getDiscussionPosts(discussionId=$discussionId)",
                    exception,
                )
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(
                    DiscussionUiEvent.ShowError(messageResId = R.string.error_unexpected),
                )
            }
    }

    /**
     * Actualiza el estado con los posts cargados.
     *
     * @param loadedPosts Posts de la discusión.
     */
    private fun onLoaded(loadedPosts: List<ForumPost>) = _state.update {
        it.copy(isLoading = false, posts = loadedPosts)
    }

    /**
     * Marca el estado como "cargando".
     */
    private fun setLoading() = _state.update { it.copy(isLoading = true) }

    private companion object {
        /** Etiqueta de logcat. */
        const val TAG = "DiscussionViewModel"
    }
}
