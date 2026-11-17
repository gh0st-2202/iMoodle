package onion.gh0st.iMoodle.ui.assignments

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.usecase.assignment.DeleteAssignmentSubmission
import onion.gh0st.iMoodle.domain.usecase.assignment.GetAssignmentDetail
import onion.gh0st.iMoodle.domain.usecase.assignment.ManageSubmissionComments
import onion.gh0st.iMoodle.domain.usecase.assignment.SubmitAssignment
import onion.gh0st.iMoodle.domain.usecase.file.DownloadCourseFile
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.fakes.FakeAssignmentRepository
import onion.gh0st.iMoodle.fakes.FakeAssignmentSubmissionRepository
import onion.gh0st.iMoodle.fakes.FakeFileDownloader
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.FakeSubmissionCommentsRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [AssignmentDetailViewModel].
 *
 * ## Qué cubren
 *
 * - La carga del detalle (éxito, tarea inexistente, error).
 * - La **traducción de errores** al mensaje que se enseña: red, motivo de
 *   rechazo de Moodle y mensaje genérico.
 * - La entrega y su recarga posterior, el borrado de la entrega (con el
 *   usuario de la sesión) y la gestión de comentarios.
 *
 * Esta clase dejó de compilar al ampliar el ViewModel en la v2.4; ahora
 * se construye con todos sus colaboradores (fakes en memoria).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeAssignmentRepository()
    private val submissionRepo = FakeAssignmentSubmissionRepository()
    private val commentsRepo = FakeSubmissionCommentsRepository()
    private val sessionRepo = FakeSessionRepository(TestSessions.active())
    private val observeSession = ObserveSession(sessionRepo)
    private val fileDownloader = FakeFileDownloader()

    /**
     * Crea el ViewModel con los fakes de la prueba.
     *
     * @param courseId Curso que recibe por argumentos.
     * @param assignmentId Tarea que recibe por argumentos.
     * @return El ViewModel ya inicializado (lanza la carga al crearse).
     */
    private fun createViewModel(
        courseId: Int = 1,
        assignmentId: Int = 42,
    ): AssignmentDetailViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "COURSE_ID" to courseId,
                "ASSIGNMENT_ID" to assignmentId,
            ),
        )
        return AssignmentDetailViewModel(
            savedStateHandle = savedState,
            observeSession = observeSession,
            getAssignmentDetail = GetAssignmentDetail(fakeRepo),
            submitAssignment = SubmitAssignment(submissionRepo),
            deleteAssignmentSubmission = DeleteAssignmentSubmission(submissionRepo),
            manageSubmissionComments = ManageSubmissionComments(commentsRepo),
            downloadCourseFile = DownloadCourseFile(observeSession, fileDownloader),
        )
    }

    @Test
    fun `successful load publishes assignment`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(42, assignment?.id)
        }
    }

    @Test
    fun `state exposes the id of the logged user`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)

        val vm = createViewModel()

        assertEquals(TestSessions.user().id, vm.state.value.currentUserId)
    }

    @Test
    fun `missing assignment exposes error event`() = runTest {
        fakeRepo.assignmentDetail = null

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertNull(assignment)
        }
        vm.events.test {
            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            assertNull(event.arg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unexpected error exposes generic error event`() = runTest {
        fakeRepo.detailError = RuntimeException("boom")

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.assignment)
        vm.events.test {
            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network failure exposes network error event`() = runTest {
        fakeRepo.detailError = AuthException.Network(java.io.IOException("sin red"))

        val vm = createViewModel()

        vm.events.test {
            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_network, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful submission emits success and reloads the assignment`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)
        val vm = createViewModel()

        vm.events.test {
            vm.submitAssignment(onlineText = "hola", fileUris = listOf("content://a/b"))

            assertEquals(AssignmentDetailUiEvent.SubmissionSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val submission = submissionRepo.lastSubmission
        assertEquals(42, submission?.assignmentId)
        assertEquals("hola", submission?.onlineText)
        assertEquals(listOf("content://a/b"), submission?.newFileUris)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `rejected submission shows the reason given by moodle`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)
        submissionRepo.submitResult = Result.failure(IllegalStateException("Las entregas están cerradas"))
        val vm = createViewModel()

        vm.events.test {
            vm.submitAssignment(onlineText = "hola", fileUris = emptyList())

            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_assignment_operation, event.messageResId)
            assertEquals("Las entregas están cerradas", event.arg)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `delete submission uses the id of the session user`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)
        val vm = createViewModel()

        vm.events.test {
            vm.deleteSubmission()

            assertEquals(AssignmentDetailUiEvent.DeletionSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(42 to TestSessions.user().id, submissionRepo.lastDeletion)
    }

    @Test
    fun `comments are loaded when the plugin is enabled and a submission exists`() = runTest {
        commentsRepo.comments = listOf(comment(id = 7, authorId = 1))
        fakeRepo.assignmentDetail = detail(id = 42, submissionId = 99, plugins = setOf("comments"))

        val vm = createViewModel()

        assertEquals(1, commentsRepo.getCalls)
        assertEquals(listOf(7), vm.state.value.comments.map { it.id })
    }

    @Test
    fun `comments are not requested when the plugin is disabled`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42, submissionId = 99, plugins = emptySet())

        createViewModel()

        assertEquals(0, commentsRepo.getCalls)
    }

    @Test
    fun `posting a comment publishes it and reloads the thread`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42, submissionId = 99, plugins = setOf("comments"))
        val vm = createViewModel()

        vm.events.test {
            vm.postComment("Gracias, profe")

            assertEquals(AssignmentDetailUiEvent.CommentPosted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("Gracias, profe"), commentsRepo.addedComments)
        assertEquals(2, commentsRepo.getCalls)
    }

    @Test
    fun `blank comments are ignored`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42, submissionId = 99, plugins = setOf("comments"))
        val vm = createViewModel()

        vm.postComment("   ")

        assertTrue(commentsRepo.addedComments.isEmpty())
    }

    @Test
    fun `deleting a comment calls the repository with its id`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42, submissionId = 99, plugins = setOf("comments"))
        val vm = createViewModel()

        vm.deleteComment(7)

        assertEquals(listOf(7), commentsRepo.deletedCommentIds)
    }

    /**
     * Construye un detalle de tarea mínimo para los tests.
     *
     * @param id Identificador de la tarea.
     * @param submissionId Identificador de la entrega, o `null` si no hay.
     * @param plugins Plugins de entrega activados.
     * @return El detalle de prueba.
     */
    private fun detail(
        id: Int,
        submissionId: Int? = null,
        plugins: Set<String> = emptySet(),
    ) = AssignmentDetail(
        id = id,
        courseId = 1,
        cmId = 100 + id,
        name = "Tarea $id",
        description = "",
        dueDate = null,
        allowSubmissionsFromDate = null,
        cutoffDate = null,
        maxGrade = 10.0,
        submissionStatus = null,
        submissionGrade = null,
        gradeDisplay = null,
        submissionGraded = false,
        canSubmit = true,
        canEdit = true,
        lastAttemptDate = null,
        submissionId = submissionId,
        files = emptyList(),
        enabledSubmissionPlugins = plugins,
    )

    /**
     * Construye un comentario de prueba.
     *
     * @param id Identificador del comentario.
     * @param authorId Identificador del autor.
     * @return El comentario de prueba.
     */
    private fun comment(id: Int, authorId: Int) = SubmissionComment(
        id = id,
        author = "Autor $authorId",
        content = "Texto",
        createdAt = 1_700_000_000L,
        authorId = authorId,
        canDelete = false,
    )
}
