package onion.gh0st.iMoodle.ui.main

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import onion.gh0st.iMoodle.domain.repository.web.WebCookieRefresher
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSessionEvents
import onion.gh0st.iMoodle.domain.usecase.session.SyncReminders
import onion.gh0st.iMoodle.fakes.FakeCalendarRepository
import onion.gh0st.iMoodle.fakes.FakeNotificationScheduler
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [MainViewModel].
 *
 * ## Qué protegen
 *
 * - La sincronización inicial se lanza una sola vez por ViewModel.
 * - El permiso de alarmas exactas se pide una sola vez y desde un
 *   único dueño (este ViewModel).
 * - Al volver de los ajustes se puede reprogramar **sin** reabrir la
 *   pantalla de permiso, lo que evita el bucle ajustes → volver →
 *   ajustes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calendarRepo = FakeCalendarRepository()
    private val scheduler = FakeNotificationScheduler()
    private val sessionRepo = FakeSessionRepository(TestSessions.active())

    private fun createViewModel() = MainViewModel(
        syncReminders = SyncReminders(calendarRepo, scheduler),
        webCookieRefresher = object : WebCookieRefresher {
            override suspend fun refresh(force: Boolean): Boolean = true
        },
        observeSession = ObserveSession(sessionRepo),
        observeSessionEvents = ObserveSessionEvents(sessionRepo),
    )

    @Test
    fun `initial sync emits permission event when exact alarms are denied`() = runTest {
        scheduler.result = ScheduleResult.ExactAlarmDenied
        val vm = createViewModel()

        vm.syncRemindersOnce()
        advanceUntilIdle()

        vm.events.test {
            assertEquals(MainUiEvent.NeedExactAlarmPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial sync only runs once per view model`() = runTest {
        val vm = createViewModel()

        vm.syncRemindersOnce()
        vm.syncRemindersOnce()
        advanceUntilIdle()

        assertEquals(1, calendarRepo.refreshCalls)
        assertEquals(1, scheduler.scheduleCalls)
    }

    @Test
    fun `reschedule after the initial sync runs again`() = runTest {
        val vm = createViewModel()
        vm.syncRemindersOnce()
        advanceUntilIdle()

        vm.rescheduleReminders()
        advanceUntilIdle()

        assertEquals(2, calendarRepo.refreshCalls)
        assertEquals(2, scheduler.scheduleCalls)
    }

    @Test
    fun `reschedule without prompting does not reopen settings`() = runTest {
        scheduler.result = ScheduleResult.ExactAlarmDenied
        val vm = createViewModel()

        vm.rescheduleReminders(promptIfDenied = false)
        advanceUntilIdle()

        vm.events.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `granting the permission reschedules without emitting another event`() = runTest {
        scheduler.result = ScheduleResult.ExactAlarmDenied
        val vm = createViewModel()
        vm.syncRemindersOnce()
        advanceUntilIdle()
        vm.events.test {
            assertEquals(MainUiEvent.NeedExactAlarmPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        scheduler.result = ScheduleResult.Success
        vm.rescheduleReminders(promptIfDenied = false)
        advanceUntilIdle()

        assertEquals(2, scheduler.scheduleCalls)
        vm.events.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session invalidation emits SessionExpired`() = runTest {
        val vm = createViewModel()

        vm.events.test {
            advanceUntilIdle()
            sessionRepo.invalidate()
            advanceUntilIdle()
            assertEquals(MainUiEvent.SessionExpired, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transparent token refresh does not emit SessionExpired`() = runTest {
        val vm = createViewModel()

        vm.events.test {
            sessionRepo.emitEvent(SessionEvent.TokenRefreshed("nuevo-token"))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
