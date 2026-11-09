package onion.gh0st.iMoodle.api.moodle.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import onion.gh0st.iMoodle.core.await
import org.json.JSONArray
import java.io.File
import java.io.IOException

/**
 * El "repartidor" que sube tus archivos a la nube de Moodle.
 *
 * ## ¿Qué es el "draft area"?
 * Moodle no guarda los archivos directamente en la tarea. Primero los guarda en una especie de
 * "sala de espera" llamada *draft area*. Una vez que los archivos están en esa sala de espera,
 * Moodle te da un número de ticket (`itemid`). Cuando finalmente pulsas "Entregar", le pasas
 * ese número de ticket a la tarea para que recoja los archivos de la sala de espera.
 *
 * ## ¿Por qué esta clase es especial?
 * Porque los archivos pesan mucho. No podemos enviarlos como si fueran texto normal. Hay que
 * partirlos en trozos (multipart) y enviarlos como un paquete especial.
 */
class MoodleUploadClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    /**
     * Sube un archivo desde tu teléfono al área de espera de Moodle.
     *
     * @param baseUrl La dirección web del colegio (ej: educacionadistancia.juntadeandalucia.es).
     * @param token Tu "llave" de acceso para que Moodle sepa que eres tú.
     * @param itemId El número de ticket de la sala de espera. Si es 0, Moodle crea una nueva sala.
     * @param fileUri La dirección exacta del archivo dentro de tu teléfono.
     * @param fileName El nombre que quieres que tenga el archivo en Moodle.
     * @return Un resultado que contiene el nuevo número de ticket (`itemid`).
     */
    suspend fun uploadFile(
        baseUrl: String,
        token: String,
        itemId: Int,
        fileUri: Uri,
        fileName: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Abrimos el archivo de tu teléfono como si fuera un chorro de agua (InputStream).
            val inputStream = if (fileUri.scheme == "file") {
                File(fileUri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(fileUri)
            } ?: return@withContext Result.failure(IOException("No se pudo abrir el archivo"))

            // Convertimos todo el archivo en una bolsa de bytes (números).
            val fileBytes = inputStream.use { stream -> stream.readBytes() }
            // Averiguamos qué tipo de archivo es (imagen, PDF, etc).
            val mimeType = if (fileUri.scheme == "file") {
                "text/plain"
            } else {
                context.contentResolver.getType(fileUri) ?: "application/octet-stream"
            }
            // Llamamos al método que hace el trabajo sucio.
            uploadFileFromBytes(baseUrl, token, itemId, fileBytes, fileName, mimeType)
        } catch (exception: CancellationException) {
            throw exception // Si el usuario cancela, no hacemos nada más.
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Sube un archivo usando sus "bytes" (los números que lo componen).
     * Es útil cuando ya tienes el archivo cargado en la memoria y quieres enviarlo
     * sin volver a leerlo del disco. (Por ejemplo, al fusionar archivos viejos con nuevos).
     */
    suspend fun uploadFileFromBytes(
        baseUrl: String,
        token: String,
        itemId: Int,
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "application/octet-stream",
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Preparamos el archivo para el envío.
            val fileRequestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file_1", fileName, fileRequestBody)
                .build()

            // Construimos la dirección web de subida con todos los datos.
            val uploadUrl = buildString {
                append(baseUrl)
                append("/webservice/upload.php")
                append("?token=").append(token)
                append("&itemid=").append(itemId)
                append("&filearea=draft")
                append("&overwrite=true") // Le decimos a Moodle: "si ya existe, machácalo".
            }

            val request = Request.Builder().url(uploadUrl).post(multipartBody).build()

            // Hacemos el envío.
            httpClient.newCall(request).await().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Error HTTP: ${response.code}"))
                }

                // Moodle responde con una lista de archivos subidos.
                val responseJson = JSONArray(responseBody)
                val firstFile = responseJson.optJSONObject(0)

                // Si Moodle se queja de algo (ej: el nombre del archivo ya existe), lanzamos error.
                val errorType = firstFile?.optString("errortype")
                if (!errorType.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Error Moodle: $errorType"))
                }

                // Extraemos el número de ticket (itemid) de la respuesta.
                val uploadedItemId = firstFile?.optInt("itemid", -1) ?: -1
                if (uploadedItemId < 0) return@withContext Result.failure(IOException("Sin itemid"))

                Result.success(uploadedItemId)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}