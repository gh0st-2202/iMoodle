package onion.gh0st.iMoodle.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de `NotificationScheduler` basada en
 * `AlarmManager`.
 *
 * ## Qué es `AlarmManager`
 *
 * Es el servicio de Android que permite programar eventos para que
 * se ejecuten en un momento futuro, incluso si la app no está
 * corriendo. Es lo que usan las apps de alarma, calendario y
 * recordatorios.
 *
 * La app lo usa para programar alarmas que, cuando se disparan,
 * envían un `Intent` a un `BroadcastReceiver` (`NotificationReceiver`)
 * que muestra la notificación.
 *
 * ## Modo `setExactAndAllowWhileIdle`
 *
 * Hay varias formas de programar una alarma en `AlarmManager`. La
 * app usa `setExactAndAllowWhileIdle`, que tiene dos
 * características:
 *
 * 1. **Exacta**: se dispara en el instante indicado, no "en algún
 *    momento alrededor". Las alarmas no exactas pueden retrasarse
 *    minutos u horas.
 * 2. **Permitida en modo Doze**: cuando el móvil está inactivo
 *    (pantalla apagada, sin movimiento), Android entra en "modo
 *    Doze" y retrasa la mayoría de las alarmas para ahorrar batería.
 *    Las alarmas programadas con `setExactAndAllowWhileIdle` **sí**
 *    se disparan incluso en Doze.
 *
 * La contrapartida es que requiere el permiso
 * `SCHEDULE_EXACT_ALARM` (a partir de Android 12), que el usuario
 * tiene que conceder manualmente.
 *
 * ## Concurrencia
 *
 * `schedule` está serializado con un `Mutex`. El motivo es que se
 * llama desde dos sitios que pueden coincidir en el tiempo:
 *
 * - `CalendarViewModel` (al abrir la Agenda).
 * - `SyncReminders` (al abrir la app).
 *
 * Sin el mutex, dos pasadas concurrentes podrían leer un
 * `AlarmStorage` desactualizado, duplicar alarmas o pisarse el
 * `PendingIntent` de una misma tarea. Con el mutex, las pasadas se
 * serializan: una termina antes de que la otra empiece.
 *
 * ## Qué NO hace
 *
 * - **No muestra notificaciones**: eso lo hace
 *   `NotificationReceiver`.
 * - **No calcula el instante de disparo**: eso lo hace
 *   `TaskTriggerCalculator`.
 * - **No persiste los eventos**: eso lo hace `AlarmStorage`.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 */
@Singleton
class DefaultNotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationScheduler {

    /**
     * Servicio de alarmas del sistema. Se obtiene una sola vez al
     * crear la instancia.
     */
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Cerrojo que serializa las llamadas a [schedule].
     *
     * Ver la explicación de concurrencia en el KDoc de la clase.
     */
    private val scheduleMutex = Mutex()

