package onion.gh0st.iMoodle.ui.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

/**
 * Pantalla de la agenda del alumno.
 *
 * ## Qué muestra
 *
 * Los eventos de Moodle de los próximos 30 días, agrupados por día
 * (Hoy, Mañana, "Lunes, 15 de enero"...). Cada evento tiene:
 *
 * - Nombre.
 * - Hora.
 * - Curso asociado (si lo tiene).
 * - Icono según el tipo.
 *
 * Al pulsar un evento de tipo tarea (`"assign"`), navega al detalle
 * de la tarea.
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `CalendarViewModel`, que a su vez observa la base de
 * datos local (offline-first). El Fragment solo se preocupa de:
 *
 * 1. Configurar el adapter.
 * 2. Observar el estado.
 * 3. Reaccionar a eventos.
 * 4. Pedir permisos.
 *
 * ## Permisos
 *
 * Esta pantalla es la que más permisos necesita:
 *
 * 1. **`POST_NOTIFICATIONS`** (Android 13+): para mostrar las
 *    notificaciones de recordatorio.
 * 2. **`SCHEDULE_EXACT_ALARM`** (Android 12+): para programar
 *    alarmas exactas. No se pide con `launcher` sino abriendo los
 *    ajustes del sistema, porque Android no tiene un diálogo
 *    estándar para este permiso.
 *
 * ## Por qué se piden permisos aquí
 *
 * Porque la agenda es el punto donde el usuario ve las alarmas
 * programadas. Si no se piden al entrar, el usuario podría no
 * enterarse de que hay alarmas funcionando sin permiso. Pidiéndolos
 * al entrar, queda claro desde el primer momento.
 */
@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    /** ViewModel de la pantalla. */
    private val viewModel: CalendarViewModel by viewModels()

    /**
     * Launcher para pedir el permiso `POST_NOTIFICATIONS`.
     *
     * Cuando el usuario responde, se le muestra un toast
     * confirmando o avisando.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onPermissionResult(granted) }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz.
     * @param savedInstanceState Estado previo (no usado: el estado
     *   está en el ViewModel).
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCalendarBinding.bind(view)
        setupWindowInsets(binding)
        ensureNotificationPermission(savedInstanceState)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentCalendarBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarCalendar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }
    }

    /**
     * Solicita el permiso `POST_NOTIFICATIONS` si es necesario.
     *
     * ## Cuándo se solicita
     *
     * - **Android 13+ (API 33+)**: siempre, si no está concedido.
     * - **Android 12 o anterior**: no se necesita el permiso (las
     *   notificaciones se conceden al instalar).
     * - **Recreación** (`savedInstanceState != null`): no se
     *   solicita (ya se pidió antes, no spamear al usuario).
     *
     * @param savedInstanceState Estado previo.
     */
    private fun ensureNotificationPermission(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Comprueba si el permiso `POST_NOTIFICATIONS` está concedido.
     *
     * @return `true` si está concedido.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Maneja la respuesta del permiso de notificaciones.
     *
     * @param granted `true` si el usuario concedió el permiso.
     */
    private fun onPermissionResult(granted: Boolean) {
        if (granted) showToast(R.string.toast_notifications_enabled)
        else showToast(R.string.toast_no_notifications_permission_short)
    }

    /**
     * Construye el adapter con el callback de click.
     *
     * @return El adapter listo.
     */
    private fun buildAdapter() = CalendarAdapter { event -> onEventClicked(event) }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
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
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
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
     * Refleja el estado del ViewModel en la UI.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private suspend fun observeState(binding: FragmentCalendarBinding, adapter: CalendarAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.items)
            binding.swipeRefreshCalendar.isRefreshing = state.isLoading
            binding.emptyStateCalendar.isVisible =
                !state.isLoading && state.items.isEmpty()
            binding.rvCalendar.isVisible = state.items.isNotEmpty()
        }
    }

    /**
     * Consume los eventos de un solo uso.
     *
     * @param binding Binding de la vista (no usado directamente).
     */
    private suspend fun observeEvents() {
        viewModel.uiEvents.collect { event ->
            when (event) {
                CalendarUiEvent.NeedExactAlarmPermission -> openExactAlarmSettings()
                is CalendarUiEvent.ShowError -> showToast(event.messageResId)
            }
        }
    }

    /**
     * Abre los ajustes del sistema para solicitar el permiso de
     * alarmas exactas.
     *
     * ## Por qué se abre un Intent
     *
     * Porque Android no tiene un diálogo estándar para este
     * permiso. La única forma es abrir la pantalla de ajustes
     * correspondiente y que el usuario lo conceda manualmente.
     *
     * @param binding Binding de la vista (no usado directamente).
     */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${requireContext().packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(settingsIntent) }
            .onFailure { showToast(R.string.toast_exact_alarm_settings) }
    }

    /**
     * Maneja el click en un evento.
     *
     * ## Comportamiento
     *
     * - Si es una tarea (`"assign"`) y tiene curso e instancia,
     *   navega al detalle.
     * - Si no, muestra un toast genérico ("no hay detalles
     *   disponibles").
     *
     * @param event Evento pulsado.
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
     * Navega al detalle de una tarea.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo.
     */
    private fun openAssignment(courseId: Int, instanceId: Int) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                AssignmentDetailFragment.newInstance(courseId, instanceId),
            )
            addToBackStack(null)
        }
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