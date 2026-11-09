package onion.gh0st.iMoodle.api.moodle.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.network.MoodleUploadClient
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentSubmission
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El "cocinero" que ejecuta las órdenes de entrega y borrado.
 *
 * ## ¿Cómo funciona el envío (submit)?
 * 1. Descarga los archivos que ya estaban subidos (si los hay).
 * 2. Vuelve a subir esos archivos viejos a una "sala de espera" nueva (junto con los nuevos).
 * 3. Le dice a Moodle: "Guarda esta sala de espera como mi entrega oficial".
 * 4. Le dice a Moodle: "Notifica al profesor de que ya he entregado".
 *
 * ## Sobre el parámetro `context`
 * Se usa `@param:ApplicationContext` para evitar el warning de Kotlin 2.x
 * (`KT-73255`), que avisa de que las anotaciones en el constructor pronto
 * se aplicarán a los campos en lugar de a los parámetros. Al poner `@param:`,
 * le decimos explícitamente que la anotación es para el parámetro.
 */
@Singleton
class MoodleAssignmentSubmissionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionApi: SessionApiProvider,
    private val moodleSessionProvider: MoodleSessionProvider,
) : AssignmentSubmissionRepository {

    override suspend fun submit(submission: AssignmentSubmission): Result<Unit> =
        runCatchingCancellable {
            val moodleSession = moodleSessionProvider.currentSession()
                ?: error("No hay sesión activa")
            val uploadClient = MoodleUploadClient(context, moodleSession.client)
            var currentItemId = 0 // Empezamos con una sala de espera vacía (0).

            // 1. Descargamos y volvemos a subir los archivos viejos que el alumno quiere conservar.
            for (fileUrl in submission.existingFileUrls) {
                val bytes = downloadFile(moodleSession, fileUrl)
                val fileName = extractFileName(fileUrl)
                currentItemId = uploadClient.uploadFileFromBytes(
                    baseUrl = moodleSession.api.config.moodleBase,
                    token = moodleSession.wstoken,
                    itemId = currentItemId,
                    bytes = bytes,
                    fileName = fileName,
                ).getOrThrow()
            }

            // 2. Subimos los archivos nuevos de tu teléfono.
            for (uriString in submission.newFileUris) {
                val fileUri = Uri.parse(uriString)
                val fileName = resolveFileName(fileUri, fallback = "archivo")
                currentItemId = uploadClient.uploadFile(
                    baseUrl = moodleSession.api.config.moodleBase,
                    token = moodleSession.wstoken,
                    itemId = currentItemId,
                    fileUri = fileUri,
                    fileName = fileName,
                ).getOrThrow()
            }

            // 3. Si no hay ni archivos ni texto, no podemos entregar "nada".
            if (currentItemId == 0 && submission.onlineText.isBlank()) {
                error("No hay contenido para entregar.")
            }

            // 4. Guardamos la entrega y avisamos al profesor.
            saveSubmission(submission.assignmentId, currentItemId, submission.onlineText)
            submitForGrading(submission.assignmentId)
        }

    /**
     * Borra la entrega. Es como decirle a Moodle: "Olvida que alguna vez hice esto".
     */
    override suspend fun deleteSubmission(assignmentId: Int, userId: Int): Result<Unit> =
        runCatchingCancellable {
            sessionApi.requireApi().callREST("mod_assign_remove_submission",
                onion.gh0st.iMoodle.api.moodle.network.MoodleParams().apply {
                    put("userid", userId); put("assignid", assignmentId)
                }
            )
            Unit
        }

    private suspend fun downloadFile(session: MoodleSession, url: String): ByteArray {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", session.api.config.userAgent).build()
        return session.client.newCall(req).await().use { if (!it.isSuccessful) error("Error descarga") else it.body?.bytes() ?: error("Vacío") }
    }

    private fun extractFileName(url: String): String = url.substringBefore("?").substringAfterLast("/").ifBlank { "archivo" }

    private suspend fun saveSubmission(assignmentId: Int, itemId: Int, onlineText: String) {
        val params = mutableMapOf("assignmentid" to assignmentId.toString())
        if (itemId > 0) params["plugindata[files_filemanager]"] = itemId.toString()
        if (onlineText.isNotBlank()) {
            params["plugindata[onlinetext_editor][text]"] = onlineText
            params["plugindata[onlinetext_editor][format]"] = "1"
            params["plugindata[onlinetext_editor][itemid]"] = itemId.toString()
        }
        sessionApi.requireApi().callRESTWithFormBody("mod_assign_save_submission", params)
    }

    private suspend fun submitForGrading(assignmentId: Int) {
        sessionApi.requireApi().callRESTWithFormBody("mod_assign_submit_for_grading",
            mapOf("assignmentid" to assignmentId.toString(), "acceptsubmissionstatement" to "1")
        )
    }

    private fun resolveFileName(fileUri: Uri, fallback: String): String {
        if (fileUri.scheme == "file") return File(fileUri.path!!).name
        return context.contentResolver.query(fileUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else fallback
            } else fallback
        } ?: fallback
    }
}