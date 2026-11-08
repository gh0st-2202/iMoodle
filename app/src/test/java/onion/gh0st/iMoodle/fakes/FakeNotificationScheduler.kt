package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult

/**
 * Implementación en memoria de [NotificationScheduler] para tests.
 *
 * Registra las llamadas y permite configurar el [ScheduleResult] que
 * devolverá [schedule].
 *
 * @property result Resultado a devolver desde [schedule].
 */
class FakeNotificationScheduler(
    var result: ScheduleResult = ScheduleResult.Success,
) : NotificationScheduler {

    /** Número de veces que se ha llamado a [schedule]. */
    var scheduleCalls: Int = 0
        private set

    /** Número de veces que se ha llamado a [cancelAll]. */
    var cancelAllCalls: Int = 0
        private set

    /** Últimos eventos recibidos por [schedule]. */
    var lastScheduledEvents: List<CalendarEvent> = emptyList()
        private set

    /**
     * Devuelve [result] y guarda los eventos.
     *
     * @param events Eventos recibidos.
     * @return [result].
     */
    override suspend fun schedule(events: List<CalendarEvent>): ScheduleResult {
        scheduleCalls++
        lastScheduledEvents = events
        return result
    }

    /**
     * Incrementa [cancelAllCalls]. No cancela nada real.
     */
    override suspend fun cancelAll() {
        cancelAllCalls++
    }
}