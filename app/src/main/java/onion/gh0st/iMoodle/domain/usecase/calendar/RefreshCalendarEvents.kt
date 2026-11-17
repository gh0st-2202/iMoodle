package onion.gh0st.iMoodle.domain.usecase.calendar

import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import javax.inject.Inject

/**
 * Caso de uso: refrescar los eventos del calendario desde Moodle.
 *
 * ## Qué hace
 *
 * Pide al repositorio que descargue los eventos del rango y los
 * guarde en la base local. La lista visible se actualiza sola porque
 * la pantalla observa `ObserveCalendarEvents`.
 *
 * ## Manejo de errores
 *
 * Propaga la excepción. El ViewModel la registra y emite un aviso
 * genérico; los eventos anteriores se mantienen.
 *
 * @param calendarRepository Contrato de acceso al calendario.
 */
class RefreshCalendarEvents @Inject constructor(
    private val calendarRepository: CalendarRepository,
) {

    /**
     * Descarga los eventos del rango.
     *
     * @param startTime Inicio del rango, en segundos Unix.
     * @param endTime Fin del rango, en segundos Unix.
     */
    suspend operator fun invoke(startTime: Long, endTime: Long) {
        calendarRepository.refreshEvents(startTime, endTime)
    }
}
