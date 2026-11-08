package onion.gh0st.iMoodle.notifications

/**
 * Contrato compartido entre los componentes de notificación.
 *
 * ## Qué es esto
 *
 * Es un conjunto de constantes que usan varios componentes del
 * sistema de notificaciones. Centralizarlas aquí tiene tres
 * ventajas:
 *
 * 1. **Consistencia**: todos los componentes usan los mismos valores.
 *    Si una constante se cambia, se cambia en un solo sitio.
 * 2. **Descubrimiento**: al leer este fichero, queda claro qué
 *    canales, tipos de eventos y claves de extras existen.
 * 3. **Evita typos**: si dos componentes usan la misma clave pero
 *    con un typo (por ejemplo, `EXTRA_TASK_ID` en uno y
 *    `EXTRA_TASKS_ID` en otro), los `Intent` no se comunicarían. Con
 *    constantes, el compilador lo detecta.
 *
 * ## Quién usa estas constantes
 *
 * - `DefaultNotificationScheduler`: al construir los `PendingIntent`.
 * - `NotificationReceiver`: al leer los extras del `Intent`.
 * - `AlarmStorage`: al serializar las alarmas.
 * - `NotificationReceiver.ensureChannel`: al crear el canal.
 *
 * ## Regla
 *
 * Si añades una nueva constante de notificación (nuevo canal, nuevo
 * tipo de evento, nueva clave de extra), va aquí. No duplicar en
 * cada clase.
 */
internal object NotificationContract {

    /**
     * ID del canal de notificaciones de recordatorios de tareas.
     *
     * ## Qué es un canal de notificaciones
     *
     * En Android 8 (API 26) y posteriores, todas las notificaciones
     * deben pertenecer a un "canal". Un canal es una categoría que
     * el usuario puede configurar por separado: puede silenciar un
     * canal sin afectar a otros, puede cambiar el sonido, la
     * importancia, etc.
     *
     * Este canal agrupa las notificaciones de "recordatorio de
     * tarea". Si en el futuro se añaden otros tipos de
     * notificaciones (por ejemplo, "mensajes del foro"), se crean
     * canales separados para que el usuario pueda elegir.
     */
    const val CHANNEL_ID = "task_reminders_channel"

    /**
     * Tipo de evento del calendario que genera recordatorios.
     *
     * ## Por qué solo `assign`
     *
     * Porque la app solo avisa de tareas próximas a entregar. Los
     * exámenes, festivos y otros tipos de eventos aparecen en la
     * agenda, pero no generan alarma. Si en el futuro se quiere
     * avisar también de exámenes, se añade otro tipo aquí y se
     * ajusta el scheduler.
     */
    const val ASSIGN_TYPE = "assign"

    /**
     * Prefijo de la acción del `Intent` que identifica la alarma.
     *
     * ## Cómo se usa
     *
     * Cada alarma tiene una `action` única que la distingue de las
     * demás. La acción se compone de este prefijo más el ID de la
     * tarea:
     *
     * ```
     * "com.gh0st.iMoodle.TASK_REMINDER_42"
     * ```
     *
     * Esto es importante porque Android identifica los
     * `PendingIntent` por su `action` (entre otras cosas). Si dos
     * alarmas tuvieran la misma `action`, se pisarían entre sí: la
     * segunda sustituiría a la primera.
     */
    const val TASK_REMINDER_PREFIX = "com.gh0st.iMoodle.TASK_REMINDER_"

    /**
     * Clave del extra con el ID de la tarea.
     *
     * Se envía en el `Intent` de la alarma y se lee en
     * `NotificationReceiver` para construir la notificación.
     */
    const val EXTRA_TASK_ID = "EXTRA_TASK_ID"

    /**
     * Clave del extra con el nombre visible de la tarea.
     *
     * Se usa como texto de la notificación.
     */
    const val EXTRA_TASK_NAME = "EXTRA_TASK_NAME"

    /**
     * Clave del extra con el ID del curso asociado.
     *
     * Se propaga desde la alarma hasta la notificación, y de ahí al
     * `Intent` del `MainActivity` cuando el usuario pulsa la
     * notificación. Permite abrir la tarea concreta al pulsar.
     */
    const val EXTRA_COURSE_ID = "EXTRA_COURSE_ID"

    /**
     * Clave del extra con el ID de instancia del módulo `assign`.
     *
     * Se propaga junto con `EXTRA_COURSE_ID` para poder abrir la
     * tarea en el detalle.
     */
    const val EXTRA_INSTANCE_ID = "EXTRA_INSTANCE_ID"
}