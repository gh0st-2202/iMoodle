package onion.gh0st.iMoodle.data.files

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import onion.gh0st.iMoodle.core.FileNames
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.repository.file.FileDownloader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [FileDownloader] con el `DownloadManager` de
 * Android.
 *
 * ## Por qué `DownloadManager` y no una descarga manual
 *
 * Porque es un servicio del sistema que descarga en segundo plano
 * aunque la app se cierre, muestra progreso, reintenta y guarda el
 * archivo en Descargas. Hacerlo a mano obligaría a gestionar hilos,
 * reintentos y notificaciones.
 *
 * ## Dónde guarda el archivo
 *
 * - **Android 10 (API 29) o superior**: en la carpeta pública de
 *   Descargas, sin permisos adicionales.
 * - **Android 9 (API 28)**: en el directorio de Descargas **propio de
 *   la app**. El directorio público exigiría `WRITE_EXTERNAL_STORAGE`
 *   en tiempo de ejecución, que la app no solicita; intentarlo falla
 *   con `SecurityException`.
 *
 * ## Qué NO hace
 *
 * No muestra diálogos ni toasts: devuelve un `Result` y la capa de
 * presentación decide cómo informar. Tampoco comprueba la sesión: eso
 * es responsabilidad del caso de uso
 * [onion.gh0st.iMoodle.domain.usecase.file.DownloadCourseFile].
 *
 * ## Seguridad del nombre
 *
 * El nombre viene del servidor y no es de fiar. Se sanea con
 * [FileNames.sanitize] antes de usarlo como ruta para evitar *path
 * traversal* y caracteres inválidos.
 *
 * ## Sobre la descripción de la notificación
 *
 * Se omite a propósito: escribir texto para el usuario en la capa de
 * datos obligaría a inyectar recursos o un `Context` con strings. El
 * título (el nombre del archivo) ya identifica la descarga.
 *
 * @param context Contexto de aplicación para obtener el servicio del
 *   sistema.
 */
@Singleton
class AndroidFileDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileDownloader {

    /**
     * Encola la descarga con `DownloadManager`.
     *
     * @param file Archivo a descargar.
     * @return `Result.success(Unit)` si se ha encolado; `Result.failure`
     *   con la causa si algo falla.
     */
    override fun download(file: CourseFile): Result<Unit> {
        return try {
            val sanitizedName = FileNames.sanitize(file.fileName)
            val downloadRequest = DownloadManager.Request(file.fileUrl.toUri())
                .setTitle(file.fileName)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadRequest.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    sanitizedName,
                )
            } else {
                downloadRequest.setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    sanitizedName,
                )
            }

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(downloadRequest)
            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
