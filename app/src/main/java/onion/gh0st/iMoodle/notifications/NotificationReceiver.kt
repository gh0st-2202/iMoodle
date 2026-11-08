package onion.gh0st.iMoodle.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.DeepLinkExtras
import onion.gh0st.iMoodle.ui.main.MainActivity

/**
 * Receiver que construye y muestra la notificación de recordatorio
 * de tarea.
 *
 * ## Cuándo se ejecuta
 *
 * Android lo invoca cuando una alarma programada con
 * `DefaultNotificationScheduler` se dispara. El `Intent` que recibe
 * contiene los datos de la tarea (ID, nombre, curso, instancia) que
 * se pusieron al programar la alarma.
 *
 * ## Qué hace
 *
 * 1. Lee el ID y el nombre de la tarea del `Intent`. Si falta el
 *    ID, no hace nada (situación anómala).
 * 2. Asegura que el canal de notificaciones existe (necesario desde
 *    Android 8).
 * 3. Construye la notificación:
 *    - Título: "Tarea próxima a entregar".
 *    - Texto: nombre de la tarea.
 *    - Icono: pequeño (obligatorio).
 *    - `PendingIntent`: al pulsar, abre `MainActivity` con un
 *      deep-link a la tarea.
 * 4. La muestra con el `NotificationManager`.
 *
 * ## Sobre el `PendingIntent` de contenido
 *
 * Cuando el usuario pulsa la notificación, Android dispara un
 * `PendingIntent`. En este caso, el `PendingIntent` abre
 * `MainActivity` con dos extras: el ID del curso y el ID de la
 * instancia del módulo. Esos extras son los que `MainActivity`
 * necesita para abrir la tarea concreta.
 *
 * La clave es que el `Intent` de contenido **no** es el mismo que el
 * `Intent` de la alarma. El de la alarma va a este receiver; el de
 * contenido va a `MainActivity`. Este último se construye aquí, en
 * el receiver, a partir de los extras del de la alarma.
 *
 * ## Notas sobre seguridad
 *
 * - No se loguean los datos de la tarea (nombre, IDs). No son
 *   sensibles, pero por consistencia con el resto de la app.
 * - El `PendingIntent` de contenido usa `FLAG_IMMUTABLE`, que es
 *   obligatorio a partir de Android 12.
 */
class NotificationReceiver : BroadcastReceiver() {

