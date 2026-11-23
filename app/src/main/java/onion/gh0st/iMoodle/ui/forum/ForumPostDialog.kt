package onion.gh0st.iMoodle.ui.forum

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.DialogForumPostBinding
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Diálogo para escribir en un foro: un tema nuevo o una respuesta.
 *
 * ## Qué valida
 *
 * - El asunto, solo cuando la pantalla lo pide (en las respuestas se
 *   rellena solo con "Re: …" y no se muestra).
 * - El mensaje, siempre.
 *
 * Si algo falta, el diálogo se queda abierto y marca el campo en rojo
 * con el motivo. Solo cierra cuando el usuario envía algo válido.
 *
 * ## Quién decide qué hacer con el texto
 *
 * El diálogo no publica nada: entrega el asunto y el mensaje a
 * [onSend]. Así la lógica de red queda en el ViewModel.
 *
 * El mensaje se convierte antes de entregarlo con
 * [HtmlUtils.plainTextToHtml]: Moodle interpreta el cuerpo del post
 * como HTML, así que hay que escapar los caracteres especiales y
 * conservar los saltos de línea.
 */
object ForumPostDialog {

    /**
     * Muestra el diálogo.
     *
     * @param context Contexto de una Activity.
     * @param titleRes Título del diálogo.
     * @param showSubject `true` si hay que pedir asunto (tema nuevo).
     * @param initialSubject Asunto inicial, o `null` para dejarlo vacío.
     * @param onSend Se ejecuta con el asunto y el mensaje ya validados.
     */
    fun show(
        context: Context,
        @StringRes titleRes: Int,
        showSubject: Boolean,
        initialSubject: String?,
        onSend: (subject: String, message: String) -> Unit,
    ) {
        val binding = DialogForumPostBinding.inflate(LayoutInflater.from(context))
        binding.layoutSubject.isVisible = showSubject
        binding.inpSubject.setText(initialSubject.orEmpty())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.action_send, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val subject = binding.inpSubject.text?.toString()?.trim().orEmpty()
                val message = binding.inpMessage.text?.toString()?.trim().orEmpty()
                val subjectError = showSubject && subject.isBlank()
                val messageError = message.isBlank()
                binding.layoutSubject.error = if (subjectError) {
                    context.getString(R.string.error_forum_subject_required)
                } else {
                    null
                }
                binding.layoutMessage.error = if (messageError) {
                    context.getString(R.string.error_forum_message_required)
                } else {
                    null
                }
                if (subjectError || messageError) return@setOnClickListener

                onSend(subject, HtmlUtils.plainTextToHtml(message))
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
