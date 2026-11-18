package onion.gh0st.iMoodle.ui.wiki

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.DialogWikiPageBinding
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Diálogo para crear una página nueva en una wiki.
 *
 * ## Qué permite
 *
 * Escribir el título y el contenido inicial. El contenido se escribe en
 * texto plano y se envía a Moodle como HTML sencillo, con los saltos de
 * línea preservados (ver `HtmlUtils.plainTextToHtml`).
 *
 * ## Quién crea la página
 *
 * El diálogo valida los campos y delega en [WikiViewModel], que comparte
 * con el fragmento que lo abrió (`requireParentFragment()`). Cuando la
 * página se crea, el fragmento cierra el diálogo y la abre.
 */
class NewWikiPageDialog : DialogFragment() {

    /** Vistas del diálogo. */
    private lateinit var binding: DialogWikiPageBinding

    /** ViewModel de la wiki, compartido con el fragmento padre. */
    private val viewModel: WikiViewModel by viewModels({ requireParentFragment() })

    /**
     * Construye el diálogo.
     *
     * El botón "Guardar" se reprograma para que **no** cierre el diálogo
     * automáticamente: se cierra solo cuando Moodle acepta la página, de
     * modo que un error no haga perder lo escrito.
     *
     * @param savedInstanceState Estado guardado tras una rotación, o `null`.
     * @return El diálogo listo para mostrar.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogWikiPageBinding.inflate(layoutInflater)
        restoreText(savedInstanceState)
        clearErrorsOnTyping()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wiki_add_page)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
        }
        observeCreatingState(dialog)
        return dialog
    }

    /**
     * Guarda el texto escrito antes de que el sistema destruya el
     * diálogo.
     *
     * @param outState Contenedor del estado a guardar.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TITLE, binding.inpWikiTitle.text?.toString())
        outState.putString(STATE_CONTENT, binding.inpWikiContent.text?.toString())
    }

    /**
     * Restaura el texto guardado tras una rotación.
     *
     * @param savedInstanceState Estado guardado, o `null` si es la primera vez.
     */
    private fun restoreText(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        binding.inpWikiTitle.setText(savedInstanceState.getString(STATE_TITLE).orEmpty())
        binding.inpWikiContent.setText(savedInstanceState.getString(STATE_CONTENT).orEmpty())
    }

    /**
     * Quita el aviso de error de un campo en cuanto el usuario vuelve a
     * escribir en él.
     */
    private fun clearErrorsOnTyping() {
        binding.inpWikiTitle.doAfterTextChanged { binding.layoutWikiTitle.error = null }
        binding.inpWikiContent.doAfterTextChanged { binding.layoutWikiContent.error = null }
    }

    /**
     * Bloquea los campos y el botón mientras la página se está creando.
     *
     * @param dialog Diálogo cuyo botón principal se actualiza.
     */
    private fun observeCreatingState(dialog: AlertDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val isBusy = state.isCreating
                    binding.inpWikiTitle.isEnabled = !isBusy
                    binding.inpWikiContent.isEnabled = !isBusy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        isEnabled = !isBusy
                        text = getString(if (isBusy) R.string.label_saving else R.string.action_save)
                    }
                    dialog.setCancelable(!isBusy)
                }
            }
        }
    }

    /**
     * Valida los campos y pide al ViewModel que cree la página.
     *
     * Si algún campo está vacío se marca con su error y no se envía nada
     * a Moodle.
     */
    private fun save() {
        val title = binding.inpWikiTitle.text?.toString()?.trim().orEmpty()
        val content = binding.inpWikiContent.text?.toString()?.trim().orEmpty()

        binding.layoutWikiTitle.error = if (title.isEmpty()) {
            getString(R.string.error_title_required)
        } else {
            null
        }
        binding.layoutWikiContent.error = if (content.isEmpty()) {
            getString(R.string.error_content_required)
        } else {
            null
        }
        if (title.isEmpty() || content.isEmpty()) return

        viewModel.createPage(title = title, content = HtmlUtils.plainTextToHtml(content))
    }

    companion object {
        /** Etiqueta con la que se registra el diálogo en el `FragmentManager`. */
        const val TAG = "NewWikiPageDialog"

        private const val STATE_TITLE = "STATE_TITLE"
        private const val STATE_CONTENT = "STATE_CONTENT"

        /**
         * Crea el diálogo.
         *
         * @return El diálogo con los campos vacíos.
         */
        fun newInstance(): NewWikiPageDialog = NewWikiPageDialog()
    }
}
