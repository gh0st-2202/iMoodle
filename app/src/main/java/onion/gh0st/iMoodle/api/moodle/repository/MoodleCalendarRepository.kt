package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.data.local.InlineTransactionRunner
import onion.gh0st.iMoodle.data.local.TransactionRunner
import onion.gh0st.iMoodle.data.local.dao.CalendarDao
import onion.gh0st.iMoodle.data.local.entity.CalendarEventEntity
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.rules.DueDateRules
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [CalendarRepository] con persistencia local
 * (Room).
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de sincronizar la **agenda** del usuario (los
 * eventos del calendario de Moodle: fechas de entrega de tareas,
 * exámenes anunciados, eventos del curso…) con la base de datos
 * local, y de exponer esos eventos a la UI de forma reactiva.
 *
 * ## Estrategia "offline-first"
 *
 * La app sigue un patrón llamado **offline-first** (primero offline).
 * La idea es que la pantalla de la agenda **nunca** depende
 * directamente de la red: siempre lee de la base de datos local. La
 * red solo se usa para **actualizar** esa base de datos. Así:
 *
 * - La agenda se abre al instante, aunque no haya conexión.
 * - Los datos que el usuario ya había visto siguen ahí.
 * - Cuando se sincroniza con Moodle, los cambios aparecen solos.
 *
 * Este repositorio implementa ese patrón con dos métodos:
 *
 * - [getEventsFlow]: expone un `Flow` que lee de Room. La UI lo
 *   observa y se actualiza sola cada vez que cambian los datos.
 * - [refreshEvents]: va a Moodle, descarga los eventos y los guarda
 *   en Room. El `Flow` se encarga de notificar a la UI.
 *
 * ## Sobre las transacciones
 *
 * El refresco hace dos operaciones sobre Room: borrar todos los
 * eventos anteriores e insertar los nuevos. Si esas dos operaciones
 * se hicieran por separado, la UI podría ver un instante con la
 * agenda vacía, y las alarmas programadas para eventos que ya no
 * existen podrían quedar huérfanas. Para evitarlo, las dos
 * operaciones van dentro de una **transacción**: o se hacen las dos,
 * o no se hace ninguna.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 * @param calendarDao DAO de eventos de Room.
 * @param transactionRunner Ejecutor de transacciones. Se inyecta como
 *   interfaz para poder sustituirlo por un fake en tests.
 */
