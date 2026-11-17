package onion.gh0st.iMoodle.api.moodle.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.source
import onion.gh0st.iMoodle.core.Defaults
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.core.mimeType
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.core.sizeInBytes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Cliente que sube archivos al *área de borrador* (`draft area`) de Moodle.
 *
 * ## Qué es el área de borrador
 *
 * Moodle no guarda los archivos directamente en la tarea. Primero los
 * deja en una zona temporal llamada *draft area* y devuelve un número
 * (`itemid`) que identifica esa zona. Al entregar la tarea se pasa ese
 * `itemid` y Moodle recoge de ahí los archivos.
 *
 * - Con `itemId = 0` Moodle **crea** un área nueva y devuelve su `itemid`.
 * - Con un `itemId` existente, el archivo se **añade** a esa misma área.
 *
 * ## Cómo se envía el archivo
 *
 * Como `multipart/form-data` al endpoint `webservice/upload.php`. El
 * token, el `itemid` y el resto de campos viajan **dentro del cuerpo**
 * de la petición, no en la URL (antes el token iba en la query string,
 * donde puede acabar en registros de servidores y proxies).
 *
 * ## Memoria
 *
 * El contenido del archivo **no se carga entero en memoria**: se envía
 * en streaming directamente desde el almacenamiento (ver
 * [ContentUriRequestBody]). Así un PDF de decenas de megas no provoca un
 * `OutOfMemoryError`.
 *
 * @param context Contexto de la app, para leer las URIs `content://`.
 * @param httpClient Cliente HTTP de la sesión (con sus cookies).
 * @param userAgent Valor de la cabecera `User-Agent`.
 */
