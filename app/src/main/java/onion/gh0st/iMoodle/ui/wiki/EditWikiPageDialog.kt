package onion.gh0st.iMoodle.ui.wiki

import android.app.Dialog
import android.os.Bundle
import android.view.View
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

/**
 * Diálogo para editar el contenido de una página de wiki.
 *
 * ## Qué muestra
 *
 * El contenido **original** de la página (el que guarda Moodle en el
 * formato de la wiki: HTML, Creole…). No se limpia ni se convierte: se
 * devuelve tal cual con los cambios del usuario, que es la única forma
 * de no estropear el formato al guardar. Por eso, si la wiki usa HTML,
 * el usuario verá y editará también las etiquetas.
 *
 * ## De dónde sale el contenido
 *
 * El diálogo no lo pide por su cuenta: se lo pide a
 * [WikiPageViewModel] (compartido con el fragmento padre) nada más
 * abrirse, porque para editar hace falta una llamada distinta a la de
 * lectura (`mod_wiki_get_page_for_editing`).
 *
 * ## Al cerrarse
 *
 * El fragmento descarta el borrador del ViewModel antes de abrir el
 * editor, para que cada edición parta del contenido que Moodle tiene
 * ahora y no de uno viejo.
 */
class EditWikiPageDialog : DialogFragment() {

    /** Vistas del diálogo. */
    private lateinit var binding: DialogWikiPageBinding

    /** ViewModel de la página, compartido con el fragmento padre. */
    private val viewModel: WikiPageViewModel by viewModels({ requireParentFragment() })

    /** Evita sobrescribir lo que escribe el usuario con cada emisión. */
    private var contentLoaded = false

    /**
     * Construye el diálogo y pide el contenido original de la página.
     *
     * El botón "Guardar" se reprograma para que **no** cierre el diálogo
     * automáticamente: se cierra solo cuando Moodle acepta los cambios.
     *
     * @param savedInstanceState Estado guardado tras una rotación, o `null`.
     * @return El diálogo listo para mostrar.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogWikiPageBinding.inflate(layoutInflater)
        binding.layoutWikiTitle.visibility = View.GONE
        binding.inpWikiContent.doAfterTextChanged { binding.layoutWikiContent.error = null }
        contentLoaded = savedInstanceState != null

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wiki_edit_page)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
        }
        observeState(dialog)
        viewModel.requestDraft()
        return dialog
    }

    /**
     * Rellena el campo cuando llega el contenido original y bloquea los
     * controles mientras se guarda.
     *
     * @param dialog Diálogo cuyo botón principal se actualiza.
     */
    private fun observeState(dialog: AlertDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (!contentLoaded && state.draft != null) {
                        contentLoaded = true
                        binding.inpWikiContent.setText(state.draft?.content.orEmpty())
                    }

                    val isBusy = state.isSaving
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
     * Valida el contenido y pide al ViewModel que guarde la página.
     *
     * Se envía el texto tal cual, sin convertirlo: es el formato que
     * espera la wiki.
     */
    private fun save() {
        val content = binding.inpWikiContent.text?.toString().orEmpty()
        if (content.isBlank()) {
            binding.layoutWikiContent.error = getString(R.string.error_content_required)
            return
        }

        viewModel.savePage(content)
    }

    companion object {
        /** Etiqueta con la que se registra el diálogo en el `FragmentManager`. */
        const val TAG = "EditWikiPageDialog"

        /**
         * Crea el diálogo.
         *
         * @return El diálogo listo para pedir el contenido.
         */
        fun newInstance(): EditWikiPageDialog = EditWikiPageDialog()
    }
}
