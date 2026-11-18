package onion.gh0st.iMoodle.ui.glossary

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentGlossaryBinding
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla de un glosario de Moodle.
 *
 * ## Qué muestra
 *
 * La lista de entradas del glosario con su concepto, definición, autor y
 * fecha. Cada tarjeta tiene acciones para editar y borrar, y el botón
 * flotante añade una entrada nueva.
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `GlossaryViewModel`. Las escrituras las confirma Moodle; si
 * el usuario no tiene permiso, la pantalla muestra el motivo que
 * devuelve el servidor.
 *
 * ## Navegación
 *
 * Se abre con [newInstance] desde `CourseDetailFragment`, dentro de
 * `R.id.detail_container`.
 */
@AndroidEntryPoint
class GlossaryFragment : Fragment(R.layout.fragment_glossary) {

    /** Nombre del glosario, recibido como argumento. */
    private var glossaryName: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: GlossaryViewModel by viewModels()

    companion object {

        /**
         * Crea el fragmento para un glosario concreto.
         *
         * @param glossaryId ID de la instancia del glosario.
         * @param glossaryName Nombre del glosario, que se muestra como
         *   título.
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(glossaryId: Int, glossaryName: String): GlossaryFragment =
            GlossaryFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.GLOSSARY_ID, glossaryId)
                    putString(FragmentArgs.MODULE_NAME, glossaryName)
                }
            }
    }

    /**
     * Recupera el nombre del glosario de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glossaryName = arguments?.getString(FragmentArgs.MODULE_NAME) ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGlossaryBinding.bind(view)
        setupToolbar(binding)
        binding.appBarGlossary.applySystemBarsInsets()
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        binding.fabAddEntry.setOnClickListener { showEntryDialog(entry = null) }
        observeUi(binding, adapter)
    }

    /**
     * Pone el nombre del glosario como título y hace que la flecha de
     * volver cierre esta pantalla.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentGlossaryBinding) {
        binding.toolbarGlossary.title = glossaryName
        binding.toolbarGlossary.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Crea el adaptador de entradas con sus acciones.
     *
     * @return El adaptador de la lista.
     */
    private fun buildAdapter() = GlossaryAdapter(
        onEdit = { entry -> showEntryDialog(entry) },
        onDelete = { entry -> confirmDelete(entry) },
    )

    /**
     * Configura la lista y el gesto de arrastrar para refrescar.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun setupRecyclerView(
        binding: FragmentGlossaryBinding,
        adapter: GlossaryAdapter,
    ) {
        binding.rvGlossary.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGlossary.adapter = adapter
        binding.swipeRefreshGlossary.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras
     * la pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun observeUi(binding: FragmentGlossaryBinding, adapter: GlossaryAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Refleja el estado del ViewModel en la UI.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private suspend fun observeState(
        binding: FragmentGlossaryBinding,
        adapter: GlossaryAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.entries)
            binding.swipeRefreshGlossary.isRefreshing = state.isLoading
            binding.fabAddEntry.isEnabled = !state.isSaving
            binding.layoutGlossaryEmpty.isVisible =
                !state.isLoading && state.entries.isEmpty()
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is GlossaryUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                GlossaryUiEvent.EntrySaved -> {
                    dismissEntryDialog()
                    showToast(R.string.toast_glossary_saved)
                }
                GlossaryUiEvent.EntryDeleted -> showToast(R.string.toast_glossary_deleted)
            }
        }
    }

    /**
     * Abre el diálogo de alta o edición.
     *
     * @param entry Entrada a editar, o `null` para crear una nueva.
     */
    private fun showEntryDialog(entry: GlossaryEntry?) {
        if (childFragmentManager.findFragmentByTag(GlossaryEntryDialog.TAG) != null) return

        val dialog = if (entry == null) {
            GlossaryEntryDialog.newInstance()
        } else {
            GlossaryEntryDialog.newInstance(entry)
        }
        dialog.show(childFragmentManager, GlossaryEntryDialog.TAG)
    }

    /**
     * Cierra el diálogo de entrada si sigue abierto.
     */
    private fun dismissEntryDialog() {
        val dialog = childFragmentManager.findFragmentByTag(GlossaryEntryDialog.TAG)
        (dialog as? GlossaryEntryDialog)?.dismissAllowingStateLoss()
    }

    /**
     * Pide confirmación antes de borrar una entrada.
     *
     * @param entry Entrada que se quiere borrar.
     */
    private fun confirmDelete(entry: GlossaryEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_glossary_entry_title)
            .setMessage(R.string.dialog_delete_glossary_entry_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteEntry(entry.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
