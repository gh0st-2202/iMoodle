package onion.gh0st.iMoodle.ui.calendar

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import onion.gh0st.iMoodle.fakes.FakeCalendarRepository
import onion.gh0st.iMoodle.fakes.FakeNotificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

/**
 * Tests de [CalendarViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeCalendarRepository()
    private val scheduler = FakeNotificationScheduler()

    private fun createViewModel(): CalendarViewModel =
        CalendarViewModel(fakeRepo, scheduler)

    @Test
    fun `empty flow leaves items empty and schedules empty list`() = runTest {
        fakeRepo.eventsFlow = flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, scheduler.scheduleCalls)
        assertTrue(scheduler.lastScheduledEvents.isEmpty())
    }

    @Test
    fun `events flow groups items and schedules alarms`() = runTest {
        fakeRepo.eventsFlow = flowOf(twoEvents())

        val vm = createViewModel()
        advanceUntilIdle()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertTrue(items.any { it is CalendarItem.Event })
        }
        assertEquals(1, scheduler.scheduleCalls)
        assertEquals(2, scheduler.lastScheduledEvents.size)
    }

    @Test
    fun `refresh error emits error event`() = runTest {
        fakeRepo.eventsFlow = flowOf(emptyList())
        fakeRepo.refreshError = RuntimeException("boom")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.uiEvents.test {
            val event = awaitItem() as CalendarUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exact alarm denied emits ui event`() = runTest {
        fakeRepo.eventsFlow = flowOf(twoEvents())
        scheduler.result = ScheduleResult.ExactAlarmDenied

        val vm = createViewModel()

        vm.uiEvents.test {
            assertEquals(CalendarUiEvent.NeedExactAlarmPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exact alarm prompt is emitted only once across several emissions`() = runTest {
        fakeRepo.eventsFlow = flowOf(twoEvents(), twoEventsOnDifferentDays())
        scheduler.result = ScheduleResult.ExactAlarmDenied

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("Debe reprogramar en cada emisión", 2, scheduler.scheduleCalls)
        vm.uiEvents.test {
            assertEquals(CalendarUiEvent.NeedExactAlarmPermission, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `events on different days produce separate headers`() = runTest {
        fakeRepo.eventsFlow = flowOf(twoEventsOnDifferentDays())

        val vm = createViewModel()
        advanceUntilIdle()

        val headers = vm.state.value.items.filterIsInstance<CalendarItem.Header>()
        assertEquals(2, headers.size)
    }

    @Test
    fun `init triggers refresh`() = runTest {
        fakeRepo.eventsFlow = flowOf(emptyList())

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCalls)
    }

    @Test
    fun `refresh triggers another sync`() = runTest {
        fakeRepo.eventsFlow = flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.refreshCalls)
    }

    private fun twoEvents(): List<CalendarEvent> {
        val base = todayAtNoonSeconds()
        return listOf(
            event(id = 1, name = "Tarea 1", at = base),
            event(id = 2, name = "Tarea 2", at = base + 3600),
        )
    }

    private fun twoEventsOnDifferentDays(): List<CalendarEvent> {
        val today = todayAtNoonSeconds()
        return listOf(
            event(id = 1, name = "Hoy", at = today),
            event(id = 2, name = "Mañana", at = today + 86_400),
        )
    }

    private fun todayAtNoonSeconds(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis / 1000

    private fun event(id: Int, name: String, at: Long) = CalendarEvent(
        id = id,
        name = name,
        description = "",
        timestart = at,
        timeduration = null,
        courseId = 1,
        courseName = "Curso",
        eventType = "assign",
        instanceId = 1,
    )
}