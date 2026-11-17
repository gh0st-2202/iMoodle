package onion.gh0st.iMoodle.ui.update

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.noties.markwon.Markwon
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.DialogUpdateBinding
import onion.gh0st.iMoodle.domain.model.update.AppUpdate

/**
 * Construye y muestra el diálogo que avisa de una nueva versión.
 *
 * ## Qué muestra
 *
 * El número de versión en el título, las notas de la versión renderizadas
 * desde Markdown (con [Markwon], igual que en GitHub) y dos botones:
 * "Descargar" y "Más tarde". Si la publicación no tiene notas se muestra
 * un texto por defecto.
 *
 * ## Quién decide qué pasa
 *
 * El diálogo no sabe nada de descargas: avisa a quien lo muestra mediante
 * los callbacks `onDownload` y `onLater`. Así no depende de ningún ámbito
 * de corrutinas ni de la Activity, y la lógica queda en [UpdateViewModel].
 */
object UpdateDialogHelper {

    /** Fracción del ancho de pantalla que ocupa el diálogo. */
    private const val DIALOG_WIDTH_FRACTION = 0.90

    /**
     * Muestra el diálogo de actualización.
     *
     * @param context Contexto de una Activity (el diálogo se ata a su ventana).
     * @param update Datos de la nueva versión.
     * @param onDownload Se ejecuta si el usuario pulsa "Descargar".
     * @param onLater Se ejecuta si el usuario pulsa "Más tarde".
     * @return El diálogo mostrado, para que quien lo llama pueda cerrarlo
     *   si la Activity se destruye (evita ventanas filtradas).
     */
    fun showUpdateDialog(
        context: Context,
        update: AppUpdate,
        onDownload: () -> Unit,
        onLater: () -> Unit,
    ): Dialog {
        val binding = DialogUpdateBinding.inflate(LayoutInflater.from(context))
        val changelog = update.changelog.ifBlank { context.getString(R.string.update_no_changelog) }
        Markwon.create(context).setMarkdown(binding.txtChangelog, changelog)
        binding.txtChangelog.movementMethod = LinkMovementMethod.getInstance()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.update_dialog_title, update.versionName))
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.update_action_download) { _, _ -> onDownload() }
            .setNegativeButton(R.string.update_action_later) { _, _ -> onLater() }
            .create()

        dialog.setOnShowListener {
            applyRoundedCornersToWindow(context, dialog)
            limitWindowSize(context, dialog)
        }
        dialog.show()
        return dialog
    }

    /**
     * Aplica al fondo de la ventana del diálogo las esquinas redondeadas
     * definidas en `update_dialog_corner_radius`, con el color de
     * superficie del tema.
     *
     * @param context Contexto para leer dimensiones y colores del tema.
     * @param dialog Diálogo cuya ventana se decora.
     */
    private fun applyRoundedCornersToWindow(context: Context, dialog: Dialog) {
        val cornerRadiusPx = context.resources.getDimension(R.dimen.update_dialog_corner_radius)
        val surfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE,
        )
        val roundedBackground = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(cornerRadiusPx)
                .build(),
        ).apply {
            fillColor = ColorStateList.valueOf(surfaceColor)
        }
        dialog.window?.setBackgroundDrawable(roundedBackground)
    }

    /**
     * Limita el ancho del diálogo a una fracción de la pantalla y deja el
     * alto ajustado al contenido.
     *
     * @param context Contexto para leer las métricas de pantalla.
     * @param dialog Diálogo a redimensionar.
     */
    private fun limitWindowSize(context: Context, dialog: Dialog) {
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val dialogWidthPx = (screenWidthPx * DIALOG_WIDTH_FRACTION).toInt()
        dialog.window?.setLayout(dialogWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
