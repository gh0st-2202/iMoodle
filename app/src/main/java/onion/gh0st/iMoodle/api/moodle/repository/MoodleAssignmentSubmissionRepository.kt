package onion.gh0st.iMoodle.api.moodle.repository

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Request
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.network.MoodleUploadClient
import onion.gh0st.iMoodle.core.DEFAULT_FILE_NAME
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.core.displayName
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentSubmission
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación contra Moodle de la entrega y el borrado de tareas.
 *
 * ## Cómo funciona la entrega ([submit])
 *
 * Moodle no recibe los archivos dentro de la propia llamada de entrega:
 * primero hay que subirlos a un *área de borrador* y luego referenciarla.
 * Los pasos son:
 *
 * 1. **Conservar los archivos ya entregados** que el alumno no ha quitado:
 *    se descargan y se vuelven a subir al área de borrador nueva (Moodle
 *    sustituye la entrega completa, no añade a la anterior).
 * 2. **Subir los archivos nuevos** elegidos en el teléfono.
 * 3. **Guardar la entrega** (`mod_assign_save_submission`) apuntando al
 *    área de borrador y, si lo hay, con el texto en línea.
 * 4. **Enviarla a calificar** (`mod_assign_submit_for_grading`), que es lo
 *    que avisa al profesor y cierra el borrador.
 *
 * ## Respuestas con avisos
 *
 * Las funciones de Moodle de los pasos 3 y 4 responden con éxito HTTP
 * incluso cuando **rechazan** la operación: lo indican con una lista de
 * avisos (`warnings`) en el cuerpo. Esta clase los interpreta
 * ([ensureNoWarnings]) para que un rechazo del servidor se muestre como
 * error y no como una falsa entrega correcta.
 *
 * ## Archivos grandes
 *
 * Los archivos existentes se descargan a un fichero temporal de la caché
 * (no a memoria) y se borran al terminar, de modo que el consumo de
 * memoria no depende del tamaño de los adjuntos.
 *
 * @param context Contexto de aplicación (resolver URIs y acceder a la caché).
 *   Se anota con `@param:` para que Kotlin 2.x aplique la anotación al
 *   parámetro del constructor (aviso KT-73255).
 * @param sessionApi Proveedor del cliente REST de la sesión activa.
 * @param moodleSessionProvider Proveedor de la sesión completa (cookies y token).
 */