class MoodleUploadClient(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val userAgent: String = Defaults.USER_AGENT,
) {

    /**
     * Sube un archivo del teléfono (identificado por su URI) al área de
     * borrador.
     *
     * ## Estrategia según el tamaño
     *
     * - Si el proveedor informa del tamaño, el archivo se envía en
     *   streaming con `Content-Length` conocido.
     * - Si no lo informa (proveedores virtuales), se copia primero a un
     *   fichero temporal de la caché para conocer su tamaño y se envía
     *   desde ahí. Es más lento pero evita enviar el cuerpo con
     *   codificación *chunked*, que algunos servidores rechazan.
     *
     * @param baseUrl URL base de Moodle (por ejemplo, `https://…/moodle`).
     * @param token `wstoken` de la sesión.
     * @param itemId Identificador del área de borrador (0 para crear una nueva).
     * @param fileUri URI del archivo (`content://` o `file://`).
     * @param fileName Nombre con el que se guardará en Moodle.
     * @return El `itemid` del área de borrador, o el fallo ocurrido.
     */
    suspend fun uploadFile(
        baseUrl: String,
        token: String,
        itemId: Int,
        fileUri: Uri,
        fileName: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val contentResolver = context.contentResolver
            val mediaType = fileUri.mimeType(contentResolver).toMediaTypeOrNull()
            val knownSize = fileUri.sizeInBytes(contentResolver)

            if (knownSize != null) {
                val fileBody = ContentUriRequestBody(context, fileUri, mediaType, knownSize)
                upload(baseUrl, token, itemId, fileName, fileBody)
            } else {
                val temporaryFile = copyToCache(fileUri)
                try {
                    upload(baseUrl, token, itemId, fileName, temporaryFile.asRequestBody(mediaType))
                } finally {
                    temporaryFile.delete()
                }
            }
        }
    }

    /**
     * Sube un fichero local (por ejemplo, uno descargado previamente a la
     * caché) al área de borrador.
     *
     * @param baseUrl URL base de Moodle.
     * @param token `wstoken` de la sesión.
     * @param itemId Identificador del área de borrador (0 para crear una nueva).
     * @param file Fichero a subir.
     * @param fileName Nombre con el que se guardará en Moodle.
     * @param mimeType Tipo MIME del contenido.
     * @return El `itemid` del área de borrador, o el fallo ocurrido.
     */
    suspend fun uploadFromFile(
        baseUrl: String,
        token: String,
        itemId: Int,
        file: File,
        fileName: String,
        mimeType: String = DEFAULT_MIME_TYPE,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            upload(baseUrl, token, itemId, fileName, file.asRequestBody(mimeType.toMediaTypeOrNull()))
        }
    }

    /**
     * Envía el formulario `multipart` a `upload.php` e interpreta la respuesta.
     *
     * @param baseUrl URL base de Moodle.
     * @param token `wstoken` de la sesión.
     * @param itemId Identificador del área de borrador.
     * @param fileName Nombre del archivo en Moodle.
     * @param fileBody Cuerpo con el contenido del archivo.
     * @return El `itemid` devuelto por Moodle.
     * @throws IOException si el servidor responde con error o con un formato inesperado.
     */
    private suspend fun upload(
        baseUrl: String,
        token: String,
        itemId: Int,
        fileName: String,
        fileBody: RequestBody,
    ): Int {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("token", token)
            .addFormDataPart("filearea", "draft")
            .addFormDataPart("itemid", itemId.toString())
            .addFormDataPart("overwrite", "true")
            .addFormDataPart("file_1", fileName, fileBody)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/webservice/upload.php")
            .header("User-Agent", userAgent)
            .post(multipartBody)
            .build()

        return httpClient.newCall(request).await().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Error HTTP ${response.code} al subir el archivo")
            parseUploadedItemId(responseBody)
        }
    }

    /**
     * Extrae el `itemid` de la respuesta de `upload.php`.
     *
     * ## Formatos posibles
     *
     * - **Éxito**: un array JSON con un objeto por archivo subido, donde
     *   cada objeto incluye `itemid`.
     * - **Error de archivo**: el primer objeto del array trae `errortype`
     *   (por ejemplo, un nombre duplicado).
     * - **Error de la petición** (token inválido, subida no permitida…):
     *   un objeto JSON suelto con el campo `error`.
     *
     * @param responseBody Cuerpo de la respuesta.
     * @return El `itemid` del área de borrador.
     * @throws IOException si la respuesta indica un error o no contiene `itemid`.
     */
    private fun parseUploadedItemId(responseBody: String): Int {
        val trimmedBody = responseBody.trim()

        if (trimmedBody.startsWith("{")) {
            val errorMessage = JSONObject(trimmedBody).optString("error").ifBlank { "respuesta inesperada" }
            throw IOException("Moodle rechazó la subida: $errorMessage")
        }

        val firstFile = JSONArray(trimmedBody).optJSONObject(0)
            ?: throw IOException("Moodle no devolvió información del archivo subido")

        val errorType = firstFile.optString("errortype")
        if (errorType.isNotBlank()) throw IOException("Moodle rechazó el archivo: $errorType")

        val uploadedItemId = firstFile.optInt("itemid", -1)
        if (uploadedItemId < 0) throw IOException("Moodle no devolvió el itemid del área de borrador")
        return uploadedItemId
    }

    /**
     * Copia el contenido de una URI a un fichero temporal de la caché.
     *
     * @param fileUri URI de origen.
     * @return El fichero temporal (el llamador debe borrarlo).
     * @throws IOException si no se puede abrir la URI.
     */
    private fun copyToCache(fileUri: Uri): File {
        val temporaryFile = File.createTempFile("upload_", ".tmp", context.cacheDir)
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: throw IOException("No se pudo abrir el archivo")
            inputStream.use { input ->
                temporaryFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (throwable: Throwable) {
            temporaryFile.delete()
            throw throwable
        }
        return temporaryFile
    }

    /**
     * Cuerpo de petición que envía el contenido de una URI **en streaming**,
     * leyéndolo del almacenamiento a medida que se transmite.
     *
     * ## Por qué hace falta una clase propia
     *
     * OkHttp sabe enviar `File` y `ByteArray`, pero no una URI de
     * `ContentResolver`. Esta clase abre el `InputStream` solo cuando
     * OkHttp pide escribir el cuerpo ([writeTo]) y lo vuelca en el
     * destino sin acumularlo en memoria. Si OkHttp tiene que reintentar
     * la petición, simplemente se vuelve a abrir.
     *
     * @param context Contexto de la app.
     * @param uri URI del contenido.
     * @param mediaType Tipo de contenido que se declara.
     * @param totalBytes Tamaño exacto en bytes (debe ser conocido).
     */
    private class ContentUriRequestBody(
        private val context: Context,
        private val uri: Uri,
        private val mediaType: MediaType?,
        private val totalBytes: Long,
    ) : RequestBody() {

        /** Tipo de contenido declarado en la parte `multipart`. */
        override fun contentType(): MediaType? = mediaType

        /** Tamaño exacto del contenido, para poder enviar `Content-Length`. */
        override fun contentLength(): Long = totalBytes

        /**
         * Vuelca el contenido de la URI en el destino.
         *
         * @param sink Destino de la escritura de OkHttp.
         * @throws IOException si no se puede abrir o leer la URI.
         */
        override fun writeTo(sink: BufferedSink) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo")
            inputStream.source().use { source -> sink.writeAll(source) }
        }
    }

    private companion object {
        /** Tipo MIME por defecto cuando no se conoce el real. */
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