    /**
     * Programa alarmas para los eventos de tipo `assign`.
     *
     * ## Qué hace paso a paso
     *
     * 1. Comprueba si el permiso `SCHEDULE_EXACT_ALARM` está
     *    concedido. Si no, devuelve `ExactAlarmDenied` sin tocar nada.
     * 2. Cancela las alarmas anteriores (leyéndolas de
     *    `AlarmStorage`).
     * 3. Guarda la nueva lista de eventos en `AlarmStorage`.
     * 4. Programa una alarma por cada evento de tipo `assign`, usando
     *    `TaskTriggerCalculator` para calcular el instante.
     *
     * ## Qué se hace con los eventos que no son `assign`
     *
     * Se ignoran. Solo las tareas generan alarma. Pero se guardan en
     * `AlarmStorage` igualmente, porque el `BootReceiver` los
     * necesita para reprogramar al reiniciar el móvil (aunque luego
     * filtre).
     *
     * @param events Eventos de calendario a procesar.
     * @return `Success` si todo ha ido bien, `ExactAlarmDenied` si
     *   falta el permiso.
     */
    override suspend fun schedule(events: List<CalendarEvent>): ScheduleResult =
        scheduleMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!canScheduleExactAlarms()) {
                    return@withContext ScheduleResult.ExactAlarmDenied
                }
                AlarmStorage.load(context).forEach { previousEvent ->
                    cancelOne(previousEvent)
                }
                AlarmStorage.save(context, events)
                events
                    .filter { event -> event.eventType == NotificationContract.ASSIGN_TYPE }
                    .forEach { event -> scheduleOne(event) }
                ScheduleResult.Success
            }
        }

    /**
     * Cancela todas las alarmas programadas y limpia el
     * almacenamiento persistente.
     *
     * ## Cuándo se llama
     *
     * - Al hacer logout.
     * - Al invalidar la sesión.
     *
     * ## Qué hace
     *
     * 1. Lee las alarmas de `AlarmStorage`.
     * 2. Cancela cada una del sistema.
     * 3. Limpia el almacenamiento.
     */
    override suspend fun cancelAll() = withContext(Dispatchers.IO) {
        AlarmStorage.load(context).forEach { event -> cancelOne(event) }
        AlarmStorage.clear(context)
    }

    // --- Comprobación de permisos ---

    /**
     * Comprueba si se pueden programar alarmas exactas.
     *
     * ## Diferencias por versión de Android
     *
     * - **Android 11 (API 30) y anteriores**: no se requiere permiso.
     *   Devuelve `true` sin más.
     * - **Android 12 (API 31) y posteriores**: se requiere el permiso
     *   `SCHEDULE_EXACT_ALARM`. El usuario puede concederlo o
     *   revocarlo en cualquier momento, así que hay que comprobarlo
     *   cada vez.
     *
     * @return `true` si se puede programar.
     */
    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    // --- Programación individual ---

    /**
     * Programa la alarma de una tarea concreta.
     *
     * ## Qué hace
     *
     * 1. Calcula el instante de disparo con
     *    `TaskTriggerCalculator`.
     * 2. Si el cálculo devuelve `null` (tarea vencida o nominal
     *    pasado), no programa nada.
     * 3. Si devuelve un instante, programa la alarma con
     *    `setExactAndAllowWhileIdle`.
     *
     * ## Sobre el tipo de alarma
     *
     * Se usa `RTC_WAKEUP`, que significa: "despierta el dispositivo
     * en el instante indicado, en tiempo real (no en tiempo relativo
     * desde el arranque)". Esto es importante para las alarmas
     * basadas en fechas: si se usara `ELAPSED_REALTIME`, el instante
     * sería relativo al arranque y podría desajustarse tras un
     * reinicio.
     *
     * @param event Evento a programar.
     */
    private fun scheduleOne(event: CalendarEvent) {
        val triggerAtMillis = TaskTriggerCalculator.calculate(
            dueDate = event.timestart * 1000,
            now = System.currentTimeMillis(),
        ) ?: return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            buildPendingIntent(event),
        )
    }

    /**
     * Cancela la alarma de una tarea concreta.
     *
     * ## Cómo funciona
     *
     * Para cancelar una alarma, `AlarmManager` necesita el **mismo**
     * `PendingIntent` con el que se programó (o uno equivalente). El
     * `PendingIntent` se identifica por su `requestCode` y su
     * `Intent` (que a su vez se identifica por su `action`). Por eso
     * se usa `FLAG_NO_CREATE`: si el `PendingIntent` no existe, no se
     * crea uno nuevo (no tendría sentido crear uno para cancelarlo).
     *
     * @param event Evento cuya alarma cancelar.
     */
    private fun cancelOne(event: CalendarEvent) {
        PendingIntent.getBroadcast(
            context,
            event.id,
            buildIntent(event),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )?.let { pendingIntent -> alarmManager.cancel(pendingIntent) }
    }

    /**
     * Construye el `PendingIntent` de una alarma.
     *
     * ## Flags usadas
     *
     * - **`FLAG_IMMUTABLE`**: obligatorio a partir de Android 12.
     *   Indica que el `PendingIntent` no puede ser modificado por
     *   otras apps.
     * - **`FLAG_UPDATE_CURRENT`**: si ya existe un `PendingIntent`
     *   con la misma `action` y `requestCode`, se actualiza con el
     *   nuevo `Intent` en lugar de crear uno nuevo. Esto es lo que
     *   permite reprogramar una alarma para la misma tarea con
     *   nuevos datos (por ejemplo, si la fecha cambió).
     *
     * @param event Evento a programar.
     * @return El `PendingIntent` listo.
     */
    private fun buildPendingIntent(event: CalendarEvent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            event.id,
            buildIntent(event),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Construye el `Intent` que disparará la alarma.
     *
     * ## Qué contiene
     *
     * - **`action`**: única por tarea, para que cada alarma sea
     *   independiente.
     * - **Extras**: los datos que `NotificationReceiver` necesita
     *   para construir la notificación.
     *
     * @param event Evento a programar.
     * @return El `Intent` con los datos del evento.
     */
    private fun buildIntent(event: CalendarEvent): Intent =
        Intent(context, NotificationReceiver::class.java).apply {
            action = "${NotificationContract.TASK_REMINDER_PREFIX}${event.id}"
            putExtra(NotificationContract.EXTRA_TASK_ID, event.id)
            putExtra(NotificationContract.EXTRA_TASK_NAME, event.name)
            putExtra(NotificationContract.EXTRA_COURSE_ID, event.courseId)
            putExtra(NotificationContract.EXTRA_INSTANCE_ID, event.instanceId)
        }
}