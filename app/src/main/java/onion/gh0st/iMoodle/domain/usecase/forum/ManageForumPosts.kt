package onion.gh0st.iMoodle.domain.usecase.forum

import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import javax.inject.Inject

/**
 * Caso de uso: comprobar permisos y publicar en foros.
 *
 * ## Qué hace
 *
 * Agrupa las tres operaciones de escritura del foro:
 *
 * - Preguntar si el alumno puede abrir una discusión.
 * - Abrir una discusión nueva.
 * - Responder en una discusión existente.
 *
 * ## Validación
 *
 * Antes de enviar nada comprueba que el asunto y el mensaje no estén
 * vacíos. La UI también lo valida, pero la regla vive aquí para que
 * ningún camino pueda publicar un mensaje en blanco.
 *
 * ## Quién lo usa
 *
 * - `ForumViewModel` (nuevo tema).
 * - `DiscussionViewModel` (respuesta).
 *
 * @param repository Contrato de acceso a foros.
 */
class ManageForumPosts @Inject constructor(
    private val repository: ForumRepository,
) {

    /**
     * Comprueba si se puede abrir una discusión nueva.
     *
     * @param forumId ID del foro.
     * @return `true` si Moodle lo permite.
     */
    suspend fun canAddDiscussion(forumId: Int): Boolean =
        repository.canAddDiscussion(forumId)

    /**
     * Abre una discusión nueva.
     *
     * @param forumId ID del foro.
     * @param subject Asunto del tema.
     * @param message Mensaje inicial.
     * @return ID de la discusión creada.
     * @throws IllegalArgumentException si el asunto o el mensaje están vacíos.
     */
    suspend fun addDiscussion(forumId: Int, subject: String, message: String): Int {
        require(subject.isNotBlank()) { "El asunto no puede estar vacío" }
        require(message.isNotBlank()) { "El mensaje no puede estar vacío" }
        return repository.addDiscussion(forumId, subject.trim(), message.trim())
    }

    /**
     * Responde en una discusión.
     *
     * @param parentPostId ID del mensaje al que se responde.
     * @param subject Asunto de la respuesta.
     * @param message Cuerpo de la respuesta.
     * @return ID del post creado.
     * @throws IllegalArgumentException si el asunto o el mensaje están vacíos.
     */
    suspend fun addReply(parentPostId: Int, subject: String, message: String): Int {
        require(subject.isNotBlank()) { "El asunto no puede estar vacío" }
        require(message.isNotBlank()) { "El mensaje no puede estar vacío" }
        return repository.addReply(parentPostId, subject.trim(), message.trim())
    }
}
