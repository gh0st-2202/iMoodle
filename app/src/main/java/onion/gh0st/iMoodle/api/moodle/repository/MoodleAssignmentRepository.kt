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
 * Implementación real de [AssignmentRepository] sobre la API REST de
 * Moodle.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de hablar con Moodle para obtener la información de
 * las **tareas** (el módulo `mod_assign` de Moodle). Traduce la
 * información cruda que devuelve la API a los modelos de dominio
 * `Assignment` (versión ligera, para listados) y `AssignmentDetail`
 * (versión completa, para la pantalla de detalle).
 *
 * ## Qué es una "tarea" en Moodle
 *
 * En Moodle, una tarea es una actividad que el profesor propone al
 * alumno (por ejemplo, "entrega este trabajo antes del viernes"). Cada
 * tarea tiene:
 *
 * - **ID** y **nombre**.
 * - **Descripción** (a veces con formato HTML).
 * - **Fecha límite de entrega** (opcional).
 * - **Fecha desde la que se permiten entregas** (opcional).
 * - **Nota máxima** posible.
 * - **Estado de la entrega** del alumno: sin entregar, borrador,
 *   entregado, calificado…
 * - **Nota obtenida** (si está calificada).
 * - **Archivos adjuntos** (plantillas, PDFs…).
 *
 * Algunos de esos datos vienen en una llamada (`mod_assign_get_assignments`)
 * y otros en otra (`mod_assign_get_submission_status`). Esta clase
 * combina ambas.
 *
 * ## Qué NO hace
 *
 * - **No guarda nada en Room**. Las tareas se consultan siempre
 *   frescas a la red. Cachearlas añadiría complejidad (sincronización,
 *   invalidación, conflictos entre dispositivos) sin mucho beneficio:
 *   el estado de una tarea cambia cuando el alumno entrega, no cada
 *   minuto.
 * - **No entrega tareas**. La app solo consulta; no implementa la
 *   entrega.
 * - **No gestiona permisos de Moodle**. Si Moodle devuelve un error
 *   de permisos (`accessexception`), se propaga como
 *   `MoodleApiException` y el llamador decide qué hacer.
 *
 * ## Sobre el estado de la entrega y su "desconocimiento"
 *
 * La llamada `mod_assign_get_submission_status` es la que dice si el
 * alumno ha entregado o no. Esa llamada puede fallar por múltiples
 * motivos: red caída, timeout, permisos, respuesta inesperada. Cuando
 * falla, esta clase **no asume** que el alumno no ha entregado.
 * Marca el resultado como "estado desconocido" con
 * `AssignmentDetail.statusUnknown = true`, y la pantalla de detalle
 * muestra "Estado desconocido" en lugar de "Sin entregar". Así el
 * usuario no ve una información engañosa.
 *
 * @param sessionApi Fuente del cliente REST autenticado. Permite
 *   obtener el `MoodleApiClient` de la sesión activa sin acoplarse a
 *   `DefaultSessionRepository`.
 */
@Singleton
class MoodleAssignmentRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : AssignmentRepository {

    /**
     * Lista de tareas de uno o varios cursos.
     *
     * ## Qué hace
     *
     * Hace una sola llamada a `mod_assign_get_assignments` con los
     * IDs de los cursos indicados. Moodle devuelve un JSON con todos
     * los cursos y, dentro de cada uno, sus tareas. Esta función
     * "aplana" ese JSON en una lista plana de `Assignment`.
     *
     * ## Cuándo se usa
     *
     * Cuando la pantalla de listado necesita mostrar cuántas tareas
     * tiene cada curso, o cuando la agenda quiere saber qué tareas
     * hay pendientes.
     *
     * ## Qué NO incluye
     *
     * El estado de la entrega de cada tarea (entregado, sin
     * entregar, etc.) no viene en esta llamada. Esa información solo
     * se pide para el detalle de una tarea concreta, porque es mucho
     * más costosa.
     *
     * @param courseIds IDs de los cursos a consultar.
     * @return Lista plana de tareas, sin estado de entrega.
     */
    override suspend fun getAssignments(courseIds: List<Int>): List<Assignment> {
        val requestParams = MoodleParams().apply { putArray("courseids", courseIds) }
        val assignmentsResponseJson = JSONObject(callRest("mod_assign_get_assignments", requestParams))
        val coursesArray = assignmentsResponseJson.optJSONArray("courses") ?: return emptyList()
        return flattenAssignments(coursesArray)
    }

