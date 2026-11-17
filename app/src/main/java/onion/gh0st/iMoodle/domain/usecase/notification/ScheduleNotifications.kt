package onion.gh0st.iMoodle.domain.usecase.notification

import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import javax.inject.Inject

/**
 * Caso de uso: programar las alarmas de las tareas próximas.
 *
 * ## Qué hace
 *
 * Entrega los eventos del calendario al programador de alarmas, que
 * decide cuáles están a punto de vencer y crea las alarmas locales.
 *
 * ## Por qué la Agenda lo necesita
 *
 * Porque al abrir la Agenda se refresca el calendario y puede haber
 * entregas nuevas. Sin volver a programar, esas tareas no avisarían
 * a tiempo hasta el siguiente arranque de la app.
 *
 * ## Permiso de alarmas exactas
 *
 * Este caso de uso **no** abre ajustes ni pide permisos: si falta el
 * permiso, el programador devuelve `ExactAlarmDenied` y no programa
 * nada. El único dueño de pedir el permiso es `MainViewModel`.
 *
 * @param notificationScheduler Programador de alarmas del sistema.
 */
class ScheduleNotifications @Inject constructor(
    private val notificationScheduler: NotificationScheduler,
) {

    /**
     * Programa las alarmas para los eventos indicados.
     *
     * @param events Eventos del rango visible.
     * @return Resultado de la programación (`Scheduled` o
     *   `ExactAlarmDenied`).
     */
    suspend operator fun invoke(events: List<CalendarEvent>) =
        notificationScheduler.schedule(events)
}
