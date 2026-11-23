package onion.gh0st.iMoodle.ui.pasengrades

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentPasenGradesBinding
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla de calificaciones de iPasen.
 *
 * ## Qué muestra
 *
 * - **Chips de convocatorias** (1ª Evaluación, 2ª…), como los días
 *   de la semana en el Horario.
 * - **Lista de asignaturas** de la convocatoria seleccionada, con su
 *   nota.
 * - **Pull-to-refresh** para recargar.
 */
@AndroidEntryPoint
class PasenGradesFragment : Fragment(R.layout.fragment_pasen_grades) {

    /** ViewModel de la pantalla. */
    private val viewModel: PasenGradesViewModel by viewModels()

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPasenGradesBinding.bind(view)
        binding.appBarPasenGrades.applySystemBarsInsets()

        val adapter = PasenGradesAdapter()
        setupRecyclerView(binding, adapter)

        observeUi(binding, adapter)
    }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
     */
    private fun setupRecyclerView(
        binding: FragmentPasenGradesBinding,
        adapter: PasenGradesAdapter,
    ) {
        binding.rvPasenGrades.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPasenGrades.adapter = adapter
        binding.swipeRefreshPasenGrades.setOnRefreshListener { viewModel.refresh() }
        binding.btnPasenGradesRetry.setOnClickListener { viewModel.refresh() }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private fun observeUi(
        binding: FragmentPasenGradesBinding,
        adapter: PasenGradesAdapter,
    ) {
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
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private suspend fun observeState(
        binding: FragmentPasenGradesBinding,
        adapter: PasenGradesAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.subjects)
            binding.swipeRefreshPasenGrades.isRefreshing = state.isLoading
            binding.rvPasenGrades.isVisible = state.subjects.isNotEmpty()

            val isEmpty = !state.isLoading && state.subjects.isEmpty()
            binding.emptyStatePasenGrades.isVisible = isEmpty
            if (isEmpty) {
                binding.txtPasenGradesEmpty.setText(
                    if (state.hasError) R.string.error_pasen_grades_load else R.string.empty_pasen_grades,
                )
            }
            binding.btnPasenGradesRetry.isVisible = state.hasError && state.subjects.isEmpty()

            if (binding.chipGroupConvocatorias.tag != state.convocatorias) {
                binding.chipGroupConvocatorias.tag = state.convocatorias
                populateChips(binding.chipGroupConvocatorias, state.convocatorias, state.selectedConvocatoria)
            }
        }
    }

    /**
     * Reconstruye los chips de convocatorias.
     *
     * @param group Grupo de chips.
     * @param convocatorias Convocatorias disponibles.
     * @param selected Convocatoria actualmente seleccionada.
     */
    private fun populateChips(
        group: ChipGroup,
        convocatorias: List<onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria>,
        selected: String?,
    ) {
        group.removeAllViews()
        convocatorias.forEach { convocatoria ->
            val isSelected = convocatoria.course == selected
            group.addView(createChip(convocatoria.course, isSelected) {
                viewModel.setConvocatoria(convocatoria.course)
            })
        }
    }

    /**
     * Construye un chip individual con el estilo del tema.
     *
     * @param text Texto a mostrar.
     * @param selected `true` si el chip debe estar seleccionado.
     * @param onClick Callback al pulsar el chip.
     * @return El chip configurado.
     */
    private fun createChip(text: String, selected: Boolean, onClick: () -> Unit): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isCheckedIconVisible = true
            isChecked = selected
            setOnClickListener { onClick() }
        }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is PasenGradesUiEvent.ShowError -> showToast(event.messageResId)
            }
        }
    }

    companion object {
        /**
         * Crea el fragmento.
         *
         * @return El fragmento con sus argumentos configurados.
         */
        fun newInstance(): PasenGradesFragment = PasenGradesFragment()
    }
}
