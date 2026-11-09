package onion.gh0st.iMoodle.domain.repository.assignment

import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment

/**
 * El contrato para gestionar los comentarios de una entrega.
 */
interface SubmissionCommentsRepository {

    /**
     * Pide a Moodle la lista de mensajes del "chat" de una entrega.
     */
    suspend fun getComments(cmId: Int, submissionId: Int): Result<List<SubmissionComment>>

    /**
     * Escribe un mensaje nuevo en el "chat" de la entrega.
     */
    suspend fun addComment(cmId: Int, submissionId: Int, content: String): Result<Unit>

    /**
     * Borra un mensaje que tú hayas escrito.
     */
    suspend fun deleteComment(commentId: Int): Result<Unit>
}