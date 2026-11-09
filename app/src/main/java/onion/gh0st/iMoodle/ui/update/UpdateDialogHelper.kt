package onion.gh0st.iMoodle.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.update.AppUpdate

/**
 * Ayudante para mostrar el diálogo de actualización.
 *
 * ## Qué hace
 * Muestra una ventana flotante al usuario con la información de la
 * nueva versión. Le ofrece dos opciones: descargar la actualización
 * (abriendo el navegador) o posponerlo para más tarde.
 */
object UpdateDialogHelper {

    /**
     * Construye y muestra el diálogo.
     *
     * @param context El contexto de la pantalla donde se muestra.
     * @param update Los datos de la actualización disponible.
     */
    fun showUpdateDialog(context: Context, update: AppUpdate) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.update_dialog_title, update.versionName))
            .setMessage(update.changelog)
            .setCancelable(false)
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.update_action_later, null)
            .show()
    }
}