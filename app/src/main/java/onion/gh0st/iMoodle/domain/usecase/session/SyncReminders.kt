package onion.gh0st.iMoodle.domain.usecase.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import javax.inject.Inject

/**
 * Caso de uso: sincronizar el calendario y reprogramar los
 * recordatorios.
 *
 * ## Qué hace
 *
 * 1. Refresca el calendario de los próximos 30 días desde Moodle.
 * 2. Lee los eventos resultantes de la base de datos local.
 * 3. Pide al `NotificationScheduler` que reprograme las alarmas para
 *    los eventos de tipo `assign` (tareas).
 *
 * ## Cuándo se llama
 *
 * Al abrir `MainActivity`. Así las alarmas se refrescan en cada
 * apertura de la app, no solo al visitar la Agenda. Es importante
 * porque:
 *
 * - Si el usuario no abre la Agenda en días, las alarmas seguirían
 *   programadas con datos viejos.
 * - Si el usuario ha entregado una tarea y no la ha visto en la
 *   Agenda, la alarma no se reprogramaría para esa tarea, pero sí
 *   para las nuevas.
 *
 * ## Manejo de errores del refresco
 *
 * Si el refresco de la red falla (por ejemplo, sin conexión), **no
 * es un error**: se programa con lo que haya en la base de datos
 * local. El usuario ya tendrá la UI informada del fallo por su
 * propio `CalendarViewModel`; aquí solo nos importa que las alarmas
 * estén coherentes con los datos locales.
 *
 * ## Por qué no propaga errores de red
 *
 * Porque este caso de uso se ejecuta al abrir la app, en background.
 * Si lanzara una excepción porque no hay red, la app tendría que
 * manejarla y mostrar algo, lo cual sería ruido innecesario. Es
 * mejor seguir con los datos locales.
 *
 * ## Quién lo usa
 *
 * - `MainViewModel` (al abrir `MainActivity`).
 *
 * ## Relación con `CalendarViewModel`
 *
 * `CalendarViewModel` también programa alarmas cuando el usuario
 * abre la Agenda. Eso es redundante con este caso de uso, pero
 * intencionado: si el usuario está viendo la Agenda y añade una
 * tarea en otro dispositivo, las alarmas se reprograman al
 * refrescar. El `DefaultNotificationScheduler` está preparado para
 * ser invocado varias veces seguidas sin problema (cancela las
 * anteriores y reprograma).
 *
 * @param calendarRepository Fuente de eventos del calendario.
 * @param notificationScheduler Programador de alarmas.
 */
class SyncReminders @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val notificationScheduler: NotificationScheduler,
) {

    /**
     * Ejecuta el refresco y la reprogramación.
     *
     * ## Qué hace paso a paso
     *
     * 1. Calcula el rango de 30 días desde ahora.
     * 2. Pide al repositorio que refresque los eventos del rango.
     *    Si falla (red), se ignora: se seguirá con los datos locales.
     * 3. Lee los eventos resultantes de la base de datos local (con
     *    `first()`, que espera al primer valor emitido por el
     *    `Flow`).
     * 4. Pide al scheduler que programe las alarmas para esos
     *    eventos.
     * 5. Devuelve el resultado de la programación.
     *
     * ## Manejo de cancelación
     *
     * Si la corrutina se cancela mientras el refresco está en curso,
     * la excepción de cancelación se propaga. Es lo correcto: no
     * queremos seguir programando alarmas si la app se está
     * cerrando.
     *
     * @return El resultado de programar las alarmas:
     *   `ScheduleResult.Success` o `ScheduleResult.ExactAlarmDenied`
     *   si falta el permiso.
     */
    suspend operator fun invoke(): ScheduleResult {
        val startTime = System.currentTimeMillis() / 1000
        val endTime = startTime + THIRTY_DAYS_SECONDS
        try {
            calendarRepository.refreshEvents(startTime, endTime)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
        }
        val events = calendarRepository.getEventsFlow(startTime, endTime).first()
        return notificationScheduler.schedule(events)
    }

    private companion object {
        /** Treinta días en segundos. */
        const val THIRTY_DAYS_SECONDS = 30L * 24 * 60 * 60
    }
}