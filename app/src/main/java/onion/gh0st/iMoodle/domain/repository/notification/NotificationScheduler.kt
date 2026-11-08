package onion.gh0st.iMoodle.domain.repository.notification

import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent

/**
 * Resultado de intentar programar los recordatorios de tareas.
 *
 * ## Por qué existe este tipo
 *
 * Programar recordatorios puede fallar por varias razones, y la UI
 * necesita distinguirlas para reaccionar de forma distinta:
 *
 * - **`Success`**: todo ha ido bien. No hay que hacer nada.
 * - **`ExactAlarmDenied`**: el sistema exige el permiso
 *   `SCHEDULE_EXACT_ALARM` y no está concedido. La UI debe abrir los
 *   ajustes del sistema para que el usuario lo conceda.
 *
 * ## Por qué es un `sealed interface`
 *
 * Porque el conjunto de resultados posibles es fijo y conocido. Un
 * `sealed` permite hacer un `when` exhaustivo: si en el futuro se
 * añade un resultado nuevo (por ejemplo, `BatteryOptimizationBlocked`),
 * el compilador obligará a actualizar los `when` que no lo
 * contemplen. Así es imposible olvidarse de un caso.
 */
sealed interface ScheduleResult {

    /**
     * Todos los recordatorios se han programado correctamente.
     */
    data object Success : ScheduleResult

    /**
     * El sistema exige el permiso `SCHEDULE_EXACT_ALARM` y no está
     * concedido.
     *
     * ## Qué significa
     *
     * En Android 12 (API 31) y posteriores, las apps necesitan un
     * permiso especial para programar alarmas exactas. Sin él, las
     * alarmas pueden retrasarse minutos u horas. El sistema no
     * concede el permiso automáticamente: el usuario tiene que
     * hacerlo desde los ajustes.
     *
     * ## Qué debe hacer la UI
     *
     * Abrir los ajustes del sistema en la pantalla de "alarmas y
     * recordatorios" para que el usuario lo conceda. La app no puede
     * concederlo por su cuenta.
     */
    data object ExactAlarmDenied : ScheduleResult
}

/**
 * Contrato para programar los recordatorios de tareas.
 *
 * ## Qué es un recordatorio
 *
 * Es una notificación local que se programa para una fecha y hora
 * concretas, para avisar al usuario de que una tarea está próxima a
 * entregar. La app usa `AlarmManager` (a través de la implementación
 * `DefaultNotificationScheduler`) para programarlos.
 *
 * ## Por qué es un contrato de dominio
 *
 * Aunque los recordatorios suenen a algo muy técnico (alarmas,
 * notificaciones, `AlarmManager`), programarlos es una
 * responsabilidad del **dominio**: es una regla de negocio de la app
 * ("avisar al usuario cuando una tarea esté próxima"). La
 * implementación concreta (con `AlarmManager`, con
 * `WorkManager`, o incluso con un fake en tests) es un detalle de
 * infraestructura.
 *
 * Por eso el contrato vive en `domain/` y la implementación en
 * `notifications/`.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `DefaultNotificationScheduler` (usa
 *   `AlarmManager`).
 * - **Tests**: `FakeNotificationScheduler` (cuenta las llamadas, sin
 *   programar nada real).
 *
 * ## Quién la consume
 *
 * - `SyncReminders` (caso de uso).
 * - `CalendarViewModel`.
 * - `MainViewModel` (al abrir la app).
 * - `DefaultSessionRepository` (al hacer logout, para cancelar).
 */
interface NotificationScheduler {

    /**
     * Programa alarmas para los eventos de tipo `assign`.
     *
     * ## Qué hace
     *
     * 1. Si el permiso `SCHEDULE_EXACT_ALARM` no está concedido,
     *    devuelve [ScheduleResult.ExactAlarmDenied] sin programar
     *    nada.
     * 2. Si está concedido:
     *    - Cancela las alarmas anteriores.
     *    - Programa alarmas nuevas para cada evento de tipo `assign`.
     *    - Devuelve [ScheduleResult.Success].
     *
     * ## Qué se hace con los eventos que no son `assign`
     *
     * Se ignoran. Solo las tareas generan recordatorios. Un examen,
     * un festivo o un evento genérico no generan alarma.
     *
     * ## Cuándo se llama
     *
     * - Al abrir la app (desde `MainViewModel`).
     * - Al abrir la agenda (desde `CalendarViewModel`).
     *
     * @param events Eventos de calendario a procesar.
     * @return El resultado de la programación.
     */
    suspend fun schedule(events: List<CalendarEvent>): ScheduleResult

    /**
     * Cancela todas las alarmas programadas y limpia el
     * almacenamiento persistente.
     *
     * ## Cuándo se llama
     *
     * - Al hacer logout: las alarmas del usuario anterior no deben
     *   seguir sonando.
     * - Al invalidar la sesión.
     *
     * ## Qué NO hace
     *
     * No borra las notificaciones ya mostradas (eso lo gestiona el
     * sistema de notificaciones de Android, no la app).
     */
    suspend fun cancelAll()
}