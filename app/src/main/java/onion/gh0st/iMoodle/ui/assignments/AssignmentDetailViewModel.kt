package onion.gh0st.iMoodle.ui.assignments

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.usecase.assignment.*
import javax.inject.Inject

/**
 * El "cerebro" de la pantalla de detalle de tarea.
 *
 * ## ¿Qué hace un ViewModel?
 * Es la pieza que sobrevive a los cambios (como girar el móvil) y que guarda
 * lo que hay que mostrar en pantalla. La pantalla (Fragment) es "tonta" y solo
 * obedece a este cerebro.
 *
 * @property isLoading `true` si estamos esperando a que cargue la tarea.
 * @property assignment Los datos de la tarea (si ya han cargado).
 * @property isSubmitting `true` si estamos subiendo archivos a Moodle.
 * @property isDeleting `true` si estamos borrando la entrega entera.
 * @property comments La lista de mensajes del chat.
 * @property isLoadingComments `true` si estamos pidiendo los mensajes.
 * @property isPostingComment `true` si estamos enviando un mensaje.
 * @property isDeletingComment `true` si estamos borrando un mensaje.
 */
data class AssignmentDetailUiState(
    val isLoading: Boolean = true,
    val assignment: AssignmentDetail? = null,
    val isSubmitting: Boolean = false,
    val isDeleting: Boolean = false,
    val comments: List<SubmissionComment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val isPostingComment: Boolean = false,
    val isDeletingComment: Boolean = false,
)

/**
 * Eventos de un solo uso.
 *
 * Imagina que es un mensaje que sale una sola vez y luego desaparece.
 * (Para que no se repita el mismo toast al girar el móvil).
 */
sealed interface AssignmentDetailUiEvent {
    data class ShowError(@param:StringRes val messageResId: Int, val arg: String? = null) : AssignmentDetailUiEvent
    data object SubmissionSuccess : AssignmentDetailUiEvent
    data object DeletionSuccess : AssignmentDetailUiEvent
    data object CommentPosted : AssignmentDetailUiEvent
    data object CommentDeleted : AssignmentDetailUiEvent
}

@HiltViewModel
class AssignmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepo: SessionRepository,
    private val getAssignmentDetail: GetAssignmentDetail,
    private val submitAssignment: SubmitAssignment,
    private val deleteAssignmentSubmission: DeleteAssignmentSubmission,
    private val manageSubmissionComments: ManageSubmissionComments,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>("COURSE_ID") ?: 0
    private val cmId: Int = savedStateHandle.get<Int>("ASSIGNMENT_ID") ?: 0

    private val _state = MutableStateFlow(AssignmentDetailUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<AssignmentDetailUiEvent>(Channel.BUFFERED)
    val events: Flow<AssignmentDetailUiEvent> = _events.receiveAsFlow()

    init { loadAssignment() }

    /**
     * Carga los datos de la tarea y luego, si es posible, los comentarios.
     */
    fun loadAssignment() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        runCatching { getAssignmentDetail(courseId, cmId) }
            .onSuccess { detail ->
                _state.update { it.copy(isLoading = false, assignment = detail) }
                loadComments()
            }
            .onFailure {
                Logging.logError("AssignmentDetailVM", "load", it)
                _state.update { it.copy(isLoading = false) }
                _events.send(AssignmentDetailUiEvent.ShowError(R.string.error_unexpected))
            }
    }

    /**
     * Pide los comentarios, pero solo si la tarea tiene activada la opción de comentarios.
     */
    fun loadComments() {
        val assignment = _state.value.assignment ?: return
        val subId = assignment.submissionId ?: return
        if (subId <= 0 || "comments" !in assignment.enabledSubmissionPlugins) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingComments = true) }
            manageSubmissionComments.getComments(assignment.cmId, subId)
                .onSuccess { commentsList ->
                    _state.update { it.copy(comments = commentsList, isLoadingComments = false) }
                }
                .onFailure { _state.update { it.copy(isLoadingComments = false) } }
        }
    }

    /**
     * Publica un comentario y recarga la lista.
     */
    fun postComment(text: String) {
        if (text.isBlank() || _state.value.isPostingComment) return
        val assignment = _state.value.assignment ?: return
        val subId = assignment.submissionId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isPostingComment = true) }
            manageSubmissionComments.addComment(assignment.cmId, subId, text)
                .onSuccess {
                    _state.update { it.copy(isPostingComment = false) }
                    _events.send(AssignmentDetailUiEvent.CommentPosted)
                    loadComments()
                }
                .onFailure {
                    _state.update { it.copy(isPostingComment = false) }
                    _events.send(AssignmentDetailUiEvent.ShowError(R.string.error_unexpected))
                }
        }
    }

    /**
     * Borra un comentario y recarga la lista.
     */
    fun deleteComment(commentId: Int) {
        if (_state.value.isDeletingComment) return
        viewModelScope.launch {
            _state.update { it.copy(isDeletingComment = true) }
            manageSubmissionComments.deleteComment(commentId)
                .onSuccess {
                    _state.update { it.copy(isDeletingComment = false) }
                    _events.send(AssignmentDetailUiEvent.CommentDeleted)
                    loadComments()
                }
                .onFailure {
                    _state.update { it.copy(isDeletingComment = false) }
                    _events.send(AssignmentDetailUiEvent.ShowError(R.string.error_unexpected))
                }
        }
    }

    /**
     * Inicia el proceso de entrega de la tarea.
     */
    fun submitAssignment(onlineText: String, fileUris: List<String>, existingFileUrls: List<String> = emptyList()) {
        if (_state.value.isSubmitting || _state.value.isDeleting) return
        val assignment = _state.value.assignment ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            submitAssignment(assignment.id, assignment.courseId, assignment.cmId, onlineText, fileUris, existingFileUrls)
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(AssignmentDetailUiEvent.SubmissionSuccess)
                }
                .onFailure {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(AssignmentDetailUiEvent.ShowError(R.string.error_unexpected))
                }
        }
    }

    /**
     * Inicia el proceso de borrado de la entrega.
     */
    fun deleteSubmission() {
        if (_state.value.isSubmitting || _state.value.isDeleting) return
        val assignment = _state.value.assignment ?: return
        val userId = sessionRepo.session.value?.user?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            deleteAssignmentSubmission(assignment.id, userId)
                .onSuccess {
                    _state.update { it.copy(isDeleting = false) }
                    _events.send(AssignmentDetailUiEvent.DeletionSuccess)
                    loadAssignment() // Recargamos para que todo vuelva a "Sin entregar".
                }
                .onFailure {
                    _state.update { it.copy(isDeleting = false) }
                    _events.send(AssignmentDetailUiEvent.ShowError(R.string.error_unexpected))
                }
        }
    }
}