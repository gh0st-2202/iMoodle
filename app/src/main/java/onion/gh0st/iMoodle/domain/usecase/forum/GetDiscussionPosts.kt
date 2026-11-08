package onion.gh0st.iMoodle.domain.usecase.forum

import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener los posts de una discusión.
 *
 * ## Qué hace
 *
 * Delega en `ForumRepository.getPosts`. No añade lógica adicional,
 * pero encapsula la operación para que el ViewModel no dependa
 * directamente del repositorio.
 *
 * ## Orden de los posts
 *
 * El repositorio devuelve los posts ordenados por fecha ascendente
 * (del más antiguo al más reciente). El primero es el mensaje
 * inicial de la discusión; los siguientes son las respuestas. Este
 * orden es el que espera la UI, así que el caso de uso no reordena.
 *
 * ## Quién lo usa
 *
 * - `DiscussionViewModel`.
 *
 * @param repository Contrato de acceso a foros.
 */
class GetDiscussionPosts @Inject constructor(
    private val repository: ForumRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Manejo de errores
     *
     * Cualquier excepción del repositorio (red, parseo, etc.) se
     * propaga tal cual. El ViewModel la captura y muestra un mensaje
     * genérico.
     *
     * @param discussionId ID de la discusión.
     * @return Lista de posts ordenados por fecha ascendente.
     */
    suspend operator fun invoke(discussionId: Int): List<ForumPost> =
        repository.getPosts(discussionId)
}