package onion.gh0st.iMoodle.ui.forum.list

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
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.usecase.forum.GetForumDiscussions
import onion.gh0st.iMoodle.domain.usecase.forum.ManageForumPosts
import javax.inject.Inject
import onion.gh0st.iMoodle.ui.common.FragmentArgs

/**
 * Estado observable de un foro.
 *
 * @property isLoading `true` mientras se cargan las discusiones.
 * @property discussions Lista de discusiones cargadas.
 * @property canCreateDiscussion `true` si Moodle permite abrir un tema nuevo.
 * @property isPosting `true` mientras se publica un tema nuevo.
 */
data class ForumUiState(
    val isLoading: Boolean = true,
    val discussions: List<ForumDiscussion> = emptyList(),
    val canCreateDiscussion: Boolean = false,
    val isPosting: Boolean = false,
)

/**
 * Eventos de un solo uso consumidos por `ForumFragment`.
 */
sealed interface ForumUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     * @property arg Argumento opcional para formatear.
     */
    data class ShowError(
        @param:StringRes val messageResId: Int,
        val arg: String? = null,
    ) : ForumUiEvent

    /** El tema nuevo se ha publicado correctamente. */
    data object DiscussionCreated : ForumUiEvent
}

/**
 * ViewModel de la lista de discusiones de un foro.
 *
 * ## Qué hace
 *
 * 1. Al construirse, carga las discusiones del foro con
 *    `GetForumDiscussions`.
 * 2. Mientras carga, pone `isLoading = true`.
 * 3. Al terminar, publica la lista.
 * 4. Si falla, registra el error y emite un `ShowError`.
 *
 * ## Dependencias
 *
 * Depende de dos casos de uso: `GetForumDiscussions` para leer y
 * `ManageForumPosts` para comprobar permisos y publicar. No filtra ni
 * reordena: el repositorio entrega la lista tal cual la devuelve
 * Moodle.
 *
 * @param savedStateHandle Handle con `FORUM_ID`.
 * @param getForumDiscussions Caso de uso de discusiones.
 * @param manageForumPosts Caso de uso de escritura en foros.
 */
@HiltViewModel
class ForumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForumDiscussions: GetForumDiscussions,
    private val manageForumPosts: ManageForumPosts,
) : ViewModel() {

    /** ID del foro, extraído de los argumentos de navegación. */
    private val forumId: Int = savedStateHandle.get<Int>(FragmentArgs.FORUM_ID) ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(ForumUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<ForumUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<ForumUiEvent> = _events.receiveAsFlow()

    init {
        loadDiscussions()
        loadPermissions()
    }

    /**
     * Vuelve a cargar las discusiones.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init`.
     * - Desde el pull-to-refresh del Fragment.
     */
    fun refresh() = loadDiscussions()

    /**
     * Publica un tema nuevo y recarga la lista.
     *
     * Se ignora si el asunto o el mensaje están vacíos o si ya hay una
     * publicación en curso.
     *
     * @param subject Asunto del tema.
     * @param message Mensaje inicial.
     */
    fun createDiscussion(subject: String, message: String) {
        if (subject.isBlank() || message.isBlank() || _state.value.isPosting) return

        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isPosting = true) }
            runCatchingCancellable { manageForumPosts.addDiscussion(forumId, subject, message) }
                .onSuccess {
                    _state.update { currentState -> currentState.copy(isPosting = false) }
                    _events.send(ForumUiEvent.DiscussionCreated)
                    loadDiscussions()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "addDiscussion(forumId=$forumId)", exception)
                    _state.update { currentState -> currentState.copy(isPosting = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Pregunta a Moodle si se puede abrir un tema nuevo.
     *
     * Si la consulta falla, el botón queda oculto: es preferible a
     * enseñar una acción que luego no funciona.
     */
    private fun loadPermissions() = viewModelScope.launch {
        val canCreate = runCatchingCancellable { manageForumPosts.canAddDiscussion(forumId) }
            .getOrDefault(false)
        _state.update { currentState -> currentState.copy(canCreateDiscussion = canCreate) }
    }

    /**
     * Traduce un fallo en el mensaje que se enseña al usuario.
     *
     * @receiver Fallo ocurrido.
     * @return Evento de error listo para emitir.
     */
    private fun Throwable.toErrorEvent(): ForumUiEvent.ShowError = when {
        this is AuthException.Network -> ForumUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            ForumUiEvent.ShowError(R.string.error_forum_operation, message)
        else -> ForumUiEvent.ShowError(R.string.error_unexpected)
    }

    /**
     * Lanza la carga de discusiones en `viewModelScope`.
     */
    private fun loadDiscussions() = viewModelScope.launch {
        setLoading()
        runCatchingCancellable { getForumDiscussions(forumId) }
            .onSuccess { loadedDiscussions -> onLoaded(loadedDiscussions) }
            .onFailure { exception ->
                Logging.logError(TAG, "getForumDiscussions(forumId=$forumId)", exception)
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _events.send(
                    ForumUiEvent.ShowError(messageResId = R.string.error_unexpected),
                )
            }
    }

    /**
     * Actualiza el estado con las discusiones cargadas.
     *
     * @param loadedDiscussions Discusiones del foro.
     */
    private fun onLoaded(loadedDiscussions: List<ForumDiscussion>) = _state.update {
        it.copy(isLoading = false, discussions = loadedDiscussions)
    }

    /**
     * Marca el estado como "cargando".
     */
    private fun setLoading() = _state.update { it.copy(isLoading = true) }

    private companion object {
        /** Etiqueta de logcat. */
        const val TAG = "ForumViewModel"
    }
}
