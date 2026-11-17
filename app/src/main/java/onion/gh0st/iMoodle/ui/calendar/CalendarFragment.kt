package onion.gh0st.iMoodle.ui.calendar

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentCalendarBinding
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla de la agenda del alumno.
 */
@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private val viewModel: CalendarViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCalendarBinding.bind(view)
        binding.appBarCalendar.applySystemBarsInsets()
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Crea el adaptador de la agenda. Al tocar un evento se llama a
     * [onEventClicked].
     *
     * @return El adaptador de la lista.
     */
    private fun buildAdapter() = CalendarAdapter { event -> onEventClicked(event) }

    /**
     * Configura la lista de eventos y el gesto de arrastrar para refrescar.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la agenda.
     */
    private fun setupRecyclerView(
        binding: FragmentCalendarBinding,
        adapter: CalendarAdapter,
    ) {
        binding.rvCalendar.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCalendar.adapter = adapter
        binding.swipeRefreshCalendar.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras la
     * pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la agenda.
     */
    private fun observeUi(binding: FragmentCalendarBinding, adapter: CalendarAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Pinta cada nuevo estado: lista de eventos, indicador de carga y
     * mensaje de "sin eventos".
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la agenda.
     */
    private suspend fun observeState(binding: FragmentCalendarBinding, adapter: CalendarAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.items)
            binding.swipeRefreshCalendar.isRefreshing = state.isLoading
            binding.emptyStateCalendar.isVisible = !state.isLoading && state.items.isEmpty()
            binding.rvCalendar.isVisible = state.items.isNotEmpty()
        }
    }

    /**
     * Reacciona a los eventos de un solo uso: mostrar un error.
     *
     * El permiso de alarmas exactas y el de notificaciones los pide
     * `MainActivity`; esta pantalla no abre ajustes del sistema para no
     * duplicar avisos.
     */
    private suspend fun observeEvents() {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is CalendarUiEvent.ShowError -> showToast(event.messageResId)
            }
        }
    }

    /**
     * Gestiona el toque en un evento: si es una tarea con curso e
     * identificador conocidos abre su detalle; en otro caso avisa de que no hay
     * detalles.
     *
     * @param event Evento tocado.
     */
    private fun onEventClicked(event: CalendarEvent) {
        val courseId = event.courseId
        val instanceId = event.instanceId
        if (event.eventType == "assign" && courseId != null && instanceId != null) {
            openAssignment(courseId, instanceId)
        } else {
            showToast(R.string.error_event_no_details)
        }
    }

    /**
     * Navega al detalle de una tarea en `R.id.detail_container`, el
     * contenedor que `MainActivity` superpone al `ViewPager2`.
     *
     * @param courseId Identificador del curso.
     * @param instanceId Identificador de la tarea.
     */
    private fun openAssignment(courseId: Int, instanceId: Int) {
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                AssignmentDetailFragment.newInstance(courseId, instanceId),
            )
            addToBackStack(null)
        }
    }

}