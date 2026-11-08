package onion.gh0st.iMoodle.domain.usecase.assignment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.fakes.FakeAssignmentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios de [GetAssignmentDetail].
 *
 * Contrato:
 * - Detalle encontrado → devuelve el modelo.
 * - Detalle no encontrado → lanza `AssignmentNotFoundException`.
 * - Error de red → se propaga tal cual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetAssignmentDetailTest {

    private val repo = FakeAssignmentRepository()
    private val useCase = GetAssignmentDetail(repo)

    /**
     * Con detalle en el repositorio, devuelve el modelo.
     */
    @Test
    fun `returns detail from repository`() = runTest {
        val detail = detail(id = 42)
        repo.assignmentDetail = detail

        val result = useCase(courseId = 1, assignmentId = 42)

        assertEquals(detail, result)
    }

    /**
     * Sin detalle, lanza `AssignmentNotFoundException`.
     */
    @Test
    fun `missing detail throws AssignmentNotFoundException`() = runTest {
        repo.assignmentDetail = null

        val exception = runCatching {
            useCase(courseId = 1, assignmentId = 42)
        }.exceptionOrNull()

        assertTrue(exception is GetAssignmentDetail.AssignmentNotFoundException)
    }

    /**
     * Un error del repositorio se propaga tal cual (no se convierte
     * en "no encontrado").
     */
    @Test
    fun `repository error propagates unchanged`() = runTest {
        repo.detailError = RuntimeException("network boom")

        val exception = runCatching {
            useCase(courseId = 1, assignmentId = 42)
        }.exceptionOrNull()

        assertTrue(exception is RuntimeException)
        assertEquals("network boom", exception?.message)
    }

    // --- Helpers ---

    private fun detail(id: Int) = AssignmentDetail(
        id = id,
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
        lastAttemptDate = null,
        files = emptyList(),
    )
}