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
 * Implementación contra Moodle de las consultas de tareas.
 *
 * ## Qué hace
 *
 * Moodle responde en JSON. Esta clase pide la información de las tareas
 * (`mod_assign_get_assignments`) y el estado de entrega del alumno
 * (`mod_assign_get_submission_status`) y la convierte en los modelos del
 * dominio [Assignment] y [AssignmentDetail].
 *
 * ## Si falla el estado de entrega
 *
 * El estado de entrega es información secundaria: si su petición falla
 * (sin permisos, error del servidor…) el detalle de la tarea se devuelve
 * igualmente, marcado con `statusUnknown = true`, en lugar de impedir
 * ver la tarea.
 *
 * @param sessionApi Proveedor del cliente REST de la sesión activa.
 */
@Singleton
class MoodleAssignmentRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : AssignmentRepository {

    /**
     * Devuelve las tareas de varios cursos, aplanadas en una sola lista.
     *
     * @param courseIds Identificadores de los cursos.
     * @return Las tareas de todos los cursos indicados.
     */
    override suspend fun getAssignments(courseIds: List<Int>): List<Assignment> {
        val assignmentsResponse = fetchAssignmentsForCourses(courseIds)
        return assignmentsResponse.optJSONArray("courses").objects()
            .flatMap { courseJson -> courseJson.optJSONArray("assignments").objects() }
            .map { assignmentJson -> parseAssignment(assignmentJson) }
    }

