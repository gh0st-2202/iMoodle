package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.repository.assignment.SubmissionCommentsRepository

/**
 * Repositorio de comentarios de entregas para los tests: devuelve una
 * lista fija y registra las operaciones recibidas.
 */
class FakeSubmissionCommentsRepository : SubmissionCommentsRepository {

    /** Comentarios que devolverá [getComments]. */
    var comments: List<SubmissionComment> = emptyList()

    /** Número de veces que se han pedido los comentarios. */
    var getCalls: Int = 0
        private set

    /** Textos publicados con [addComment]. */
    val addedComments = mutableListOf<String>()

    /** Identificadores borrados con [deleteComment]. */
    val deletedCommentIds = mutableListOf<Int>()

    override suspend fun getComments(cmId: Int, submissionId: Int): Result<List<SubmissionComment>> {
        getCalls++
        return Result.success(comments)
    }

    override suspend fun addComment(cmId: Int, submissionId: Int, content: String): Result<Unit> {
        addedComments.add(content)
        return Result.success(Unit)
    }

    override suspend fun deleteComment(commentId: Int): Result<Unit> {
        deletedCommentIds.add(commentId)
        return Result.success(Unit)
    }
}
