package onion.gh0st.iMoodle.ui.files

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import onion.gh0st.iMoodle.R

/**
 * Diálogo de confirmación de descarga.
 *
 * ## Por qué solo es un diálogo
 *
 * Antes esta pieza también lanzaba la descarga con el `DownloadManager`
 * y mostraba toasts. Eso mezclaba dos capas: la vista (preguntar al
 * usuario) y la infraestructura (hablar con el sistema). Ahora:
 *
 * - El **diálogo** vive aquí, en `ui/`, porque es puro Android.
 * - La **descarga** vive en `data/files/AndroidFileDownloader`.
 * - La **decisión** (¿hay sesión?) vive en
 *   `domain/usecase/file/DownloadCourseFile`.
 * - Los **avisos** los emite el ViewModel como eventos y los pinta el
 *   Fragment.
 *
 * ## Qué NO hace
 *
 * No comprueba la sesión ni conoce Moodle: solo muestra un texto con
 * el nombre del archivo y devuelve la confirmación por callback.
 */
object FileDownloadDialog {

    /**
     * Muestra el diálogo de confirmación.
     *
     * @param context Contexto para construir el diálogo.
     * @param fileName Nombre del archivo que se va a descargar.
     * @param onConfirm Se ejecuta si el usuario pulsa "Descargar".
     */
    fun show(
        context: Context,
        fileName: String,
        onConfirm: () -> Unit,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_download_title)
            .setMessage(context.getString(R.string.dialog_download_message, fileName))
            .setPositiveButton(R.string.action_download) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
