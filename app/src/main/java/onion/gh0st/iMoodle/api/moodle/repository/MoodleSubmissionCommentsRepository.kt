package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.repository.assignment.SubmissionCommentsRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación contra Moodle de los comentarios de una entrega.
 *
 * ## Qué son estos comentarios
 *
 * Es el hilo de mensajes que alumno y profesor pueden intercambiar bajo
 * una entrega (el plugin `assignsubmission_comments`). Moodle los gestiona
 * con las funciones genéricas `core_comment_*`, identificando el hilo con:
 *
 * - `contextlevel = module` e `instanceid = cmId`: la actividad concreta.
 * - `component = assignsubmission_comments` y `area = submission_comments`.
 * - `itemid = submissionId`: la entrega a la que pertenece el hilo.
 *
 * @param sessionApi Proveedor del cliente REST de la sesión activa.
 */
@Singleton
class MoodleSubmissionCommentsRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : SubmissionCommentsRepository {

    /**
     * Pide a Moodle los comentarios de una entrega.
     *
     * Para cada comentario se conserva quién lo escribió (nombre e
     * identificador) y si el usuario actual puede borrarlo, que es lo que
     * decide Moodle (`delete`) y evita tener que comparar nombres.
     *
     * @param cmId Identificador del módulo del curso (`cmid`).
     * @param submissionId Identificador de la entrega.
     * @return Los comentarios en el orden que devuelve Moodle, o el fallo ocurrido.
     */
    override suspend fun getComments(cmId: Int, submissionId: Int): Result<List<SubmissionComment>> =
        runCatchingCancellable {
            val params = commentThreadParams(cmId, submissionId)
            val response = sessionApi.requireApi().callREST("core_comment_get_comments", params)

            JSONObject(response).optJSONArray("comments").objects().map { commentJson ->
                SubmissionComment(
                    id = commentJson.optInt("id"),
                    author = commentJson.optString("fullname").takeIf { name -> name.isNotBlank() }
                        ?: UNKNOWN_AUTHOR,
                    content = commentJson.optString("content"),
                    createdAt = commentJson.optLong("timecreated"),
                    authorId = commentJson.optInt("userid"),
                    canDelete = commentJson.optBoolean("delete", false),
                )
            }
        }

    /**
     * Publica un comentario nuevo en una entrega.
     *
     * Se envía por `POST` y con la identidad del hilo **dentro** de
     * `comments[0]`, que es la estructura que exige
     * `core_comment_add_comments`: recibe una lista de comentarios, y
     * cada uno lleva su propio `contextlevel`, `instanceid`, `component`,
     * `area`, `itemid` y `content`. Si esos campos viajan en el nivel
     * superior, Moodle los ignora y responde
     * `invalid_parameter_exception` por faltarle claves a cada comentario.
     *
     * @param cmId Identificador del módulo del curso (`cmid`).
     * @param submissionId Identificador de la entrega.
     * @param content Texto del comentario.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun addComment(cmId: Int, submissionId: Int, content: String): Result<Unit> =
        runCatchingCancellable {
            val params = commentThreadParams(cmId, submissionId)
                .build()
                .mapKeys { (name, _) -> "$COMMENTS_PREFIX[$name]" } +
                mapOf("$COMMENTS_PREFIX[content]" to content)
            sessionApi.requireApi().callRESTWithFormBody("core_comment_add_comments", params)
            Unit
        }

    /**
     * Borra un comentario del propio usuario.
     *
     * @param commentId Identificador del comentario.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun deleteComment(commentId: Int): Result<Unit> =
        runCatchingCancellable {
            sessionApi.requireApi().callRESTWithFormBody(
                "core_comment_delete_comments",
                mapOf(COMMENTS_PREFIX to commentId.toString()),
            )
            Unit
        }

    /**
     * Construye los parámetros que identifican el hilo de comentarios de
     * una entrega.
     *
     * @param cmId Identificador del módulo del curso (`cmid`).
     * @param submissionId Identificador de la entrega.
     * @return Parámetros comunes a las tres operaciones sobre el hilo.
     */
    private fun commentThreadParams(cmId: Int, submissionId: Int): MoodleParams =
        MoodleParams().apply {
            put("contextlevel", "module")
            put("instanceid", cmId)
            put("component", "assignsubmission_comments")
            put("area", "submission_comments")
            put("itemid", submissionId)
        }

    private companion object {
        /** Autor mostrado cuando Moodle no informa del nombre. */
        const val UNKNOWN_AUTHOR = "Desconocido"

        /** Primer elemento del array `comments` que esperan las funciones `core_comment_*`. */
        const val COMMENTS_PREFIX = "comments[0]"
    }
}
