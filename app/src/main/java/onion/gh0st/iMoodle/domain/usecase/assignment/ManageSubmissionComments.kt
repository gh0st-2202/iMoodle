package onion.gh0st.iMoodle.domain.usecase.assignment

import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.repository.assignment.SubmissionCommentsRepository
import javax.inject.Inject

/**
 * El "gerente" encargado de los comentarios de una entrega.
 *
 * Tiene tres trabajos: Leer los comentarios (getComments), Escribir uno nuevo (addComment)
 * y Borrar uno tuyo (deleteComment).
 */
class ManageSubmissionComments @Inject constructor(
    private val repository: SubmissionCommentsRepository,
) {
    /**
     * Obtiene los comentarios de una entrega.
     *
     * @param cmId Identificador del módulo del curso.
     * @param submissionId Identificador de la entrega.
     * @return Los comentarios, o el fallo ocurrido.
     */
    suspend fun getComments(cmId: Int, submissionId: Int): Result<List<SubmissionComment>> =
        repository.getComments(cmId, submissionId)

    /**
     * Publica un comentario nuevo en una entrega.
     *
     * @param cmId Identificador del módulo del curso.
     * @param submissionId Identificador de la entrega.
     * @param content Texto del comentario.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun addComment(cmId: Int, submissionId: Int, content: String): Result<Unit> =
        repository.addComment(cmId, submissionId, content)

    /**
     * Borra un comentario.
     *
     * @param commentId Identificador del comentario.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun deleteComment(commentId: Int): Result<Unit> =
        repository.deleteComment(commentId)
}