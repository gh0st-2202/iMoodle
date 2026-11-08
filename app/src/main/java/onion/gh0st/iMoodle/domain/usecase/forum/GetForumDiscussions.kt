package onion.gh0st.iMoodle.domain.usecase.forum

import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener las discusiones de un foro.
 *
 * ## Qué hace
 *
 * Delega en `ForumRepository.getDiscussions`. No añade lógica
 * adicional, pero encapsula la operación para que el ViewModel no
 * dependa directamente del repositorio.
 *
 * ## Por qué existe aunque sea una simple delegación
 *
 * Porque es el punto de entrada del dominio. Si mañana hay que añadir
 * lógica (por ejemplo, cachear el resultado, combinar con otra
 * fuente, o filtrar por algún criterio), se añade aquí sin tocar el
 * ViewModel. Es una inversión a futuro que cuesta muy poco.
 *
 * ## Quién lo usa
 *
 * - `ForumViewModel`.
 *
 * @param repository Contrato de acceso a foros.
 */
class GetForumDiscussions @Inject constructor(
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
     * @param forumId ID del foro.
     * @return Lista de discusiones.
     */
    suspend operator fun invoke(forumId: Int): List<ForumDiscussion> =
        repository.getDiscussions(forumId)
}