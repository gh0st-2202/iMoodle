package onion.gh0st.iMoodle.ui.files

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.ui.common.FileNames

/**
 * Helper para descargar archivos de Moodle.
 *
 * ## Qué hace
 *
 * Encapsula las dos operaciones necesarias para descargar un
 * archivo:
 *
 * 1. Mostrar un **diálogo de confirmación** (para que el usuario
 *    confirme antes de descargar).
 * 2. Lanzar la **descarga** con el `DownloadManager` de Android.
 *
 * ## Por qué usar `DownloadManager` y no una descarga manual
 *
 * Porque `DownloadManager` es un servicio del sistema que:
 *
 * - Descarga en background, aunque la app se cierre.
 * - Muestra una notificación de progreso.
 * - Gestiona reintentos y errores de red.
 * - Guarda el archivo en el directorio público de Descargas.
 *
 * Hacerlo a mano requeriría gestionar hilos, reintentos,
 * notificaciones, etc., y sería mucho más propenso a errores.
 *
 * ## Sobre el token en la URL
 *
 * La URL del archivo (`CourseFile.fileUrl`) **ya lleva** el token de
 * Moodle cuando apunta al propio Moodle. El token se añade en el
 * repositorio con `appendMoodleToken`. Este helper **no recibe
 * token** ni lo añade: solo usa la URL tal cual.
 *
 * ## Sobre el nombre del archivo
 *
 * El nombre viene del servidor, así que **no es de fiar**. Antes de
 * usarlo como ruta de destino, se sanea con `FileNames.sanitize` para
 * evitar path traversal y caracteres inválidos.
 *
 * ## Verificación de sesión
 *
 * Este helper **no** verifica si hay sesión activa. Esa verificación
 * la hace cada Fragment llamador antes de invocar el helper. Así el
 * helper es independiente del estado de la sesión y más fácil de
 * testear.
 */
object FileDownloadHelper {

    /**
     * Muestra un diálogo de confirmación antes de descargar.
     *
     * ## Qué hace
     *
     * Muestra un `MaterialAlertDialog` con:
     *
     * - **Título**: "Descargar archivo".
     * - **Mensaje**: "¿Quieres descargar `<nombre>`?".
     * - **Botón positivo**: "Descargar" → llama al callback.
     * - **Botón negativo**: "Cancelar" → no hace nada.
     *
     * @param context Contexto para el diálogo.
     * @param file Archivo a descargar.
     * @param onConfirm Callback si el usuario confirma.
     */
    fun showConfirmationDialog(
        context: Context,
        file: CourseFile,
        onConfirm: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_download_title)
            .setMessage(context.getString(R.string.dialog_download_message, file.fileName))
            .setPositiveButton(R.string.action_download) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Lanza la descarga del archivo con `DownloadManager`.
     *
     * ## Qué hace paso a paso
     *
     * 1. Construye una solicitud de descarga con:
     *    - **URL**: la del archivo (ya lleva token).
     *    - **Título**: el nombre del archivo.
     *    - **Descripción**: un texto genérico.
     *    - **Visibilidad**: visible, con notificación al completar.
     *    - **Destino**: `Descargas/<nombre saneado>`.
     * 2. La encola en `DownloadManager`.
     * 3. Muestra un toast de "descargando".
     *
     * ## Manejo de errores
     *
     * Si algo falla (por ejemplo, no hay `DownloadManager`
     * disponible, o el nombre no se puede sanear), se captura la
     * excepción y se muestra un toast de error con el mensaje.
     *
     * ## Sobre el nombre saneado
     *
     * Se usa `FileNames.sanitize` para convertir el nombre que viene
     * del servidor en uno seguro. Por ejemplo,
     * `"../../etc/passwd"` se convierte en `"passwd"`.
     *
     * @param context Contexto para el sistema.
     * @param file Archivo a descargar.
     */
    fun download(context: Context, file: CourseFile) {
        try {
            val downloadRequest = DownloadManager.Request(Uri.parse(file.fileUrl))
                .setTitle(file.fileName)
                .setDescription(context.getString(R.string.notification_download_description))
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, FileNames.sanitize(file.fileName),
                )
                .setAllowedOverMetered(true)

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(downloadRequest)
            Toast.makeText(
                context,
                context.getString(R.string.toast_downloading, file.fileName),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_download_error, exception.message.orEmpty()),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}