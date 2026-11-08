package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [ForumRepository] sobre la API REST de
 * Moodle.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de hablar con Moodle para obtener la información de
 * los **foros** (el módulo `mod_forum`). Un foro tiene dos niveles:
 *
 * - **Discusiones** (`ForumDiscussion`): los "hilos" o temas abiertos.
 *   Cada uno tiene un título, un autor, una fecha y un número de
 *   respuestas.
 * - **Posts** (`ForumPost`): los mensajes dentro de una discusión.
 *   El primero es el mensaje inicial; los siguientes son respuestas.
 *
 * La app muestra primero la lista de discusiones de un foro, y al
 * pulsar una, la lista de posts de esa discusión.
 *
 * ## Qué NO hace
 *
 * - **No guarda nada en Room**. Los foros cambian mucho (posts
 *   nuevos, respuestas, ediciones) y cachearlos sería más un problema
 *   que una ayuda: el usuario vería contenido desactualizado. Se
 *   consultan siempre frescos.
 * - **No permite escribir posts**. La app solo consulta; no
 *   implementa la publicación.
 * - **No gestiona suscripciones ni notificaciones** de foros.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 */
@Singleton
class MoodleForumRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : ForumRepository {

    /**
     * Lista de discusiones de un foro.
     *
     * ## Qué hace
     *
     * Hace una llamada a `mod_forum_get_forum_discussions` con el ID
     * del foro. Moodle devuelve un JSON con todas las discusiones y
     * sus datos básicos. Aquí se mapean a `ForumDiscussion`.
     *
     * ## Qué NO incluye
     *
     * Los posts de cada discusión. Eso se pide aparte con
     * [getPosts], solo cuando el usuario entra en una discusión.
     *
     * @param forumId ID del foro.
     * @return Lista de discusiones con datos básicos.
     */
    override suspend fun getDiscussions(forumId: Int): List<ForumDiscussion> {
        val requestParams = MoodleParams().apply { put("forumid", forumId) }
        val forumResponseJson = JSONObject(callRest("mod_forum_get_forum_discussions", requestParams))
        val discussionsArray = forumResponseJson.optJSONArray("discussions") ?: return emptyList()
        return (0 until discussionsArray.length()).map { discussionIndex ->
            parseDiscussion(discussionsArray.getJSONObject(discussionIndex))
        }
    }

    /**
     * Posts de una discusión, ordenados por fecha ascendente.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pide los posts a `mod_forum_get_discussion_posts`, indicando
     *    que se ordenen por fecha de creación (`created`) en orden
     *    ascendente (`ASC`). Así el mensaje original queda primero y
     *    las respuestas después, como en cualquier foro.
     * 2. Comprueba que Moodle no haya devuelto un JSON de error.
     * 3. Mapea cada post a `ForumPost`.
     *
     * ## Manejo de errores
     *
     * - Si Moodle devuelve un error de negocio, se lanza
     *   `IllegalStateException` con el mensaje original.
     * - Los errores de red los gestiona el `MoodleApiClient`.
     *
     * @param discussionId ID de la discusión.
     * @return Lista de posts ordenados.
     * @throws IllegalStateException si Moodle devuelve un JSON de
     *   error.
     */
    override suspend fun getPosts(discussionId: Int): List<ForumPost> {
        val postsResponseJson = fetchPostsJson(discussionId)
        ensureNoMoodleError(postsResponseJson)
        val postsArray = postsResponseJson.optJSONArray("posts") ?: return emptyList()
        return parsePosts(postsArray)
    }

    // --- Obtención de datos de red ---

    /**
     * Descarga los posts de una discusión.
     *
     * @param discussionId ID de la discusión.
     * @return `JSONObject` con el array `posts[]`.
     */
    private suspend fun fetchPostsJson(discussionId: Int): JSONObject {
        val requestParams = MoodleParams().apply {
            put("discussionid", discussionId)
            put("sortby", "created")
            put("sortdirection", "ASC")
        }
        return JSONObject(callRest("mod_forum_get_discussion_posts", requestParams))
    }

    /**
     * Lanza una excepción si el JSON es un error de Moodle.
     *
     * @param postsResponseJson Respuesta de Moodle.
     * @throws IllegalStateException si el JSON contiene una clave
     *   `exception`.
     */
    private fun ensureNoMoodleError(postsResponseJson: JSONObject) {
        if (!postsResponseJson.has("exception")) return
        val errorMessage = postsResponseJson.optString("message", "Error desconocido")
        throw IllegalStateException(errorMessage)
    }

    /**
     * Convierte un `JSONObject` de discusión en `ForumDiscussion`.
     *
     * @param discussionJson `JSONObject` de una discusión.
     * @return Discusión de dominio.
     */
    private fun parseDiscussion(discussionJson: JSONObject) = ForumDiscussion(
        id = discussionJson.optInt("discussion"),
        name = discussionJson.optString("name"),
        message = discussionJson.optString("message"),
        author = discussionJson.optString("userfullname"),
        created = discussionJson.optLong("created"),
        numReplies = discussionJson.optInt("numreplies"),
    )

    /**
     * Convierte un array de posts en una lista de dominio.
     *
     * @param postsArray Array de posts.
     * @return Lista de posts de dominio.
     */
    private fun parsePosts(postsArray: JSONArray): List<ForumPost> =
        (0 until postsArray.length()).map { postIndex ->
            parsePost(postsArray.getJSONObject(postIndex))
        }

    /**
     * Convierte un `JSONObject` de post en `ForumPost`.
     *
     * @param postJson `JSONObject` de un post.
     * @return Post de dominio.
     */
    private fun parsePost(postJson: JSONObject) = ForumPost(
        id = postJson.optInt("id"),
        subject = postJson.optString("subject"),
        message = postJson.optString("message"),
        author = resolveAuthor(postJson),
        created = resolveCreatedAt(postJson),
        isReply = postJson.optInt("parentid", 0) > 0,
    )

    /**
     * Resuelve el nombre del autor de un post.
     *
     * ## Por qué hay dos formas
     *
     * Moodle puede devolver el autor en dos sitios distintos según la
     * versión:
     *
     * - `author.fullname` en versiones recientes.
     * - `userfullname` en versiones antiguas.
     *
     * Se prefiere el primero y se cae al segundo si no está.
     *
     * @param postJson `JSONObject` del post.
     * @return Nombre del autor, o `"Usuario desconocido"` si no hay.
     */
    private fun resolveAuthor(postJson: JSONObject): String =
        postJson.optJSONObject("author")?.optString("fullname")
            ?: postJson.optString("userfullname")
            ?: "Usuario desconocido"

    /**
     * Resuelve la fecha de creación de un post.
     *
     * ## Por qué hay dos formas
     *
     * Moodle usa `timecreated` en versiones recientes y `created` en
     * versiones antiguas. Se prefiere el primero.
     *
     * @param postJson `JSONObject` del post.
     * @return Timestamp en **segundos**, o `0` si no está.
     */
    private fun resolveCreatedAt(postJson: JSONObject): Long {
        val primaryCreatedAt = postJson.optLong("timecreated", 0L)
        return if (primaryCreatedAt > 0) primaryCreatedAt
        else postJson.optLong("created", 0L)
    }

    /**
     * Ejecuta una función REST contra el cliente autenticado.
     *
     * @param function Nombre de la función de Moodle.
     * @param params Parámetros ya montados.
     * @return Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si no hay sesión activa.
     */
    private suspend fun callRest(function: String, params: MoodleParams): String =
        sessionApi.requireApi().callREST(function, params)
}