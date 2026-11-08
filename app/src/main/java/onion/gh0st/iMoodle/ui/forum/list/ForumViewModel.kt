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
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.usecase.forum.GetForumDiscussions
import javax.inject.Inject

/**
 * Estado observable de un foro.
 *
 * @property isLoading `true` mientras se cargan las discusiones.
 * @property discussions Lista de discusiones cargadas.
 */
data class ForumUiState(
    val isLoading: Boolean = true,
    val discussions: List<ForumDiscussion> = emptyList(),
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
 * Solo depende del caso de uso `GetForumDiscussions`. No filtra ni
 * reordena: el repositorio entrega la lista tal cual la devuelve
 * Moodle.
 *
 * @param savedStateHandle Handle con `FORUM_ID`.
 * @param getForumDiscussions Caso de uso de discusiones.
 */
@HiltViewModel
class ForumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForumDiscussions: GetForumDiscussions,
) : ViewModel() {

    /** ID del foro, extraído de los argumentos de navegación. */
    private val forumId: Int = savedStateHandle.get<Int>("FORUM_ID") ?: 0

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
     * Lanza la carga de discusiones en `viewModelScope`.
     */
    private fun loadDiscussions() = viewModelScope.launch {
        setLoading()
        runCatching { getForumDiscussions(forumId) }
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