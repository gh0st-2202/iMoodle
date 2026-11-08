package onion.gh0st.iMoodle.domain.repository.forum

import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.model.forum.ForumPost

/**
 * Contrato de acceso a los foros de Moodle.
 *
 * ## Qué es un foro
 *
 * En Moodle, un foro es una actividad de discusión. Se organiza en
 * dos niveles:
 *
 * - **Discusiones** (o "hilos"): cada conversación, con un título y
 *   un mensaje inicial.
 * - **Posts**: los mensajes individuales dentro de una discusión.
 *
 * La app muestra primero la lista de discusiones, y al pulsar una,
 * la lista de posts.
 *
 * ## Por qué no hay caché
 *
 * A diferencia de los cursos y las tareas, los foros no se cachean.
 * Los motivos:
 *
 * - Cambian con frecuencia (posts nuevos, respuestas, ediciones).
 * - Cachearlos mostraría contenido desactualizado.
 * - La consulta es relativamente ligera, así que no merece la pena
 *   el coste de sincronización.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleForumRepository` (consulta siempre a la
 *   red).
 * - **Tests**: `FakeForumRepository` (devuelve datos controlados).
 *
 * ## Quién la consume
 *
 * - `GetForumDiscussions`.
 * - `GetDiscussionPosts`.
 * - `ForumViewModel`, `DiscussionViewModel`.
 */
interface ForumRepository {

    /**
     * Devuelve la lista de discusiones de un foro.
     *
     * ## Qué incluye
     *
     * Los datos básicos de cada discusión: título, autor, fecha y
     * número de respuestas. No incluye los posts individuales.
     *
     * ## Cuándo se llama
     *
     * Al abrir un foro.
     *
     * @param forumId ID del foro.
     * @return Lista de discusiones con datos básicos.
     */
    suspend fun getDiscussions(forumId: Int): List<ForumDiscussion>

    /**
     * Devuelve los posts de una discusión, ordenados por fecha
     * ascendente.
     *
     * ## Orden
     *
     * Los posts se devuelven ordenados de más antiguo a más reciente.
     * El primero es el mensaje inicial de la discusión; los
     * siguientes son las respuestas.
     *
     * ## Cuándo se llama
     *
     * Al abrir una discusión concreta.
     *
     * @param discussionId ID de la discusión.
     * @return Lista de posts ordenados.
     */
    suspend fun getPosts(discussionId: Int): List<ForumPost>
}