@Singleton
class MoodleCalendarRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
    private val calendarDao: CalendarDao,
    private val transactionRunner: TransactionRunner = InlineTransactionRunner,
) : CalendarRepository {

    /**
     * Observa los eventos del calendario dentro de un rango temporal.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de eventos cada vez que
     * cambia algo en Room. La UI lo recoge con `collect` y se
     * actualiza automáticamente.
     *
     * ## Por qué se lee de Room y no de la red
     *
     * Porque Room es rápido y no falla. La red puede tardar, fallar o
     * no estar disponible. Si la UI leyera directamente de la red, la
     * pantalla de la agenda parpadearía y se rompería sin conexión.
     *
     * @param startTime Inicio del rango en **segundos** (epoch).
     * @param endTime Fin del rango en **segundos** (epoch).
     * @return `Flow` que emite la lista ordenada por `timestart`.
     */
    override fun getEventsFlow(
        startTime: Long,
        endTime: Long,
    ): Flow<List<CalendarEvent>> =
        calendarDao.getEvents(startTime, endTime).map { eventEntities ->
            eventEntities.map { eventEntity -> eventEntity.toDomainModel() }
        }

    /**
     * Sincroniza los eventos del rango desde Moodle y los guarda en
     * Room.
     *
     * ## Qué hace paso a paso
     *
     * 1. Descarga los eventos del rango desde Moodle.
     * 2. Ejecuta una transacción que:
     *    - Borra todos los eventos anteriores.
     *    - Inserta los nuevos.
     *
     * ## Por qué la red va fuera de la transacción
     *
     * Porque si la red falla, **no queremos tocar Room**. Si
     * borráramos primero y luego fallara la red, el usuario se
     * quedaría sin agenda. Dejando la red fuera, si falla, Room se
     * queda con los eventos anteriores y la app sigue mostrando lo
     * que ya tenía.
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     */
    override suspend fun refreshEvents(startTime: Long, endTime: Long) {
        val events = fetchFromNetwork(startTime, endTime)
        transactionRunner.run {
            calendarDao.clearAll()
            calendarDao.insertEvents(events.map { event -> event.toEntity() })
        }
    }

    // --- Obtención de datos de red ---

    /**
     * Descarga y parsea los eventos del rango desde Moodle.
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     * @return Lista de eventos de dominio.
     * @throws IllegalStateException si Moodle devuelve un JSON de
     *   error en lugar de la lista de eventos.
     */
    private suspend fun fetchFromNetwork(
        startTime: Long,
        endTime: Long,
    ): List<CalendarEvent> {
        val eventsResponseJson = fetchEventsJson(startTime, endTime)
        ensureNoMoodleError(eventsResponseJson)
        val eventsArray = eventsResponseJson.optJSONArray("events") ?: return emptyList()
        return (0 until eventsArray.length()).map { eventIndex ->
            parseEvent(eventsArray.getJSONObject(eventIndex))
        }
    }

    /**
     * Pide los eventos del rango a
     * `core_calendar_get_action_events_by_timesort`.
     *
     * ## Qué es esa función de Moodle
     *
     * Es la función que devuelve los "próximos eventos" del usuario:
     * entregas de tareas, exámenes, eventos del curso, etc., en un
     * rango temporal concreto. El parámetro `limitnum` limita cuántos
     * se piden de una vez para no sobrecargar la respuesta.
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     * @return `JSONObject` con el array `events`.
     */
    private suspend fun fetchEventsJson(
        startTime: Long,
        endTime: Long,
    ): JSONObject {
        val requestParams = MoodleParams().apply {
            put("timesortfrom", startTime)
            put("timesortto", endTime)
            put("limitnum", 50)
        }
        val rawJson = sessionApi.requireApi()
            .callREST("core_calendar_get_action_events_by_timesort", requestParams)
        return JSONObject(rawJson)
    }

    /**
     * Lanza una excepción si el JSON es un error de Moodle.
     *
     * ## Por qué hace falta esta comprobación
     *
     * Moodle a veces devuelve un error de negocio con `HTTP 200` y el
     * error dentro del JSON. Sin esta comprobación, el parseo
     * posterior fallaría con un mensaje críptico tipo "no such key
     * 'events'". Con esta comprobación, el error de Moodle se
     * propaga con un mensaje claro.
     *
     * @param eventsResponseJson Respuesta de Moodle.
     * @throws IllegalStateException si el JSON contiene una clave
     *   `exception`.
     */
    private fun ensureNoMoodleError(eventsResponseJson: JSONObject) {
        if (!eventsResponseJson.has("exception")) return
        val errorMessage = eventsResponseJson.optString("message", "Error desconocido")
        throw IllegalStateException(errorMessage)
    }

    /**
     * Convierte un evento JSON de Moodle en `CalendarEvent` de
     * dominio.
     *
     * ## Ajuste de "medianoche"
     *
     * Moodle suele poner la fecha límite de una tarea a las `00:00`
     * del día siguiente al que realmente quiere decir. Por ejemplo,
     * una tarea "para el viernes" se representa como `sábado 00:00`.
     * Semánticamente, eso significa "final del viernes".
     *
     * Para que el usuario vea la fecha intuitiva y la notificación
     * caiga en el día correcto, se aplica
     * `DueDateRules.adjustMidnight`, que convierte `00:00` en
     * `23:59` del día anterior.
     *
     * ## Sobre las unidades
     *
     * `timestart` se guarda en **segundos** (epoch), pero
     * `adjustMidnight` trabaja en milisegundos. Por eso se multiplica
     * por 1000 al entrar y se divide por 1000 al salir.
     *
     * @param eventJson `JSONObject` de un evento de Moodle.
     * @return Evento de dominio con `timestart` ajustado.
     */
    private fun parseEvent(eventJson: JSONObject): CalendarEvent {
        val courseJson = eventJson.optJSONObject("course")
        val rawStartSeconds = eventJson.optLong("timestart", 0L).takeIf { it > 0 }
            ?: eventJson.optLong("timesort", 0L)
        val adjustedStartSeconds =
            DueDateRules.adjustMidnight(rawStartSeconds * 1000L) / 1000L
        return CalendarEvent(
            id = eventJson.optInt("id"),
            name = eventJson.optString("name"),
            description = eventJson.optString("description", ""),
            timestart = adjustedStartSeconds,
            timeduration = null,
            courseId = courseJson?.optInt("id"),
            courseName = courseJson?.optString("fullname"),
            eventType = eventJson.optString("modulename", "event"),
            instanceId = eventJson.optInt("instance", 0).takeIf { it > 0 },
        )
    }

    // --- Mapeos entidad ↔ dominio ---

    /**
     * Convierte una entidad de Room al modelo de dominio.
     *
     * ## Por qué `timeduration` es `null`
     *
     * Porque la entidad no la persiste (no se usa en la UI actual).
     * Si en el futuro se necesita, se añade a la entidad y se mapea
     * aquí.
     *
     * @receiver Entidad de Room.
     * @return Evento de dominio.
     */
    private fun CalendarEventEntity.toDomainModel() = CalendarEvent(
        id = id,
        name = name,
        description = description,
        timestart = timestart,
        timeduration = null,
        courseId = courseId,
        courseName = courseName,
        eventType = eventType,
        instanceId = instanceId,
    )

    /**
     * Convierte un evento de dominio a su entidad de Room.
     *
     * @receiver Evento de dominio.
     * @return Entidad lista para Room.
     */
    private fun CalendarEvent.toEntity() = CalendarEventEntity(
        id = id,
        name = name,
        description = description,
        timestart = timestart,
        courseId = courseId,
        courseName = courseName,
        eventType = eventType,
        instanceId = instanceId,
    )
}