    /**
     * Devuelve el detalle completo de una tarea, con su estado de entrega.
     *
     * @param courseId Identificador del curso.
     * @param assignmentId Identificador de la tarea (también se acepta el
     *   `cmid` del módulo, por si llega desde un enlace que lo use).
     * @return El detalle, o `null` si el curso no contiene esa tarea.
     */
    override suspend fun getAssignmentDetail(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail? {
        val assignmentsResponse = fetchAssignmentsForCourses(listOf(courseId))
        val assignmentJson = findAssignment(assignmentsResponse, assignmentId) ?: return null
        val submissionStatusResult = fetchSubmissionStatus(assignmentJson.optInt("id"))
        return buildAssignmentDetail(assignmentJson, submissionStatusResult)
    }

    /**
     * Pide a Moodle las tareas de uno o varios cursos.
     *
     * @param courseIds Identificadores de los cursos.
     * @return La respuesta JSON de `mod_assign_get_assignments`.
     */
    private suspend fun fetchAssignmentsForCourses(courseIds: List<Int>): JSONObject {
        val requestParams = MoodleParams().apply { putArray("courseids", courseIds) }
        return JSONObject(sessionApi.requireApi().callREST("mod_assign_get_assignments", requestParams))
    }

    /**
     * Pide el estado de entrega de una tarea.
     *
     * @param assignmentId Identificador de la tarea.
     * @return La respuesta JSON, o el fallo si la petición no tuvo éxito.
     */
    private suspend fun fetchSubmissionStatus(assignmentId: Int): Result<JSONObject> =
        runCatchingCancellable {
            val requestParams = MoodleParams().apply { put("assignid", assignmentId) }
            JSONObject(sessionApi.requireApi().callREST("mod_assign_get_submission_status", requestParams))
        }

    /**
     * Busca una tarea dentro de la respuesta de Moodle.
     *
     * Primero se busca por el identificador de la tarea (`id`) y, solo si
     * no aparece, por el del módulo (`cmid`). Así un `cmid` que casualmente
     * coincida con el `id` de otra tarea nunca desplaza a la tarea correcta.
     *
     * @param assignmentsResponse Respuesta de `mod_assign_get_assignments`.
     * @param targetId Identificador buscado.
     * @return La tarea encontrada, o `null`.
     */
    private fun findAssignment(assignmentsResponse: JSONObject, targetId: Int): JSONObject? {
        val allAssignments = assignmentsResponse.optJSONArray("courses").objects()
            .flatMap { courseJson -> courseJson.optJSONArray("assignments").objects() }
        return allAssignments.firstOrNull { assignmentJson -> assignmentJson.optInt("id") == targetId }
            ?: allAssignments.firstOrNull { assignmentJson -> assignmentJson.optInt("cmid") == targetId }
    }

    /**
     * Convierte una tarea de Moodle en el modelo de listado.
     *
     * @param assignmentJson Objeto JSON de la tarea.
     * @return La tarea como [Assignment].
     */
    private fun parseAssignment(assignmentJson: JSONObject) = Assignment(
        id = assignmentJson.optInt("id"),
        courseId = assignmentJson.optInt("course"),
        name = assignmentJson.optString("name"),
        description = assignmentJson.optString("intro"),
        dueDate = assignmentJson.positiveEpochOrNull("duedate"),
        allowSubmissionsFromDate = assignmentJson.positiveEpochOrNull("allowsubmissionsfromdate"),
        grade = assignmentJson.optDouble("grade", 0.0).takeIf { grade -> !grade.isNaN() },
        submissionStatus = null,
    )

    /**
     * Compone el detalle de una tarea a partir de su JSON y del estado
     * de entrega.
     *
     * @param assignmentJson Objeto JSON de la tarea.
     * @param submissionStatusResult Resultado de pedir el estado de entrega.
     * @return El detalle como [AssignmentDetail].
     */
    private fun buildAssignmentDetail(
        assignmentJson: JSONObject,
        submissionStatusResult: Result<JSONObject>,
    ): AssignmentDetail {
        val submissionInfo = SubmissionInfo.from(submissionStatusResult)

        return AssignmentDetail(
            id = assignmentJson.optInt("id"),
            courseId = assignmentJson.optInt("course"),
            cmId = assignmentJson.optInt("cmid"),
            name = assignmentJson.optString("name"),
            description = assignmentJson.optString("intro"),
            dueDate = assignmentJson.positiveEpochOrNull("duedate"),
            allowSubmissionsFromDate = assignmentJson.positiveEpochOrNull("allowsubmissionsfromdate"),
            cutoffDate = assignmentJson.positiveEpochOrNull("cutoffdate"),
            maxGrade = assignmentJson.optDouble("grade", 100.0),
            submissionStatus = submissionInfo.status,
            submissionGrade = submissionInfo.grade,
            gradeDisplay = submissionInfo.display,
            submissionGraded = submissionInfo.graded,
            canSubmit = calculateCanSubmit(assignmentJson, submissionInfo, submissionStatusResult),
            canEdit = submissionInfo.canEdit,
            lastAttemptDate = submissionInfo.lastAttemptAt,
            submissionId = submissionInfo.submissionId,
            files = parseIntroFiles(assignmentJson),
            submissionFiles = parseSubmissionFiles(submissionStatusResult),
            enabledSubmissionPlugins = parseEnabledSubmissionPlugins(assignmentJson),
            statusUnknown = submissionInfo.statusUnknown,
        )
    }

    /**
     * Decide si el alumno puede entregar o modificar su entrega.
     *
     * ## Por qué no se fía solo de Moodle
     *
     * A veces Moodle indica que no se puede entregar (por un fallo interno
     * del servidor o una configuración rara) aunque las fechas estén
     * abiertas. Por eso, cuando Moodle dice "no", se comprueban las fechas
     * como segunda opinión.
     *
     * ## Orden de las comprobaciones
     *
     * 1. Ya entregada: no.
     * 2. Bloqueada por el profesor: no.
     * 3. Moodle dice que sí: sí.
     * 4. Segunda opinión por fechas: no antes de la apertura; no después
     *    del cierre duro (`cutoffdate`); si no hay cierre duro, no después
     *    de la fecha límite (`duedate`); en cualquier otro caso, sí.
     *
     * @param assignmentJson Objeto JSON de la tarea (aporta las fechas).
     * @param submissionInfo Estado de entrega ya interpretado.
     * @param submissionStatusResult Respuesta bruta del estado de entrega.
     * @return `true` si se debe permitir entregar.
     */
    private fun calculateCanSubmit(
        assignmentJson: JSONObject,
        submissionInfo: SubmissionInfo,
        submissionStatusResult: Result<JSONObject>,
    ): Boolean {
        if (submissionInfo.status == STATUS_SUBMITTED) return false

        val lastAttemptJson = submissionStatusResult.getOrNull()?.optJSONObject("lastattempt")
        if (lastAttemptJson?.optBoolean("locked", false) == true) return false
        if (lastAttemptJson?.optBoolean("cansubmit", false) == true) return true

        val nowEpochSeconds = System.currentTimeMillis() / 1000
        val allowFrom = assignmentJson.optLong("allowsubmissionsfromdate", 0)
        val dueDate = assignmentJson.optLong("duedate", 0)
        val cutoffDate = assignmentJson.optLong("cutoffdate", 0)

        return when {
            allowFrom > 0 && nowEpochSeconds < allowFrom -> false
            cutoffDate > 0 && nowEpochSeconds > cutoffDate -> false
            cutoffDate == 0L && dueDate > 0 && nowEpochSeconds > dueDate -> false
            else -> true
        }
    }

    /**
     * Obtiene los plugins de entrega activados en la tarea (por ejemplo
     * `file` u `onlinetext`), que indican qué se puede entregar.
     *
     * @param assignmentJson Objeto JSON de la tarea.
     * @return Los nombres de los plugins con `enabled = 1`.
     */
    private fun parseEnabledSubmissionPlugins(assignmentJson: JSONObject): Set<String> =
        assignmentJson.optJSONArray("configs").objects()
            .filter { config ->
                config.optString("subtype") == "assignsubmission" &&
                    config.optString("name") == "enabled" &&
                    config.optString("value") == "1"
            }
            .map { config -> config.optString("plugin") }
            .toSet()

    /**
     * Obtiene los archivos que el alumno ya ha entregado.
     *
     * Están anidados en `lastattempt.submission.plugins[tipo=file]
     * .fileareas[area=submission_files].files`. A cada URL se le añade el
     * token para poder descargarla.
     *
     * @param submissionStatusResult Resultado de pedir el estado de entrega.
     * @return Los archivos entregados, o una lista vacía si no hay o falló.
     */
    private fun parseSubmissionFiles(submissionStatusResult: Result<JSONObject>): List<CourseFile> {
        val (token, host) = currentAuth() ?: return emptyList()
        val statusJson = submissionStatusResult.getOrNull() ?: return emptyList()

        return statusJson.optJSONObject("lastattempt")
            ?.optJSONObject("submission")
            ?.optJSONArray("plugins").objects()
            .filter { plugin -> plugin.optString("type") == "file" }
            .flatMap { plugin -> plugin.optJSONArray("fileareas").objects() }
            .filter { fileArea -> fileArea.optString("area") == "submission_files" }
            .flatMap { fileArea -> fileArea.optJSONArray("files").objects() }
            .map { fileJson -> fileJson.toCourseFile(fileJson.optString("fileurl").appendMoodleToken(token, host)) }
    }

    /**
     * Obtiene los archivos adjuntos a la descripción de la tarea
     * (enunciados, plantillas…), con el token añadido a cada URL.
     *
     * @param assignmentJson Objeto JSON de la tarea.
     * @return Los archivos de `introattachments` e `introfiles` que tengan URL.
     */
    private fun parseIntroFiles(assignmentJson: JSONObject): List<CourseFile> {
        val (token, host) = currentAuth() ?: return emptyList()

        return (assignmentJson.optJSONArray("introattachments").objects() +
            assignmentJson.optJSONArray("introfiles").objects())
            .filter { fileJson -> fileJson.optString("fileurl").isNotEmpty() }
            .map { fileJson -> fileJson.toCourseFile(fileJson.optString("fileurl").appendMoodleToken(token, host)) }
    }

    /**
     * Convierte un archivo de Moodle en [CourseFile].
     *
     * @receiver Objeto JSON del archivo.
     * @param fileUrl URL final (con token) del archivo.
     * @return El archivo como [CourseFile].
     */
    private fun JSONObject.toCourseFile(fileUrl: String) = CourseFile(
        fileName = optString("filename"),
        fileUrl = fileUrl,
        fileSize = optLong("filesize", 0),
        mimeType = optString("mimetype").takeIf { mimeType -> mimeType.isNotEmpty() },
        timeModified = optLong("timemodified", 0),
    )

    /**
     * Lee una fecha en segundos Unix de un campo, tratando `0` como "sin fecha".
     *
     * @receiver Objeto JSON que contiene el campo.
     * @param key Nombre del campo.
     * @return La fecha, o `null` si no existe o vale `0` o menos.
     */
    private fun JSONObject.positiveEpochOrNull(key: String): Long? =
        optLong(key, 0).takeIf { epoch -> epoch > 0 }

    /**
     * Devuelve el token y el host de la sesión activa.
     *
     * @return Par `(token, host)`, o `null` si no hay sesión.
     */
    private fun currentAuth(): Pair<String, String>? =
        try {
            val api = sessionApi.requireApi()
            api.token.value to api.config.moodleHost
        } catch (exception: IllegalStateException) {
            null
        }

    /**
     * Estado de entrega del alumno, ya interpretado.
     *
     * @property status Estado (`submitted`, `draft`, `new`…), o `null` si no hay entrega.
     * @property grade Nota numérica de la entrega, o `null` si no está calificada.
     * @property display Nota tal como la muestra Moodle (por ejemplo `7,5 / 10`).
     * @property graded `true` si el profesor ya ha calificado.
     * @property canEdit `true` si Moodle permite editar la entrega.
     * @property lastAttemptAt Fecha de la última modificación (segundos Unix), o `null`.
     * @property submissionId Identificador de la entrega, o `null` si no existe.
     * @property statusUnknown `true` si no se pudo obtener el estado de Moodle.
     */
    private data class SubmissionInfo(
        val status: String?,
        val grade: Double?,
        val display: String?,
        val graded: Boolean,
        val canEdit: Boolean,
        val lastAttemptAt: Long?,
        val submissionId: Int?,
        val statusUnknown: Boolean,
    ) {
        companion object {
            /** Estado cuando no hay datos: sin entrega y editable. */
            private val EMPTY = SubmissionInfo(null, null, null, false, true, null, null, false)

            /**
             * Interpreta el resultado de pedir el estado de entrega.
             *
             * @param result Respuesta de Moodle o el fallo de la petición.
             * @return El estado; si hubo fallo, [EMPTY] con `statusUnknown = true`.
             */
            fun from(result: Result<JSONObject>): SubmissionInfo = result.fold(
                onSuccess = { json -> fromJson(json) },
                onFailure = { EMPTY.copy(statusUnknown = true) },
            )

            /**
             * Extrae los campos relevantes del JSON de estado de entrega.
             *
             * @param json Respuesta de `mod_assign_get_submission_status`.
             * @return El estado interpretado.
             */
            private fun fromJson(json: JSONObject): SubmissionInfo {
                val lastAttempt = json.optJSONObject("lastattempt")
                val submission = lastAttempt?.optJSONObject("submission")
                val feedback = json.optJSONObject("feedback")
                val gradeJson = feedback?.optJSONObject("grade")

                return SubmissionInfo(
                    status = submission?.optString("status"),
                    grade = gradeJson
                        ?.takeIf { grade -> !grade.isNull("grade") }
                        ?.optDouble("grade", -1.0)
                        ?.takeIf { grade -> grade >= 0 },
                    display = feedback?.optString("gradefordisplay", "")?.takeIf { text -> text.isNotBlank() },
                    graded = lastAttempt?.optBoolean("graded", false) == true,
                    canEdit = lastAttempt?.optBoolean("canedit", true) ?: true,
                    lastAttemptAt = submission?.optLong("timemodified", 0)?.takeIf { epoch -> epoch > 0 },
                    submissionId = submission?.optInt("id", 0)?.takeIf { id -> id > 0 },
                    statusUnknown = false,
                )
            }
        }
    }

    private companion object {
        /** Estado de Moodle de una entrega ya enviada a calificar. */
        const val STATUS_SUBMITTED = "submitted"
    }
}
