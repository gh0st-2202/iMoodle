package onion.gh0st.iMoodle.domain.usecase.calendar

import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import javax.inject.Inject

/**
 * Caso de uso: observar los eventos del calendario.
 *
 * ## Qué devuelve
 *
 * Un `Flow` con los eventos del rango, leídos de la base local. Room
 * emite una lista nueva cuando el refresco de red termina, así que la
 * pantalla se repinta sola.
 *
 * ## Por qué el rango llega como parámetros
 *
 * Porque la Agenda muestra los próximos 30 días desde que se abre la
 * pantalla. El ViewModel calcula el rango una vez y lo reutiliza al
 * refrescar.
 *
 * @param calendarRepository Contrato de acceso al calendario.
 */
class ObserveCalendarEvents @Inject constructor(
    private val calendarRepository: CalendarRepository,
) {

    /**
     * Observa los eventos del rango.
     *
     * @param startTime Inicio del rango, en segundos Unix.
     * @param endTime Fin del rango, en segundos Unix.
     * @return Flujo con los eventos (puede empezar vacío).
     */
    operator fun invoke(startTime: Long, endTime: Long): Flow<List<CalendarEvent>> =
        calendarRepository.getEventsFlow(startTime, endTime)
}
