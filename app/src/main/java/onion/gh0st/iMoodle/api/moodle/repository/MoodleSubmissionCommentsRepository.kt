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
 * El "cartero" que lleva y trae los mensajes de los comentarios.
 */
@Singleton
class MoodleSubmissionCommentsRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : SubmissionCommentsRepository {

    /**
     * Pide los mensajes a Moodle.
     */
    override suspend fun getComments(cmId: Int, submissionId: Int): Result<List<SubmissionComment>> =
        runCatchingCancellable {
            val params = MoodleParams().apply {
                put("contextlevel", "module")
                put("instanceid", cmId)
                put("component", "assignsubmission_comments")
                put("area", "submission_comments")
                put("itemid", submissionId)
            }
            val res = sessionApi.requireApi().callREST("core_comment_get_comments", params)
            val arr = JSONObject(res).optJSONArray("comments") ?: return@runCatchingCancellable emptyList()

            // Convertimos la lista fea de Moodle en nuestros objetos bonitos.
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                SubmissionComment(
                    id = c.optInt("id"),
                    author = c.optString("fullname").takeIf { it.isNotBlank() } ?: "Desconocido",
                    content = c.optString("content"),
                    createdAt = c.optLong("timecreated"),
                )
            }
        }

    /**
     * Envía un mensaje nuevo.
     */
    override suspend fun addComment(cmId: Int, submissionId: Int, content: String): Result<Unit> =
        runCatchingCancellable {
            sessionApi.requireApi().callRESTWithFormBody("core_comment_add_comments", mutableMapOf(
                "contextlevel" to "module", "instanceid" to cmId.toString(),
                "component" to "assignsubmission_comments", "area" to "submission_comments",
                "itemid" to submissionId.toString(), "comments[0][content]" to content,
                "comments[0][format]" to "1",
            ))
            Unit
        }

    /**
     * Borra un mensaje tuyo.
     */
    override suspend fun deleteComment(commentId: Int): Result<Unit> =
        runCatchingCancellable {
            sessionApi.requireApi().callRESTWithFormBody("core_comment_delete_comments", mapOf("comments[0]" to commentId.toString()))
            Unit
        }
}