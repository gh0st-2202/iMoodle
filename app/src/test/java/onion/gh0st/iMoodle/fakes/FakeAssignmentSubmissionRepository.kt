package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.assignment.AssignmentSubmission
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository

/**
 * Repositorio de entregas para los tests: no toca la red y guarda las
 * llamadas recibidas para poder comprobarlas.
 */
class FakeAssignmentSubmissionRepository : AssignmentSubmissionRepository {

    /** Resultado que devolverá [submit]. */
    var submitResult: Result<Unit> = Result.success(Unit)

    /** Resultado que devolverá [deleteSubmission]. */
    var deleteResult: Result<Unit> = Result.success(Unit)

    /** Última entrega recibida en [submit], o `null` si no hubo ninguna. */
    var lastSubmission: AssignmentSubmission? = null
        private set

    /** Pareja `(assignmentId, userId)` de la última llamada a [deleteSubmission]. */
    var lastDeletion: Pair<Int, Int>? = null
        private set

    override suspend fun submit(submission: AssignmentSubmission): Result<Unit> {
        lastSubmission = submission
        return submitResult
    }

    override suspend fun deleteSubmission(assignmentId: Int, userId: Int): Result<Unit> {
        lastDeletion = assignmentId to userId
        return deleteResult
    }
}
