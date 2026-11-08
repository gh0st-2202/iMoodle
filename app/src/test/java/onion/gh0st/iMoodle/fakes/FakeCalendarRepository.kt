package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository

/**
 * Implementación en memoria de [CalendarRepository] para tests.
 */
class FakeCalendarRepository : CalendarRepository {

    /** Flow de eventos que observará el ViewModel. */
    var eventsFlow: Flow<List<CalendarEvent>> = flowOf(emptyList())

    /** Si es no-null, se lanza al invocar [refreshEvents]. */
    var refreshError: Throwable? = null

    /** Número de llamadas a [refreshEvents]. */
    var refreshCalls: Int = 0
        private set

    /**
     * Devuelve [eventsFlow].
     *
     * @param startTime Inicio del rango (ignorado por el fake).
     * @param endTime Fin del rango (ignorado por el fake).
     * @return El flujo configurado.
     */
    override fun getEventsFlow(startTime: Long, endTime: Long): Flow<List<CalendarEvent>> =
        eventsFlow

    /**
     * Incrementa [refreshCalls] o lanza [refreshError].
     *
     * @param startTime Inicio del rango (ignorado por el fake).
     * @param endTime Fin del rango (ignorado por el fake).
     * @throws Throwable si [refreshError] no es `null`.
     */
    override suspend fun refreshEvents(startTime: Long, endTime: Long) {
        refreshCalls++
        refreshError?.let { throw it }
    }
}