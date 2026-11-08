package onion.gh0st.iMoodle.notifications

import android.content.Context
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Almacenamiento ligero de las alarmas programadas.
 *
 * ## Qué hace
 *
 * Guarda en `SharedPreferences` la lista de eventos para los que se
 * han programado alarmas. Así, si el dispositivo se reinicia (y
 * `AlarmManager` pierde las alarmas), se pueden reprogramar a partir
 * de esta lista.
 *
 * ## Qué se guarda y dónde
 *
 * Se guarda una lista de `CalendarEvent` serializada como JSON, en el
 * fichero de preferencias `scheduled_alarms`. Cada evento se
 * serializa con los campos mínimos necesarios para reprogramar:
 *
 * - `id`: ID del evento.
 * - `name`: nombre (para la notificación).
 * - `timestart`: cuándo empieza (para calcular el trigger).
 * - `courseId`: curso asociado (para el deep-link).
 * - `instanceId`: instancia del módulo (para el deep-link).
 * - `eventType`: tipo de evento (para filtrar `assign`).
 *
 * ## Por qué no se guarda el evento completo
 *
 * Porque muchos campos del `CalendarEvent` (descripción, nombre del
 * curso, duración) no se usan para reprogramar la alarma. Guardar
 * solo lo necesario ahorra espacio y evita persistir datos que
 * podrían quedar obsoletos.
 *
 * ## Sobre la sincronía
 *
 * Las operaciones de este objeto son **síncronas** (no son
 * `suspend`). El motivo es que se usan desde el `BootReceiver`, que
 * no puede suspender (su método `onReceive` debe terminar rápido).
 *
 * ## Manejo de datos corruptos
 *
 * Si el JSON guardado está corrupto (por ejemplo, porque una
 * escritura se interrumpió), la lectura se envuelve en
 * `runCatching`, devolviendo una lista vacía en lugar de lanzar una
 * excepción. Un JSON corrupto no debe tumbar el `BootReceiver` ni el
 * scheduler: el peor caso es perder las alarmas programadas, que se
 * reprogramarán la próxima vez que se abra la app.
 */
internal object AlarmStorage {

    /** Nombre del fichero de `SharedPreferences`. */
    private const val PREFERENCES_FILE_NAME = "scheduled_alarms"

    /** Clave bajo la que se guarda el array de alarmas. */
    private const val KEY_ALARMS = "alarms"

    /**
     * Guarda la lista completa de eventos programados.
     *
     * @param context Contexto para acceder a `SharedPreferences`.
     * @param events Eventos a persistir.
     */
    fun save(context: Context, events: List<CalendarEvent>) {
        val eventsArray = JSONArray()
        events.forEach { event -> eventsArray.put(event.toJsonObject()) }
        sharedPreferences(context)
            .edit()
            .putString(KEY_ALARMS, eventsArray.toString())
            .apply()
    }

    /**
     * Recupera los eventos persistidos.
     *
     * ## Manejo de datos corruptos
     *
     * Si el JSON está corrupto, devuelve una lista vacía. Ver la
     * explicación en el KDoc de la clase.
     *
     * @param context Contexto para acceder a `SharedPreferences`.
     * @return Lista de eventos (vacía si no había ninguno o si el
     *   JSON estaba corrupto).
     */
    fun load(context: Context): List<CalendarEvent> {
        val rawJson = sharedPreferences(context).getString(KEY_ALARMS, "[]") ?: "[]"
        return runCatching {
            val eventsArray = JSONArray(rawJson)
            (0 until eventsArray.length()).map { eventIndex ->
                eventsArray.getJSONObject(eventIndex).toCalendarEvent()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Borra la lista persistida.
     *
     * ## Qué NO hace
     *
     * No cancela las alarmas del sistema. Eso es responsabilidad de
     * `DefaultNotificationScheduler.cancelAll`, que llama a este
     * método después de cancelar las alarmas reales.
     *
     * @param context Contexto para acceder a `SharedPreferences`.
     */
    fun clear(context: Context) {
        sharedPreferences(context).edit().remove(KEY_ALARMS).apply()
    }

    /**
     * Devuelve las preferencias compartidas del módulo.
     *
     * @param context Contexto para acceder a `SharedPreferences`.
     * @return Las preferencias.
     */
    private fun sharedPreferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Serializa un `CalendarEvent` a JSON.
     *
     * @receiver Evento a serializar.
     * @return El `JSONObject` con los campos necesarios para
     *   reprogramar la alarma.
     */
    private fun CalendarEvent.toJsonObject() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("timestart", timestart)
        put("courseId", courseId)
        put("instanceId", instanceId)
        put("eventType", eventType)
    }

    /**
     * Deserializa un `CalendarEvent` desde JSON.
     *
     * ## Campos que se rellenan con valores por defecto
     *
     * Los campos que no se guardan (description, timeduration,
     * courseName) se rellenan con valores vacíos o `null`. No se usan
     * para reprogramar la alarma, así que da igual.
     *
     * @receiver `JSONObject` con los campos del evento.
     * @return El `CalendarEvent` reconstruido.
     */
    private fun JSONObject.toCalendarEvent() = CalendarEvent(
        id = getInt("id"),
        name = getString("name"),
        description = "",
        timestart = getLong("timestart"),
        timeduration = null,
        courseId = optInt("courseId", 0).takeIf { it > 0 },
        courseName = null,
        eventType = getString("eventType"),
        instanceId = optInt("instanceId", 0).takeIf { it > 0 },
    )
}