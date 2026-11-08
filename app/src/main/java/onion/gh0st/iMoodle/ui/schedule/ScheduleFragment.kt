package onion.gh0st.iMoodle.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import onion.gh0st.iMoodle.databinding.FragmentScheduleBinding

/**
 * Pantalla de horario semanal.
 *
 * ## Qué muestra
 *
 * - **Chips de filtro por día** (Lunes, Martes, …).
 * - **Lista de franjas horarias** del día seleccionado, con color
 *   por asignatura, rango horario, asignatura y profesor.
 * - **Pull-to-refresh** para recargar.
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `ScheduleViewModel`.
 *
 * ## Manejo de errores
 *
 * Los errores se consumen como eventos de un solo uso
 * (`ScheduleUiEvent.ShowError`).
 */
@AndroidEntryPoint
class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    /** ViewModel de la pantalla. */
    private val viewModel: ScheduleViewModel by viewModels()

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentScheduleBinding.bind(view)
        setupWindowInsets(binding)

        val adapter = ScheduleAdapter()
        setupRecyclerView(binding, adapter)

        observeUi(binding, adapter)
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentScheduleBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarSchedule) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                view.paddingBottom,
            )
            insets
        }
    }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
     */
    private fun setupRecyclerView(
        binding: FragmentScheduleBinding,
        adapter: ScheduleAdapter,
    ) {
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.adapter = adapter
        binding.swipeRefreshSchedule.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private fun observeUi(binding: FragmentScheduleBinding, adapter: ScheduleAdapter) {
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
        binding: FragmentScheduleBinding,
        adapter: ScheduleAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.items)
            binding.swipeRefreshSchedule.isRefreshing = state.isLoading
            binding.emptyStateSchedule.isVisible = !state.isLoading && state.items.isEmpty()
            binding.rvSchedule.isVisible = state.items.isNotEmpty()

            if (binding.chipGroupDays.tag != state.days) {
                binding.chipGroupDays.tag = state.days
                populateChips(binding.chipGroupDays, state.days, state.selectedDay)
            }
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is ScheduleUiEvent.ShowError -> showToast(event.messageResId)
            }
        }
    }

    /**
     * Reconstruye los chips de filtro por día.
     *
     * @param chipGroup Grupo de chips.
     * @param days Días disponibles.
     * @param selectedDay Día actualmente seleccionado.
     */
    private fun populateChips(group: ChipGroup, days: List<String>, selectedDay: String) {
        group.removeAllViews()
        days.forEach { day ->
            val isSelected = day == selectedDay
            group.addView(createChip(day, isSelected) { viewModel.setDay(day) })
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
     * Muestra un toast corto.
     *
     * @param messageResId ID del recurso de string.
     */
    private fun showToast(@StringRes messageResId: Int) {
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
    }
}