@Singleton
class MoodleAssignmentSubmissionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionApi: SessionApiProvider,
    private val moodleSessionProvider: MoodleSessionProvider,
) : AssignmentSubmissionRepository {

    /**
     * Entrega una tarea: sube archivos, guarda la entrega y la envía a calificar.
     *
     * @param submission Datos de la entrega (texto, archivos nuevos y existentes).
     * @return Éxito, o el fallo ocurrido (sin sesión, sin contenido, red,
     *   rechazo de Moodle…).
     */
    override suspend fun submit(submission: AssignmentSubmission): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable { performSubmission(submission) }
        }

    /**
     * Ejecuta los pasos de la entrega. Debe llamarse en `Dispatchers.IO`
     * porque descarga y copia ficheros.
     *
     * @param submission Datos de la entrega.
     */
    private suspend fun performSubmission(submission: AssignmentSubmission) {
        val moodleSession = moodleSessionProvider.currentSession()
            ?: error("No hay sesión activa")
        /*
         * Preflight barato: fuerza a `MoodleApiClient` a comprobar el
         * token antes de subir nada. Si caducó mientras el usuario
         * redactaba la entrega, se refresca aquí y las subidas usan
         * `moodleSession.wstoken` (que es un getter del token actual).
         * Sin esto, `upload.php` no refresca tokens y la entrega
         * fallaría hasta que otra pantalla lo renovara.
         */
        moodleSession.api.callREST("core_webservice_get_site_info", MoodleParams())

        val uploadClient = MoodleUploadClient(context, moodleSession.client, moodleSession.api.config.userAgent)

        var draftItemId = 0

        for (fileUrl in submission.existingFileUrls) {
            draftItemId = reuploadExistingFile(moodleSession, uploadClient, draftItemId, fileUrl)
        }

        for (uriString in submission.newFileUris) {
            draftItemId = uploadNewFile(moodleSession, uploadClient, draftItemId, uriString)
        }

        if (draftItemId == 0 && submission.onlineText.isBlank()) {
            error("No hay contenido para entregar.")
        }

        saveSubmission(submission.assignmentId, draftItemId, submission.onlineText)
        submitForGrading(submission.assignmentId)
    }

    /**
     * Borra la entrega del alumno (equivale a "Eliminar entrega" en la web).
     *
     * @param assignmentId Identificador de la tarea.
     * @param userId Identificador del alumno.
     * @return Éxito, o el fallo ocurrido (incluido un rechazo de Moodle).
     */
    override suspend fun deleteSubmission(assignmentId: Int, userId: Int): Result<Unit> =
        runCatchingCancellable {
            val removeParams = MoodleParams().apply {
                put("userid", userId)
                put("assignid", assignmentId)
            }
            val response = sessionApi.requireApi().callREST("mod_assign_remove_submission", removeParams)
            ensureRemovalSucceeded(response)
        }

    /**
     * Descarga un archivo ya entregado y lo vuelve a subir al área de
     * borrador, para conservarlo en la nueva entrega.
     *
     * @param moodleSession Sesión activa.
     * @param uploadClient Cliente de subida.
     * @param draftItemId Área de borrador actual (0 si aún no existe).
     * @param fileUrl URL (con token) del archivo existente.
     * @return El `itemid` del área de borrador tras la subida.
     */
    private suspend fun reuploadExistingFile(
        moodleSession: MoodleSession,
        uploadClient: MoodleUploadClient,
        draftItemId: Int,
        fileUrl: String,
    ): Int {
        val downloadedFile = downloadToCache(moodleSession, fileUrl)
        try {
            return uploadClient.uploadFromFile(
                baseUrl = moodleSession.api.config.moodleBase,
                token = moodleSession.wstoken,
                itemId = draftItemId,
                file = downloadedFile,
                fileName = fileNameFromUrl(fileUrl),
            ).getOrThrow()
        } finally {
            downloadedFile.delete()
        }
    }

    /**
     * Sube al área de borrador un archivo nuevo elegido en el teléfono.
     *
     * @param moodleSession Sesión activa.
     * @param uploadClient Cliente de subida.
     * @param draftItemId Área de borrador actual (0 si aún no existe).
     * @param uriString URI del archivo, como texto.
     * @return El `itemid` del área de borrador tras la subida.
     */
    private suspend fun uploadNewFile(
        moodleSession: MoodleSession,
        uploadClient: MoodleUploadClient,
        draftItemId: Int,
        uriString: String,
    ): Int {
        val fileUri = uriString.toUri()
        return uploadClient.uploadFile(
            baseUrl = moodleSession.api.config.moodleBase,
            token = moodleSession.wstoken,
            itemId = draftItemId,
            fileUri = fileUri,
            fileName = fileUri.displayName(context.contentResolver),
        ).getOrThrow()
    }

    /**
     * Descarga una URL de Moodle a un fichero temporal de la caché.
     *
     * @param moodleSession Sesión activa (aporta el cliente con cookies).
     * @param url URL a descargar.
     * @return El fichero temporal (el llamador debe borrarlo).
     * @throws IllegalStateException si el servidor responde con error o sin cuerpo.
     */
    private suspend fun downloadToCache(moodleSession: MoodleSession, url: String): File {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", moodleSession.api.config.userAgent)
            .build()
        val temporaryFile = File.createTempFile("submission_", ".tmp", context.cacheDir)

        try {
            moodleSession.client.newCall(request).await().use { response ->
                check(response.isSuccessful) { "No se pudo descargar un archivo ya entregado (HTTP ${response.code})" }
                val responseBody = checkNotNull(response.body) { "El archivo ya entregado está vacío" }
                responseBody.byteStream().use { input ->
                    temporaryFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (throwable: Throwable) {
            temporaryFile.delete()
            throw throwable
        }
        return temporaryFile
    }

    /**
     * Deduce el nombre de un archivo a partir de su URL, ignorando la
     * *query string* (donde va el token).
     *
     * @param url URL del archivo.
     * @return El último tramo de la ruta, o el nombre por defecto si queda vacío.
     */
    private fun fileNameFromUrl(url: String): String =
        url.substringBefore("?").substringAfterLast("/").ifBlank { DEFAULT_FILE_NAME }

    /**
     * Guarda la entrega en Moodle (`mod_assign_save_submission`).
     *
     * El área de borrador se envía como `files_filemanager`. Si hay texto
     * en línea, se envía en formato HTML (`format = 1`).
     *
     * @param assignmentId Identificador de la tarea.
     * @param draftItemId Área de borrador con los archivos (0 si no hay).
     * @param onlineText Texto en línea (puede estar vacío).
     * @throws IllegalStateException si Moodle rechaza el guardado.
     */
    private suspend fun saveSubmission(assignmentId: Int, draftItemId: Int, onlineText: String) {
        val params = mutableMapOf("assignmentid" to assignmentId.toString())
        if (draftItemId > 0) {
            params["plugindata[files_filemanager]"] = draftItemId.toString()
        }
        if (onlineText.isNotBlank()) {
            params["plugindata[onlinetext_editor][text]"] = onlineText
            params["plugindata[onlinetext_editor][format]"] = ONLINE_TEXT_FORMAT_HTML
            params["plugindata[onlinetext_editor][itemid]"] = draftItemId.toString()
        }
        val response = sessionApi.requireApi().callRESTWithFormBody("mod_assign_save_submission", params)
        ensureNoWarnings(response, operation = "guardar la entrega")
    }

    /**
     * Envía la entrega a calificar (`mod_assign_submit_for_grading`),
     * aceptando la declaración de autoría que exigen algunos cursos.
     *
     * @param assignmentId Identificador de la tarea.
     * @throws IllegalStateException si Moodle rechaza el envío.
     */
    private suspend fun submitForGrading(assignmentId: Int) {
        val response = sessionApi.requireApi().callRESTWithFormBody(
            "mod_assign_submit_for_grading",
            mapOf(
                "assignmentid" to assignmentId.toString(),
                "acceptsubmissionstatement" to "1",
            ),
        )
        ensureNoWarnings(response, operation = "enviar la entrega")
    }

    /**
     * Comprueba que una respuesta de Moodle no contiene avisos de rechazo.
     *
     * Estas funciones devuelven `[]` si todo fue bien o una lista de
     * objetos con `message` si el servidor rechazó la operación (por
     * ejemplo, "las entregas están cerradas").
     *
     * @param response Cuerpo JSON de la respuesta.
     * @param operation Descripción de la operación, para el mensaje de error.
     * @throws IllegalStateException con el mensaje de Moodle si hay avisos.
     */
    private fun ensureNoWarnings(response: String, operation: String) {
        val trimmedResponse = response.trim()
        if (!trimmedResponse.startsWith("[")) return

        val warnings = JSONArray(trimmedResponse)
        if (warnings.length() > 0) {
            error(warningMessage(warnings, fallback = "Moodle rechazó $operation"))
        }
    }

    /**
     * Comprueba el resultado de `mod_assign_remove_submission`, que
     * responde con `{"status": true|false, "warnings": [...]}`.
     *
     * @param response Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si `status` es `false`.
     */
    private fun ensureRemovalSucceeded(response: String) {
        val trimmedResponse = response.trim()
        if (!trimmedResponse.startsWith("{")) return

        val result = JSONObject(trimmedResponse)
        if (!result.optBoolean("status", true)) {
            error(warningMessage(result.optJSONArray("warnings"), fallback = "Moodle rechazó eliminar la entrega"))
        }
    }

    /**
     * Extrae el primer mensaje de una lista de avisos de Moodle.
     *
     * @param warnings Lista de avisos (puede ser `null`).
     * @param fallback Mensaje a usar si no hay ninguno legible.
     * @return El mensaje del primer aviso o [fallback].
     */
    private fun warningMessage(warnings: JSONArray?, fallback: String): String =
        warnings.objects().firstOrNull()?.optString("message")?.takeIf { message -> message.isNotBlank() }
            ?: fallback

    private companion object {
        /** Código de formato HTML del editor de texto de Moodle. */
        const val ONLINE_TEXT_FORMAT_HTML = "1"
    }
}
