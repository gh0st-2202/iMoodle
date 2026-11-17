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
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.usecase.file.DownloadCourseFile
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.domain.usecase.assignment.DeleteAssignmentSubmission
import onion.gh0st.iMoodle.domain.usecase.assignment.GetAssignmentDetail
import onion.gh0st.iMoodle.domain.usecase.assignment.ManageSubmissionComments
import onion.gh0st.iMoodle.domain.usecase.assignment.SubmitAssignment
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado completo de la pantalla de detalle de una tarea.
 *
 * @property isLoading `true` mientras se carga el detalle de la tarea.
 * @property assignment Detalle de la tarea, o `null` si aún no se ha cargado o no existe.
 * @property isSubmitting `true` mientras se está entregando.
 * @property isDeleting `true` mientras se está borrando la entrega.
 * @property comments Comentarios de la entrega, en el orden que devuelve Moodle.
 * @property isLoadingComments `true` mientras se cargan los comentarios.
 * @property isPostingComment `true` mientras se publica un comentario.
 * @property isDeletingComment `true` mientras se borra un comentario.
 * @property currentUserId Identificador del usuario con la sesión iniciada (`0` si se
 *   desconoce); sirve para saber qué comentarios son suyos.
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
    val currentUserId: Int = 0,
)

/**
 * Eventos de un solo uso de la pantalla de detalle de una tarea.
 *
 * Son cosas que ocurren una vez (mostrar un mensaje, cerrar un diálogo) y
 * que, a diferencia del estado, no deben repetirse al recrear la pantalla.
 */
sealed interface AssignmentDetailUiEvent {

    /**
     * Mostrar un mensaje de error.
     *
     * @property messageResId Recurso del texto del mensaje.
     * @property arg Valor que sustituye a `%1$s` en el texto, si lo usa.
     */
    data class ShowError(@param:StringRes val messageResId: Int, val arg: String? = null) : AssignmentDetailUiEvent

    /** La tarea se ha entregado correctamente. */
    data object SubmissionSuccess : AssignmentDetailUiEvent

    /** La entrega se ha borrado correctamente. */
    data object DeletionSuccess : AssignmentDetailUiEvent

    /** Un comentario se ha publicado correctamente. */
    data object CommentPosted : AssignmentDetailUiEvent

    /** Un comentario se ha borrado correctamente. */
    data object CommentDeleted : AssignmentDetailUiEvent

    /**
     * La descarga de un archivo se ha encolado correctamente.
     *
     * @property fileName Nombre del archivo, para el aviso al usuario.
     */
    data class DownloadStarted(val fileName: String) : AssignmentDetailUiEvent

    /** No se pudo encolar la descarga (sin sesión o error del sistema). */
    data object DownloadFailed : AssignmentDetailUiEvent
}

/**
 * ViewModel de la pantalla de detalle de una tarea.
 *
 * ## Qué gestiona
 *
 * - Carga el detalle de la tarea al crearse y cada vez que se recarga.
 * - Entrega la tarea y borra la entrega.
 * - Carga, publica y borra los comentarios de la entrega.
 *
 * Mientras una operación está en marcha, las demás acciones conflictivas
 * se ignoran (por ejemplo, no se puede entregar mientras se borra), para
 * evitar peticiones cruzadas.
 *
 * ## Errores
 *
 * Los fallos llegan a la pantalla como [AssignmentDetailUiEvent.ShowError].
 * Cuando Moodle rechaza una operación con un motivo concreto (por ejemplo,
 * "las entregas están cerradas") se muestra ese motivo; si falla la red,
 * se indica; en cualquier otro caso, un mensaje genérico. La cancelación
 * de corrutinas nunca se trata como error.
 *
 * ## Argumentos
 *
 * Recibe por `SavedStateHandle` el identificador del curso
 * ([FragmentArgs.COURSE_ID]) y el de la tarea ([FragmentArgs.ASSIGNMENT_ID]).
 * Este último es el identificador de instancia de la tarea (aunque también
 * se acepta el `cmid` del módulo; ver `MoodleAssignmentRepository`).
 *
 * @param savedStateHandle Argumentos de la pantalla.
 * @param observeSession Lectura de la sesión activa (para conocer al
 *   usuario actual).
 * @param getAssignmentDetail Caso de uso que obtiene el detalle de la tarea.
 * @param submitAssignment Caso de uso que entrega la tarea.
 * @param deleteAssignmentSubmission Caso de uso que borra la entrega.
 * @param manageSubmissionComments Caso de uso para los comentarios de la entrega.
 */