    /**
     * Detalle completo de una tarea: nombre, descripción, fechas,
     * estado de entrega, nota y adjuntos.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pide a Moodle las tareas del curso (`mod_assign_get_assignments`).
     * 2. Busca la tarea concreta por ID (o por `cmid`, que es el
     *    identificador del módulo dentro del curso; Moodle a veces los
     *    confunde y conviene aceptar ambos).
     * 3. Pide el estado de entrega de esa tarea concreta
     *    (`mod_assign_get_submission_status`). Esta segunda llamada
     *    puede fallar; si falla, no se propaga el error: el detalle se
     *    construye igualmente con `statusUnknown = true`.
     * 4. Construye el `AssignmentDetail` combinando la información de
     *    la tarea con la del estado de entrega.
     *
     * ## Cuándo devuelve `null`
     *
     * Si no encuentra la tarea en el curso (porque el ID no existe, o
     * porque ha sido borrada, o porque el alumno ya no está
     * matriculado). No es un error: es una situación válida.
     *
     * @param courseId ID del curso.
     * @param assignmentId ID (o `cmid`) de la tarea.
     * @return El detalle, o `null` si la tarea no existe en ese curso.
     */
    override suspend fun getAssignmentDetail(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail? {
        val assignmentsResponseJson = fetchAssignmentsForCourse(courseId)
        val assignmentJson = findAssignment(assignmentsResponseJson, assignmentId) ?: return null
        val submissionStatusResult = fetchSubmissionStatus(assignmentJson.optInt("id"))
        return buildAssignmentDetail(assignmentJson, submissionStatusResult)
    }

    // --- Obtención de datos de red ---

    /**
     * Descarga las tareas de un curso concreto desde Moodle.
     *
     * @param courseId ID del curso.
     * @return `JSONObject` con el array `courses[].assignments[]`.
     */
    private suspend fun fetchAssignmentsForCourse(courseId: Int): JSONObject {
        val requestParams = MoodleParams().apply {
            putArray("courseids", listOf(courseId))
        }
        return JSONObject(callRest("mod_assign_get_assignments", requestParams))
    }

    /**
     * Descarga el estado de entrega de una tarea concreta.
     *
     * ## Cómo se gestiona el error
     *
     * Se usa `runCatchingCancellable`, que captura cualquier excepción
     * (red, parseo, permisos) pero **respeta la cancelación de
     * corrutinas**. El resultado se devuelve como `Result<JSONObject>`:
     *
     * - **Éxito**: contiene el JSON del estado.
     * - **Fallo**: contiene la excepción.
     *
     * El llamador ([buildAssignmentDetail]) decide qué hacer con el
     * fallo. En este caso, marcar el detalle como "estado
     * desconocido".
     *
     * @param assignmentId ID interno de la tarea.
     * @return El resultado de la descarga.
     */
    private suspend fun fetchSubmissionStatus(assignmentId: Int): Result<JSONObject> =
        runCatchingCancellable {
            val requestParams = MoodleParams().apply { put("assignid", assignmentId) }
            JSONObject(callRest("mod_assign_get_submission_status", requestParams))
        }

    /**
     * Ejecuta una función REST contra el cliente autenticado.
     *
     * @param function Nombre de la función de Moodle.
     * @param params Parámetros ya montados.
     * @return Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si no hay sesión activa (lanzado
     *   por `SessionApiProvider.requireApi`).
     */
    private suspend fun callRest(function: String, params: MoodleParams): String =
        sessionApi.requireApi().callREST(function, params)

    // --- Búsqueda dentro del JSON ---

    /**
     * Busca una tarea por su ID (o `cmid`) dentro del JSON de cursos
     * devuelto por `mod_assign_get_assignments`.
     *
     * @param assignmentsResponseJson Respuesta de Moodle.
     * @param targetAssignmentId ID interno o `cmid` de la tarea
     *   buscada.
     * @return El `JSONObject` de la tarea, o `null` si no está.
     */
    private fun findAssignment(
        assignmentsResponseJson: JSONObject,
        targetAssignmentId: Int,
    ): JSONObject? {
        val coursesArray = assignmentsResponseJson.optJSONArray("courses") ?: return null
        for (courseIndex in 0 until coursesArray.length()) {
            val courseJson = coursesArray.getJSONObject(courseIndex)
            val assignmentsArray = courseJson.optJSONArray("assignments") ?: continue
            findAssignmentInArray(assignmentsArray, targetAssignmentId)?.let { found ->
                return found
            }
        }
        return null
    }

    /**
     * Busca una tarea por ID (o `cmid`) en un array de tareas.
     *
     * ## Por qué se acepta tanto `id` como `cmid`
     *
     * Moodle usa dos identificadores para una tarea:
     *
     * - `id`: el ID interno de la tarea en el plugin `mod_assign`.
     * - `cmid`: el ID del módulo dentro del curso (Course Module ID).
     *
     * Distintas pantallas y deep-links pueden tener uno u otro, así
     * que aceptamos ambos. Es más robusto.
     *
     * @param assignmentsArray Array de tareas de un curso.
     * @param targetAssignmentId ID buscado.
     * @return La tarea, o `null` si no está.
     */
    private fun findAssignmentInArray(
        assignmentsArray: JSONArray,
        targetAssignmentId: Int,
    ): JSONObject? =
        (0 until assignmentsArray.length())
            .map { assignmentIndex -> assignmentsArray.getJSONObject(assignmentIndex) }
            .firstOrNull { assignmentJson ->
                assignmentJson.optInt("id") == targetAssignmentId ||
                        assignmentJson.optInt("cmid") == targetAssignmentId
            }

    // --- Mapeo de JSON a dominio ---

    /**
     * Aplana el JSON de cursos+tareas en una lista plana de
     * `Assignment`.
     *
     * ## Qué es "aplanar"
     *
     * Moodle devuelve una estructura anidada: una lista de cursos, y
     * dentro de cada curso una lista de tareas. Para la UI suele ser
     * más cómodo tener una sola lista plana de tareas. Eso es lo que
     * hace esta función.
     *
     * @param coursesArray Array `courses[]` de la respuesta de Moodle.
     * @return Lista plana de tareas.
     */
    private fun flattenAssignments(coursesArray: JSONArray): List<Assignment> {
        val assignments = mutableListOf<Assignment>()
        for (courseIndex in 0 until coursesArray.length()) {
            val courseJson = coursesArray.getJSONObject(courseIndex)
            val assignmentsArray = courseJson.optJSONArray("assignments") ?: continue
            (0 until assignmentsArray.length()).forEach { assignmentIndex ->
                val assignmentJson = assignmentsArray.getJSONObject(assignmentIndex)
                assignments.add(parseAssignment(assignmentJson))
            }
        }
        return assignments
    }

    /**
     * Convierte un `JSONObject` de tarea en un `Assignment` de
     * dominio (sin estado de entrega).
     *
     * @param assignmentJson `JSONObject` de una tarea.
     * @return Tarea de dominio.
     */
    private fun parseAssignment(assignmentJson: JSONObject) = Assignment(
        id = assignmentJson.optInt("id"),
        courseId = assignmentJson.optInt("course"),
        name = assignmentJson.optString("name"),
        description = assignmentJson.optString("intro"),
        dueDate = assignmentJson.optLong("duedate", 0).takeIf { it > 0 },
        allowSubmissionsFromDate = assignmentJson
            .optLong("allowsubmissionsfromdate", 0)
            .takeIf { it > 0 },
        grade = assignmentJson.optDouble("grade", 0.0).takeIf { it.isNaN().not() },
        submissionStatus = null,
    )

    /**
     * Construye el `AssignmentDetail` a partir del JSON de la tarea y
     * del resultado del estado de entrega.
     *
     * ## Qué hace con el `Result`
     *
     * - Si el `Result` es éxito, extrae la información de entrega con
     *   `SubmissionInfo.from`.
     * - Si el `Result` es fallo, `SubmissionInfo.from` devuelve un
     *   objeto vacío con `statusUnknown = true`. Eso hace que la UI
     *   muestre "Estado desconocido" en lugar de "Sin entregar".
     *
     * El resto de campos (nombre, descripción, fechas, adjuntos) son
     * independientes del estado de entrega y se rellenan siempre.
     *
     * @param assignmentJson `JSONObject` de la tarea.
     * @param submissionStatusResult Resultado del estado de entrega.
     * @return Detalle completo de la tarea.
     */
    private fun buildAssignmentDetail(
        assignmentJson: JSONObject,
        submissionStatusResult: Result<JSONObject>,
    ): AssignmentDetail {
        val submissionInfo = SubmissionInfo.from(submissionStatusResult)
        return AssignmentDetail(
            id = assignmentJson.optInt("id"),
            name = assignmentJson.optString("name"),
            description = assignmentJson.optString("intro"),
            dueDate = assignmentJson.optLong("duedate", 0).takeIf { it > 0 },
            allowSubmissionsFromDate = assignmentJson
                .optLong("allowsubmissionsfromdate", 0)
                .takeIf { it > 0 },
            cutoffDate = assignmentJson.optLong("cutoffdate", 0).takeIf { it > 0 },
            maxGrade = assignmentJson.optDouble("grade", 100.0),
            submissionStatus = submissionInfo.status,
            submissionGrade = submissionInfo.grade,
            gradeDisplay = submissionInfo.display,
            submissionGraded = submissionInfo.graded,
            canSubmit = submissionInfo.canSubmit,
            lastAttemptDate = submissionInfo.lastAttemptAt,
            files = parseFiles(assignmentJson),
            statusUnknown = submissionInfo.statusUnknown,
        )
    }

    /**
     * Extrae los archivos adjuntos del enunciado de una tarea.
     *
     * ## Qué campos de Moodle se combinan
     *
     * Moodle expone los adjuntos en dos arrays distintos:
     *
     * - **`introattachments`**: adjuntos "normales" que el profesor
     *   sube con el enunciado.
     * - **`introfiles`**: archivos que algunos plugins generan
     *   automáticamente (por ejemplo, imágenes insertadas en el HTML).
     *
     * Para no perder ninguno, se combinan los dos.
     *
     * @param assignmentJson `JSONObject` de la tarea.
     * @return Lista de archivos descargables.
     */
    private fun parseFiles(assignmentJson: JSONObject): List<CourseFile> {
        val courseFiles = mutableListOf<CourseFile>()
        appendFilesFrom(assignmentJson.optJSONArray("introattachments"), courseFiles)
        appendFilesFrom(assignmentJson.optJSONArray("introfiles"), courseFiles)
        return courseFiles
    }

    /**
     * Añade los archivos de un array JSON a la lista de salida.
     *
     * ## Qué pasa si no hay sesión
     *
     * Si no hay sesión activa (`currentAuth` devuelve `null`), no se
     * añade nada. Los archivos se quedan sin token, y el
     * `DownloadManager` fallará con 401/403 al intentar descargarlos.
     * Es preferible a lanzar una excepción: el listado de tareas no
     * debería romperse solo porque no se pueden resolver los
     * adjuntos.
     *
     * @param filesArray Array de archivos, o `null`.
     * @param outputFiles Lista donde acumular los archivos.
     */
    private fun appendFilesFrom(
        filesArray: JSONArray?,
        outputFiles: MutableList<CourseFile>,
    ) {
        if (filesArray == null) return
        val (token, host) = currentAuth() ?: return
        (0 until filesArray.length())
            .mapNotNull { fileIndex ->
                toCourseFile(filesArray.optJSONObject(fileIndex), token, host)
            }
            .forEach(outputFiles::add)
    }

    /**
     * Convierte un `JSONObject` de archivo en `CourseFile`.
     *
     * ## Por qué se le añade el token a la URL
     *
     * Moodle devuelve la URL del archivo sin token, y sin token el
     * `DownloadManager` no puede descargarlo. Aquí se lo añadimos con
     * `appendMoodleToken`, que además se asegura de no añadirlo si la
     * URL es de otro host (por seguridad).
     *
     * @param fileJson `JSONObject` de archivo, o `null`.
     * @param token Token de la sesión activa.
     * @param host Host de Moodle (el token solo se añade a este host).
     * @return Archivo de dominio, o `null` si no tiene URL.
     */
    private fun toCourseFile(fileJson: JSONObject?, token: String, host: String): CourseFile? {
        if (fileJson == null) return null
        val rawFileUrl = fileJson.optString("fileurl", "")
        if (rawFileUrl.isEmpty()) return null
        return CourseFile(
            fileName = fileJson.optString("filename"),
            fileUrl = rawFileUrl.appendMoodleToken(token, host),
            fileSize = fileJson.optLong("filesize", 0),
            mimeType = fileJson.optString("mimetype").takeIf { it.isNotEmpty() },
            timeModified = fileJson.optLong("timemodified", 0),
        )
    }

    /**
     * Devuelve el token y el host de la sesión activa.
     *
     * @return Par `(token, host)`, o `null` si no hay sesión activa.
     */
    private fun currentAuth(): Pair<String, String>? {
        val moodleApiClient = try {
            sessionApi.requireApi()
        } catch (exception: IllegalStateException) {
            return null
        }
        return moodleApiClient.token.value to moodleApiClient.config.moodleHost
    }

    /**
     * Estado de entrega normalizado a partir del JSON de Moodle.
     *
     * ## Qué representa
     *
     * Agrupa toda la información que Moodle da sobre la entrega de
     * una tarea concreta por parte del alumno:
     *
     * - **status**: `"submitted"` (entregado), `"draft"` (borrador
     *   sin entregar), `"new"` (nunca entregado), o `null` si no está
     *   disponible.
     * - **grade**: nota numérica, o `null`.
     * - **display**: nota formateada por Moodle (por ejemplo, `"7,5"`
     *   o `"Apto"`), o `null`.
     * - **graded**: si está calificada.
     * - **canSubmit**: si el alumno puede entregar ahora mismo.
     * - **lastAttemptAt**: fecha del último intento en segundos, o
     *   `null`.
     * - **statusUnknown**: si **no se pudo consultar** el estado. En
     *   ese caso, el resto de campos no son fiables.
     *
     * @property status Estado de la entrega.
     * @property grade Nota numérica.
     * @property display Nota formateada.
     * @property graded `true` si está calificada.
     * @property canSubmit `true` si se puede entregar ahora.
     * @property lastAttemptAt Último intento en segundos, o `null`.
     * @property statusUnknown `true` si no se pudo consultar el
     *   estado.
     */
    private data class SubmissionInfo(
        val status: String?,
        val grade: Double?,
        val display: String?,
        val graded: Boolean,
        val canSubmit: Boolean,
        val lastAttemptAt: Long?,
        val statusUnknown: Boolean,
    ) {
        companion object {

            /**
             * Construye la info de entrega a partir del `Result` de
             * Moodle.
             *
             * ## Por qué se usa `fold`
             *
             * `fold` obliga a tratar los dos casos (éxito y fallo) de
             * forma explícita. Así es imposible olvidarse de uno.
             *
             * - **Éxito**: se parsea el JSON con `fromJson`.
             * - **Fallo**: se devuelve `EMPTY` con
             *   `statusUnknown = true`.
             *
             * @param submissionStatusResult Resultado del estado de
             *   entrega.
             * @return La info normalizada, siempre no-nula.
             */
            fun from(submissionStatusResult: Result<JSONObject>): SubmissionInfo =
                submissionStatusResult.fold(
                    onSuccess = { submissionStatusJson -> fromJson(submissionStatusJson) },
                    onFailure = { EMPTY.copy(statusUnknown = true) },
                )

            /**
             * Construye la info de entrega a partir del JSON de
             * `mod_assign_get_submission_status`.
             *
             * @param submissionStatusJson JSON del estado de entrega.
             * @return La info normalizada con `statusUnknown = false`.
             */
            private fun fromJson(submissionStatusJson: JSONObject): SubmissionInfo {
                val lastAttemptJson = submissionStatusJson.optJSONObject("lastattempt")
                val submissionJson = lastAttemptJson?.optJSONObject("submission")
                val feedbackJson = submissionStatusJson.optJSONObject("feedback")
                return SubmissionInfo(
                    status = submissionJson?.optString("status"),
                    grade = gradeFrom(feedbackJson),
                    display = displayFrom(feedbackJson),
                    graded = gradedFrom(lastAttemptJson, feedbackJson),
                    canSubmit = lastAttemptJson?.optBoolean("cansubmit", true) ?: true,
                    lastAttemptAt = submissionJson
                        ?.optLong("timemodified", 0)
                        ?.takeIf { it > 0 },
                    statusUnknown = false,
                )
            }

            /**
             * Valor por defecto cuando no hay JSON o el `Result` es
             * `failure`.
             *
             * `canSubmit = true` para que la UI no oculte el botón
             * "Entregar" solo porque no se pudo consultar el estado.
             * `statusUnknown = false` como valor inicial: solo se pone
             * a `true` cuando el fallo está confirmado.
             */
            val EMPTY = SubmissionInfo(
                status = null,
                grade = null,
                display = null,
                graded = false,
                canSubmit = true,
                lastAttemptAt = null,
                statusUnknown = false,
            )

            /**
             * Extrae la nota numérica del feedback.
             *
             * @param feedbackJson JSON de `feedback`, o `null`.
             * @return Nota (`>= 0`), o `null` si no está informada.
             */
            private fun gradeFrom(feedbackJson: JSONObject?): Double? {
                val gradeJson = feedbackJson?.optJSONObject("grade") ?: return null
                if (gradeJson.isNull("grade")) return null
                return gradeJson.optDouble("grade", -1.0).takeIf { it >= 0 }
            }

            /**
             * Extrae la nota formateada del feedback.
             *
             * @param feedbackJson JSON de `feedback`, o `null`.
             * @return Texto de la nota, o `null` si está vacío.
             */
            private fun displayFrom(feedbackJson: JSONObject?): String? {
                val gradeDisplay = feedbackJson?.optString("gradefordisplay", "").orEmpty()
                return gradeDisplay.takeIf { it.isNotBlank() }
            }

            /**
             * Decide si la tarea está calificada.
             *
             * ## Cómo lo decide
             *
             * Hay tres fuentes de verdad posibles:
             *
             * 1. El flag `graded` del último intento.
             * 2. La presencia de una nota numérica.
             * 3. La presencia de una nota formateada (`gradeDisplay`).
             *
             * Con que **una** de las tres diga "sí", se considera
             * calificada. Esto evita falsos negativos cuando Moodle
             * no rellena todas las fuentes de la misma forma.
             *
             * @param lastAttemptJson JSON de `lastattempt`, o `null`.
             * @param feedbackJson JSON de `feedback`, o `null`.
             * @return `true` si está calificada.
             */
            private fun gradedFrom(
                lastAttemptJson: JSONObject?,
                feedbackJson: JSONObject?,
            ): Boolean =
                lastAttemptJson?.optBoolean("graded", false) == true ||
                        gradeFrom(feedbackJson) != null ||
                        displayFrom(feedbackJson) != null
        }
    }
}