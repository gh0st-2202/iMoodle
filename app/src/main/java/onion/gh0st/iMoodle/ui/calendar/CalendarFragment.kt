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
 */
@AndroidEntryPoint
class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private val viewModel: CalendarViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onPermissionResult(granted) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCalendarBinding.bind(view)
        setupWindowInsets(binding)
        ensureNotificationPermission(savedInstanceState)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

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

    private fun ensureNotificationPermission(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionResult(granted: Boolean) {
        if (granted) showToast(R.string.toast_notifications_enabled)
        else showToast(R.string.toast_no_notifications_permission_short)
    }

    private fun buildAdapter() = CalendarAdapter { event -> onEventClicked(event) }

    private fun setupRecyclerView(
        binding: FragmentCalendarBinding,
        adapter: CalendarAdapter,
    ) {
        binding.rvCalendar.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCalendar.adapter = adapter
        binding.swipeRefreshCalendar.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeUi(binding: FragmentCalendarBinding, adapter: CalendarAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    private suspend fun observeState(binding: FragmentCalendarBinding, adapter: CalendarAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.items)
            binding.swipeRefreshCalendar.isRefreshing = state.isLoading
            binding.emptyStateCalendar.isVisible = !state.isLoading && state.items.isEmpty()
            binding.rvCalendar.isVisible = state.items.isNotEmpty()
        }
    }

    private suspend fun observeEvents() {
        viewModel.uiEvents.collect { event ->
            when (event) {
                CalendarUiEvent.NeedExactAlarmPermission -> openExactAlarmSettings()
                is CalendarUiEvent.ShowError -> showToast(event.messageResId)
            }
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${requireContext().packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(settingsIntent) }
            .onFailure { showToast(R.string.toast_exact_alarm_settings) }
    }

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
     * ## Corrección (v2.2)
     * Se usa `R.id.detail_container` en lugar de `R.id.fragment_container`
     * porque el layout principal ahora usa un `ViewPager2` para las
     * pestañas y un contenedor de detalles superpuesto.
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

    private fun showToast(@StringRes messageResId: Int) {
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
    }
}