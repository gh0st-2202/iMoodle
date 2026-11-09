package onion.gh0st.iMoodle.domain.usecase.assignment

import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository
import javax.inject.Inject

/**
 * El "gerente" que gestiona el borrado de una entrega.
 */
class DeleteAssignmentSubmission @Inject constructor(
    private val repository: AssignmentSubmissionRepository,
) {
    suspend operator fun invoke(assignmentId: Int, userId: Int): Result<Unit> =
        repository.deleteSubmission(assignmentId, userId)
}