@HiltViewModel
class AssignmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSession: ObserveSession,
    private val getAssignmentDetail: GetAssignmentDetail,
    private val submitAssignment: SubmitAssignment,
    private val deleteAssignmentSubmission: DeleteAssignmentSubmission,
    private val manageSubmissionComments: ManageSubmissionComments,
    private val downloadCourseFile: DownloadCourseFile,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>(FragmentArgs.COURSE_ID) ?: 0
    private val assignmentRef: Int = savedStateHandle.get<Int>(FragmentArgs.ASSIGNMENT_ID) ?: 0

    private val _state = MutableStateFlow(
        AssignmentDetailUiState(currentUserId = observeSession.currentUserId ?: 0),
    )

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    private val _events = Channel<AssignmentDetailUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso (mensajes, confirmaciones). */
    val events: Flow<AssignmentDetailUiEvent> = _events.receiveAsFlow()

    init {
        loadAssignment()
    }

    /**
     * Carga (o recarga) el detalle de la tarea y, si procede, sus comentarios.
     *
     * Si la carga falla, se apaga `isLoading` y se emite un error.
     */
    fun loadAssignment() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatchingCancellable { getAssignmentDetail(courseId, assignmentRef) }
                .onSuccess { detail ->
                    _state.update { it.copy(isLoading = false, assignment = detail) }
                    loadComments()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "loadAssignment", exception)
                    _state.update { it.copy(isLoading = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Carga los comentarios de la entrega.
     *
     * No hace nada si la tarea no se ha cargado, si no hay entrega aún o
     * si la tarea no tiene activado el plugin de comentarios. Un fallo al
     * cargarlos se registra pero no molesta al usuario: son información
     * secundaria.
     */
    fun loadComments() {
        val assignment = _state.value.assignment ?: return
        val submissionId = assignment.submissionId ?: return
        if (submissionId <= 0 || COMMENTS_PLUGIN !in assignment.enabledSubmissionPlugins) return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingComments = true) }
            manageSubmissionComments.getComments(assignment.cmId, submissionId)
                .onSuccess { commentsList ->
                    _state.update { it.copy(comments = commentsList, isLoadingComments = false) }
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "loadComments", exception)
                    _state.update { it.copy(isLoadingComments = false) }
                }
        }
    }

    /**
     * Publica un comentario en la entrega y recarga la lista.
     *
     * Se ignora si el texto está vacío o ya hay un comentario enviándose.
     *
     * @param text Texto del comentario.
     */
    fun postComment(text: String) {
        if (text.isBlank() || _state.value.isPostingComment) return
        val assignment = _state.value.assignment ?: return
        val submissionId = assignment.submissionId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isPostingComment = true) }
            manageSubmissionComments.addComment(assignment.cmId, submissionId, text)
                .onSuccess {
                    _state.update { it.copy(isPostingComment = false) }
                    _events.send(AssignmentDetailUiEvent.CommentPosted)
                    loadComments()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "postComment", exception)
                    _state.update { it.copy(isPostingComment = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Borra un comentario y recarga la lista.
     *
     * Se ignora si ya hay un borrado en curso.
     *
     * @param commentId Identificador del comentario a borrar.
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
                .onFailure { exception ->
                    Logging.logError(TAG, "deleteComment", exception)
                    _state.update { it.copy(isDeletingComment = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Entrega la tarea con el texto y los archivos indicados y, si
     * tiene éxito, recarga la tarea para reflejar el nuevo estado.
     *
     * Se ignora si ya hay una entrega o un borrado en curso, o si la
     * tarea aún no se ha cargado.
     *
     * @param onlineText Texto en línea de la entrega (puede estar vacío).
     * @param fileUris URIs de los archivos nuevos elegidos en el teléfono.
     * @param existingFileUrls URLs de los archivos ya entregados que se conservan.
     */
    fun submitAssignment(
        onlineText: String,
        fileUris: List<String>,
        existingFileUrls: List<String> = emptyList(),
    ) {
        if (_state.value.isSubmitting || _state.value.isDeleting) return
        val assignment = _state.value.assignment ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            submitAssignment(
                assignment.id,
                assignment.courseId,
                assignment.cmId,
                onlineText,
                fileUris,
                existingFileUrls,
            )
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(AssignmentDetailUiEvent.SubmissionSuccess)
                    loadAssignment()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "submitAssignment", exception)
                    _state.update { it.copy(isSubmitting = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Borra la entrega del usuario y recarga la tarea para que vuelva a
     * aparecer como "Sin entregar".
     *
     * Se ignora si ya hay una entrega o un borrado en curso, o si no se
     * conoce al usuario.
     */
    fun deleteSubmission() {
        if (_state.value.isSubmitting || _state.value.isDeleting) return
        val assignment = _state.value.assignment ?: return
        val userId = observeSession.currentUserId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            deleteAssignmentSubmission(assignment.id, userId)
                .onSuccess {
                    _state.update { it.copy(isDeleting = false) }
                    _events.send(AssignmentDetailUiEvent.DeletionSuccess)
                    loadAssignment()
                }
                .onFailure { exception ->
                    Logging.logError(TAG, "deleteSubmission", exception)
                    _state.update { it.copy(isDeleting = false) }
                    _events.send(exception.toErrorEvent())
                }
        }
    }

    /**
     * Traduce un fallo en el mensaje que se enseña al usuario.
     *
     * - Fallo de red: aviso de red.
     * - Rechazo con motivo (las operaciones de entrega lanzan
     *   `IllegalStateException` con el mensaje de Moodle): se muestra el motivo.
     * - Cualquier otra cosa: mensaje genérico.
     *
     * @receiver Fallo ocurrido.
     * @return Evento de error listo para emitir.
     */
    private fun Throwable.toErrorEvent(): AssignmentDetailUiEvent.ShowError = when {
        this is AuthException.Network -> AssignmentDetailUiEvent.ShowError(R.string.error_network)
        this is IllegalStateException && !message.isNullOrBlank() ->
            AssignmentDetailUiEvent.ShowError(R.string.error_assignment_operation, message)
        else -> AssignmentDetailUiEvent.ShowError(R.string.error_unexpected)
    }

    /**
     * Descarga un archivo adjunto de la tarea.
     *
     * ## Qué hace
     *
     * Delega en [DownloadCourseFile], que comprueba que haya sesión, y
     * emite el evento para que la pantalla avise al usuario.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario confirma en el diálogo de descarga.
     *
     * @param file Archivo a descargar.
     */
    fun downloadFile(file: CourseFile) {
        viewModelScope.launch {
            downloadCourseFile(file)
                .onSuccess { _events.send(AssignmentDetailUiEvent.DownloadStarted(file.fileName)) }
                .onFailure { exception ->
                    Logging.logError(TAG, "downloadFile", exception)
                    _events.send(AssignmentDetailUiEvent.DownloadFailed)
                }
        }
    }

    private companion object {
        const val TAG = "AssignmentDetailVM"

        /** Nombre del plugin de entrega que habilita los comentarios. */
        const val COMMENTS_PLUGIN = "comments"
    }
}
