package onion.gh0st.iMoodle.ui.glossary

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
import onion.gh0st.iMoodle.databinding.DialogGlossaryEntryBinding
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Diálogo para crear o editar una entrada de un glosario.
 *
 * ## Qué permite
 *
 * Escribir el concepto y la definición de una entrada. Si se abre con
 * los datos de una entrada existente, los campos aparecen rellenos y el
 * botón guarda los cambios; si se abre vacío, crea una entrada nueva.
 *
 * ## Quién guarda
 *
 * El diálogo no escribe nada: valida los campos y delega en
 * [GlossaryViewModel], que comparte con el fragmento que lo abrió
 * (`requireParentFragment()`). Cuando el guardado termina bien, es el
 * fragmento quien cierra el diálogo, igual que en
 * `AssignmentSubmissionDialog`.
 *
 * ## Sobre el formato de la definición
 *
 * Moodle guarda la definición en HTML, pero el usuario escribe texto
 * normal. Al abrir una entrada se limpia el HTML (`HtmlUtils`) y al
 * guardar se vuelve a construir uno sencillo con
 * [HtmlUtils.plainTextToHtml]: saltos de línea preservados y caracteres
 * especiales escapados. Por eso, al editar una entrada con formato
 * complejo (tablas, listas), ese formato se pierde.
 */
class GlossaryEntryDialog : DialogFragment() {

    /** Vistas del diálogo. */
    private lateinit var binding: DialogGlossaryEntryBinding

    /** ViewModel del glosario, compartido con el fragmento padre. */
    private val viewModel: GlossaryViewModel by viewModels({ requireParentFragment() })

    /** ID de la entrada que se edita, o `0` si se está creando. */
    private val entryId: Int
        get() = requireArguments().getInt(ARG_ENTRY_ID, 0)

    /**
     * Construye el diálogo con los campos ya rellenos.
     *
     * El botón "Guardar" se reprograma para que **no** cierre el diálogo
     * automáticamente: se cierra solo cuando el guardado tiene éxito, de
     * modo que un error de Moodle no haga perder lo escrito.
     *
     * @param savedInstanceState Estado guardado tras una rotación, o `null`.
     * @return El diálogo listo para mostrar.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogGlossaryEntryBinding.inflate(layoutInflater)
        prefillFields(savedInstanceState)
        clearErrorsOnTyping()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (entryId == 0) R.string.glossary_add_entry else R.string.glossary_edit_entry)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
        }
        observeSavingState(dialog)
        return dialog
    }

    /**
     * Rellena los campos con la entrada que se edita.
     *
     * Cuando Android ha guardado el estado (rotación), los textos ya los
     * restaura él solo, así que no se sobrescriben para no perder lo que
     * el usuario hubiera cambiado.
     *
     * @param savedInstanceState Estado guardado, o `null` si es la primera vez.
     */
    private fun prefillFields(savedInstanceState: Bundle?) {
        binding.inpConcept.setText(arguments?.getString(ARG_ENTRY_CONCEPT).orEmpty())
        val definition = arguments?.getString(ARG_ENTRY_DEFINITION).orEmpty()
        binding.inpDefinition.setText(HtmlUtils.stripHtmlPreservingLines(definition))
        if (savedInstanceState == null) return
        binding.inpConcept.setText(savedInstanceState.getString(STATE_CONCEPT, binding.inpConcept.text?.toString()))
        binding.inpDefinition.setText(
            savedInstanceState.getString(STATE_DEFINITION, binding.inpDefinition.text?.toString()),
        )
    }

    /**
     * Guarda el texto escrito antes de que el sistema destruya el
     * diálogo, para restaurarlo en [prefillFields].
     *
     * @param outState Contenedor del estado a guardar.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CONCEPT, binding.inpConcept.text?.toString())
        outState.putString(STATE_DEFINITION, binding.inpDefinition.text?.toString())
    }

    /**
     * Quita el aviso de error de un campo en cuanto el usuario vuelve a
     * escribir en él.
     */
    private fun clearErrorsOnTyping() {
        binding.inpConcept.doAfterTextChanged { binding.layoutConcept.error = null }
        binding.inpDefinition.doAfterTextChanged { binding.layoutDefinition.error = null }
    }

    /**
     * Bloquea los campos y el botón mientras la entrada se está
     * guardando.
     *
     * @param dialog Diálogo cuyo botón principal se actualiza.
     */
    private fun observeSavingState(dialog: AlertDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val isBusy = state.isSaving
                    binding.inpConcept.isEnabled = !isBusy
                    binding.inpDefinition.isEnabled = !isBusy
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
     * Valida los campos y pide al ViewModel que guarde la entrada.
     *
     * Si algún campo está vacío se marca con su error y no se envía nada
     * a Moodle.
     */
    private fun save() {
        val concept = binding.inpConcept.text?.toString()?.trim().orEmpty()
        val definition = binding.inpDefinition.text?.toString()?.trim().orEmpty()

        binding.layoutConcept.error = if (concept.isEmpty()) {
            getString(R.string.error_concept_required)
        } else {
            null
        }
        binding.layoutDefinition.error = if (definition.isEmpty()) {
            getString(R.string.error_definition_required)
        } else {
            null
        }
        if (concept.isEmpty() || definition.isEmpty()) return

        viewModel.saveEntry(
            entryId = entryId,
            concept = concept,
            definition = HtmlUtils.plainTextToHtml(definition),
        )
    }

    companion object {
        /** Etiqueta con la que se registra el diálogo en el `FragmentManager`. */
        const val TAG = "GlossaryEntryDialog"

        private const val ARG_ENTRY_ID = "ENTRY_ID"
        private const val ARG_ENTRY_CONCEPT = "ENTRY_CONCEPT"
        private const val ARG_ENTRY_DEFINITION = "ENTRY_DEFINITION"

        private const val STATE_CONCEPT = "STATE_CONCEPT"
        private const val STATE_DEFINITION = "STATE_DEFINITION"

        /**
         * Crea el diálogo para añadir una entrada nueva.
         *
         * @return El diálogo con los campos vacíos.
         */
        fun newInstance(): GlossaryEntryDialog = GlossaryEntryDialog().apply {
            arguments = Bundle().apply { putInt(ARG_ENTRY_ID, 0) }
        }

        /**
         * Crea el diálogo para editar una entrada existente.
         *
         * Los datos se pasan como tipos primitivos para que el `Bundle`
         * no necesite `Parcelable` ni `Serializable`.
         *
         * @param entry Entrada que se quiere modificar.
         * @return El diálogo con los campos ya rellenos.
         */
        fun newInstance(entry: GlossaryEntry): GlossaryEntryDialog = GlossaryEntryDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_ENTRY_ID, entry.id)
                putString(ARG_ENTRY_CONCEPT, entry.concept)
                putString(ARG_ENTRY_DEFINITION, entry.definition)
            }
        }
    }
}
