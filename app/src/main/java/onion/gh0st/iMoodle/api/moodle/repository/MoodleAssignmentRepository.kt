package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.appendMoodleToken
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.assignment.Assignment
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El "traductor" que convierte los datos crudos de Moodle en objetos que la app entiende.
 *
 * ## ¿Qué hace esta clase?
 * Moodle nos habla en un idioma llamado JSON (un texto lleno de llaves y corchetes).
 * Esta clase coge ese texto, busca las cosas importantes (como las fechas o si puedes entregar)
 * y las mete en nuestras clases `Assignment` y `AssignmentDetail`.
 */
@Singleton
class MoodleAssignmentRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : AssignmentRepository {

    override suspend fun getAssignments(courseIds: List<Int>): List<Assignment> {
        val requestParams = MoodleParams().apply { putArray("courseids", courseIds) }
        val assignmentsResponseJson = JSONObject(callRest("mod_assign_get_assignments", requestParams))
        val coursesArray = assignmentsResponseJson.optJSONArray("courses") ?: return emptyList()
        return flattenAssignments(coursesArray)
    }

    override suspend fun getAssignmentDetail(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail? {
        val assignmentsResponseJson = fetchAssignmentsForCourse(courseId)
        val assignmentJson = findAssignment(assignmentsResponseJson, assignmentId) ?: return null
        val submissionStatusResult = fetchSubmissionStatus(assignmentJson.optInt("id"))
        return buildAssignmentDetail(assignmentJson, submissionStatusResult)
    }

    private suspend fun fetchAssignmentsForCourse(courseId: Int): JSONObject {
        val requestParams = MoodleParams().apply { putArray("courseids", listOf(courseId)) }
        return JSONObject(callRest("mod_assign_get_assignments", requestParams))
    }

    private suspend fun fetchSubmissionStatus(assignmentId: Int): Result<JSONObject> =
        runCatchingCancellable {
            val requestParams = MoodleParams().apply { put("assignid", assignmentId) }
            JSONObject(callRest("mod_assign_get_submission_status", requestParams))
        }

    private suspend fun callRest(function: String, params: MoodleParams): String =
        sessionApi.requireApi().callREST(function, params)

    private fun findAssignment(assignmentsResponseJson: JSONObject, targetAssignmentId: Int): JSONObject? {
        val coursesArray = assignmentsResponseJson.optJSONArray("courses") ?: return null
        for (courseIndex in 0 until coursesArray.length()) {
            val courseJson = coursesArray.getJSONObject(courseIndex)
            val assignmentsArray = courseJson.optJSONArray("assignments") ?: continue
            findAssignmentInArray(assignmentsArray, targetAssignmentId)?.let { return it }
        }
        return null
    }

    private fun findAssignmentInArray(assignmentsArray: JSONArray, targetAssignmentId: Int): JSONObject? =
        (0 until assignmentsArray.length())
            .map { assignmentsArray.getJSONObject(it) }
            .firstOrNull { it.optInt("id") == targetAssignmentId || it.optInt("cmid") == targetAssignmentId }

    private fun flattenAssignments(coursesArray: JSONArray): List<Assignment> {
        val assignments = mutableListOf<Assignment>()
        for (i in 0 until coursesArray.length()) {
            val assignmentsArray = coursesArray.getJSONObject(i).optJSONArray("assignments") ?: continue
            (0 until assignmentsArray.length()).forEach {
                assignments.add(parseAssignment(assignmentsArray.getJSONObject(it)))
            }
        }
        return assignments
    }

    private fun parseAssignment(assignmentJson: JSONObject) = Assignment(
        id = assignmentJson.optInt("id"),
        courseId = assignmentJson.optInt("course"),
        name = assignmentJson.optString("name"),
        description = assignmentJson.optString("intro"),
        dueDate = assignmentJson.optLong("duedate", 0).takeIf { it > 0 },
        allowSubmissionsFromDate = assignmentJson.optLong("allowsubmissionsfromdate", 0).takeIf { it > 0 },
        grade = assignmentJson.optDouble("grade", 0.0).takeIf { it.isNaN().not() },
        submissionStatus = null,
    )

    private fun buildAssignmentDetail(
        assignmentJson: JSONObject,
        submissionStatusResult: Result<JSONObject>,
    ): AssignmentDetail {
        val submissionInfo = SubmissionInfo.from(submissionStatusResult)
        val submissionFiles = parseSubmissionFiles(submissionStatusResult)
        val enabledPlugins = parseEnabledSubmissionPlugins(assignmentJson)
        val canSubmit = calculateCanSubmit(assignmentJson, submissionInfo, submissionStatusResult)

        return AssignmentDetail(
            id = assignmentJson.optInt("id"),
            courseId = assignmentJson.optInt("course"),
            cmId = assignmentJson.optInt("cmid"),
            name = assignmentJson.optString("name"),
            description = assignmentJson.optString("intro"),
            dueDate = assignmentJson.optLong("duedate", 0).takeIf { it > 0 },
            allowSubmissionsFromDate = assignmentJson.optLong("allowsubmissionsfromdate", 0).takeIf { it > 0 },
            cutoffDate = assignmentJson.optLong("cutoffdate", 0).takeIf { it > 0 },
            maxGrade = assignmentJson.optDouble("grade", 100.0),
            submissionStatus = submissionInfo.status,
            submissionGrade = submissionInfo.grade,
            gradeDisplay = submissionInfo.display,
            submissionGraded = submissionInfo.graded,
            canSubmit = canSubmit,
            canEdit = submissionInfo.canEdit,
            lastAttemptDate = submissionInfo.lastAttemptAt,
            submissionId = submissionInfo.submissionId,
            files = parseFiles(assignmentJson),
            submissionFiles = submissionFiles,
            enabledSubmissionPlugins = enabledPlugins,
            statusUnknown = submissionInfo.statusUnknown,
        )
    }

    /**
     * Calcula si el alumno puede entregar.
     *
     * ## ¿Por qué es tan lioso?
     * Porque a veces Moodle dice que NO puedes entregar (por un fallo interno del servidor),
     * pero las fechas están abiertas. En vez de hacerle caso ciegamente, miramos las fechas
     * nosotros mismos.
     */
    private fun calculateCanSubmit(
        assignmentJson: JSONObject,
        submissionInfo: SubmissionInfo,
        submissionStatusResult: Result<JSONObject>,
    ): Boolean {
        if (submissionInfo.status == "submitted") return false // Ya está entregado.

        val lastAttemptJson = submissionStatusResult.getOrNull()?.optJSONObject("lastattempt")
        if (lastAttemptJson?.optBoolean("locked", false) == true) return false // El profesor ha cerrado la tarea.

        val canSubmitFromMoodle = lastAttemptJson?.optBoolean("cansubmit", false) ?: false
        if (canSubmitFromMoodle) return true // Si Moodle dice que sí, adelante.

        // Si Moodle dice que no, comprobamos las fechas.
        val now = System.currentTimeMillis() / 1000
        val allowFrom = assignmentJson.optLong("allowsubmissionsfromdate", 0)
        val dueDate = assignmentJson.optLong("duedate", 0)
        val cutoffDate = assignmentJson.optLong("cutoffdate", 0)

        if (allowFrom > 0 && now < allowFrom) return false // Aún no ha empezado.
        if (cutoffDate > 0 && now > cutoffDate) return false // El corte duro ya pasó.
        if (cutoffDate == 0L && dueDate > 0 && now > dueDate) return false // La fecha límite ya pasó.

        return true // Si pasó todos los filtros, le dejamos entregar.
    }

    private fun parseEnabledSubmissionPlugins(assignmentJson: JSONObject): Set<String> {
        val configs = assignmentJson.optJSONArray("configs") ?: return emptySet()
        val enabledPlugins = mutableSetOf<String>()
        for (i in 0 until configs.length()) {
            val config = configs.getJSONObject(i)
            if (config.optString("subtype") == "assignsubmission" && config.optString("name") == "enabled" && config.optString("value") == "1") {
                enabledPlugins.add(config.optString("plugin"))
            }
        }
        return enabledPlugins
    }

    private fun parseSubmissionFiles(submissionStatusResult: Result<JSONObject>): List<CourseFile> {
        val submissionFiles = mutableListOf<CourseFile>()
        val auth = currentAuth() ?: return emptyList()
        submissionStatusResult.onSuccess { statusJson ->
            val plugins = statusJson.optJSONObject("lastattempt")?.optJSONObject("submission")?.optJSONArray("plugins")
            if (plugins != null) {
                for (i in 0 until plugins.length()) {
                    val plugin = plugins.getJSONObject(i)
                    if (plugin.optString("type") == "file") {
                        val fileareas = plugin.optJSONArray("fileareas")
                        if (fileareas != null) {
                            for (j in 0 until fileareas.length()) {
                                val area = fileareas.getJSONObject(j)
                                if (area.optString("area") == "submission_files") {
                                    val files = area.optJSONArray("files")
                                    if (files != null) {
                                        for (k in 0 until files.length()) {
                                            val file = files.getJSONObject(k)
                                            submissionFiles.add(CourseFile(
                                                fileName = file.optString("filename"),
                                                fileUrl = file.optString("fileurl").appendMoodleToken(auth.first, auth.second),
                                                fileSize = file.optLong("filesize", 0),
                                                mimeType = file.optString("mimetype").takeIf { it.isNotEmpty() },
                                                timeModified = file.optLong("timemodified", 0),
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return submissionFiles
    }

    private fun parseFiles(assignmentJson: JSONObject): List<CourseFile> {
        val courseFiles = mutableListOf<CourseFile>()
        val (token, host) = currentAuth() ?: return emptyList()

        fun appendFrom(array: JSONArray?) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val file = array.optJSONObject(i) ?: continue
                val rawUrl = file.optString("fileurl", "")
                if (rawUrl.isNotEmpty()) {
                    courseFiles.add(CourseFile(
                        fileName = file.optString("filename"),
                        fileUrl = rawUrl.appendMoodleToken(token, host),
                        fileSize = file.optLong("filesize", 0),
                        mimeType = file.optString("mimetype").takeIf { it.isNotEmpty() },
                        timeModified = file.optLong("timemodified", 0),
                    ))
                }
            }
        }
        appendFrom(assignmentJson.optJSONArray("introattachments"))
        appendFrom(assignmentJson.optJSONArray("introfiles"))
        return courseFiles
    }

    private fun currentAuth(): Pair<String, String>? = try {
        val api = sessionApi.requireApi()
        api.token.value to api.config.moodleHost
    } catch (e: IllegalStateException) { null }

    private data class SubmissionInfo(
        val status: String?, val grade: Double?, val display: String?,
        val graded: Boolean, val canEdit: Boolean, val lastAttemptAt: Long?,
        val submissionId: Int?, val statusUnknown: Boolean,
    ) {
        companion object {
            fun from(result: Result<JSONObject>): SubmissionInfo = result.fold(
                onSuccess = { fromJson(it) },
                onFailure = { EMPTY.copy(statusUnknown = true) },
            )
            private fun fromJson(json: JSONObject): SubmissionInfo {
                val lastAttempt = json.optJSONObject("lastattempt")
                val submission = lastAttempt?.optJSONObject("submission")
                val feedback = json.optJSONObject("feedback")
                return SubmissionInfo(
                    status = submission?.optString("status"),
                    grade = feedback?.optJSONObject("grade")?.let { if (it.isNull("grade")) null else it.optDouble("grade", -1.0).takeIf { g -> g >= 0 } },
                    display = feedback?.optString("gradefordisplay", "")?.takeIf { it.isNotBlank() },
                    graded = lastAttempt?.optBoolean("graded", false) == true,
                    canEdit = lastAttempt?.optBoolean("canedit", true) ?: true,
                    lastAttemptAt = submission?.optLong("timemodified", 0)?.takeIf { it > 0 },
                    submissionId = submission?.optInt("id", 0)?.takeIf { it > 0 },
                    statusUnknown = false,
                )
            }
            val EMPTY = SubmissionInfo(null, null, null, false, true, null, null, false)
        }
    }
}