    /**
     * Punto de entrada del receiver.
     *
     * ## Flujo
     *
     * 1. Lee `EXTRA_TASK_ID`. Si es 0, no hace nada y sale.
     * 2. Lee `EXTRA_TASK_NAME`. Si no está, usa un nombre genérico
     *    ("Recordatorio de tarea") para que la notificación no quede
     *    vacía.
     * 3. Asegura que el canal existe.
     * 4. Muestra la notificación.
     *
     * @param context Contexto del receiver.
     * @param intent `Intent` de la alarma.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(NotificationContract.EXTRA_TASK_ID, 0)
        if (taskId == 0) return
        val taskName = intent.getStringExtra(NotificationContract.EXTRA_TASK_NAME)
            ?: context.getString(R.string.notification_default_task_name)
        ensureChannel(context)
        notifyTask(context, taskId, taskName, intent)
    }

    /**
     * Crea el canal de notificaciones si no existe.
     *
     * ## Qué es un canal
     *
     * En Android 8 (API 26) y posteriores, todas las notificaciones
     * deben pertenecer a un canal. Un canal es una categoría que el
     * usuario puede configurar por separado (sonido, importancia,
     * etc.). Crear el canal es idempotente: si ya existe, no hace
     * nada.
     *
     * ## Importancia del canal
     *
     * Se usa `IMPORTANCE_HIGH`, que hace que la notificación:
     *
     * - Suene (si el usuario no la ha silenciado).
     * - Aparezca como "heads-up" (un banner flotante en la parte
     *   superior de la pantalla).
     * - Se muestre en la pantalla de bloqueo.
     *
     * Es apropiado para recordatorios de tareas: el usuario quiere
     * enterarse.
     *
     * ## Por qué no se comprueba la versión de Android
     *
     * Porque `minSdk` del proyecto es 28, y los canales existen
     * desde API 26. No hace falta comprobar.
     *
     * @param context Contexto del receiver.
     */
    private fun ensureChannel(context: Context) {
        notificationManager(context).createNotificationChannel(
            NotificationChannel(
                NotificationContract.CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * Construye y muestra la notificación de una tarea.
     *
     * ## Qué incluye
     *
     * - **Icono pequeño**: obligatorio en todas las notificaciones.
     *   Se usa `R.drawable.ic_courses`.
     * - **Título**: "Tarea próxima a entregar".
     * - **Texto**: nombre de la tarea.
     * - **Prioridad**: `PRIORITY_HIGH` (en desuso en API 26+, pero se
     *   mantiene por compatibilidad con versiones anteriores).
     * - **`PendingIntent` de contenido**: abre la tarea en
     *   `MainActivity`.
     * - **`AutoCancel`**: la notificación desaparece al pulsarla.
     *
     * ## Por qué se usa `notify(taskId, notification)`
     *
     * Porque el `taskId` se usa como identificador de la
     * notificación. Si ya hay una notificación con ese ID, se
     * actualiza en lugar de crear una nueva. Así, si una tarea se
     * reprograma varias veces, no se acumulan notificaciones.
     *
     * @param context Contexto del receiver.
     * @param taskId ID de la tarea (identificador de la notificación).
     * @param taskName Nombre visible de la tarea.
     * @param intent `Intent` original de la alarma (para copiar los
     *   extras de deep-link).
     */
    private fun notifyTask(
        context: Context,
        taskId: Int,
        taskName: String,
        intent: Intent,
    ) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationContract.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_courses)
            .setContentTitle(context.getString(R.string.notification_title_due_soon))
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildContentIntent(context, taskId, intent))
            .setAutoCancel(true)
            .build()
        notificationManager(context).notify(taskId, notification)
    }

    /**
     * Construye el `PendingIntent` que abre la app al pulsar la
     * notificación.
     *
     * ## Qué hace
     *
     * 1. Crea un `Intent` que abre `MainActivity`.
     * 2. Le añade los extras de deep-link (curso e instancia),
     *    copiándolos del `Intent` original de la alarma.
     * 3. Lo envuelve en un `PendingIntent`.
     *
     * ## Flags del `Intent`
     *
     * - **`FLAG_ACTIVITY_NEW_TASK`**: necesario porque el
     *   `PendingIntent` se dispara desde fuera de una Activity.
     * - **`FLAG_ACTIVITY_CLEAR_TOP`**: si `MainActivity` ya está en
     *   la pila, se reutiliza en lugar de crear una nueva. Evita
     *   apilar Activities.
     *
     * ## Flags del `PendingIntent`
     *
     * - **`FLAG_IMMUTABLE`**: obligatorio desde Android 12.
     * - **`FLAG_UPDATE_CURRENT`**: si ya existe un `PendingIntent`
     *   con el mismo `requestCode`, se actualiza con los nuevos
     *   extras.
     *
     * @param context Contexto del receiver.
     * @param taskId ID de la tarea (clave del `PendingIntent`).
     * @param source `Intent` original de la alarma.
     * @return El `PendingIntent` hacia `MainActivity`.
     */
    private fun buildContentIntent(
        context: Context,
        taskId: Int,
        source: Intent,
    ): PendingIntent {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                DeepLinkExtras.COURSE_ID,
                source.getIntExtra(NotificationContract.EXTRA_COURSE_ID, 0),
            )
            putExtra(
                DeepLinkExtras.INSTANCE_ID,
                source.getIntExtra(NotificationContract.EXTRA_INSTANCE_ID, 0),
            )
        }
        return PendingIntent.getActivity(
            context,
            taskId,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Devuelve el `NotificationManager` del sistema.
     *
     * @param context Contexto del receiver.
     * @return El `NotificationManager`.
     */
    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}