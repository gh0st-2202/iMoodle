package onion.gh0st.iMoodle.domain.repository.calendar

import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent

/**
 * Contrato de acceso a los eventos del calendario.
 *
 * ## Estrategia offline-first
 *
 * Este contrato sigue el patrón **offline-first** (primero offline):
 *
 * - La UI **nunca** lee directamente de la red. Lee de la base de
 *   datos local, que es rápida y no falla.
 * - La red se usa para **actualizar** esa base de datos.
 *
 * Por eso la interfaz expone dos métodos separados:
 *
 * 1. [getEventsFlow]: observa la base de datos local. La UI lo usa
 *    para renderizar.
 * 2. [refreshEvents]: va a la red y actualiza la base de datos.
 *
 * Gracias a esto, la agenda se abre al instante aunque no haya
 * conexión, y los datos que el usuario ya había visto siguen ahí
 * hasta que se sincronicen.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleCalendarRepository` (con Room + red).
 * - **Tests**: `FakeCalendarRepository` (devuelve datos controlados).
 *
 * ## Quién la consume
 *
 * - El caso de uso `SyncReminders`.
 * - `CalendarViewModel`.
 */
interface CalendarRepository {

    /**
     * Observa los eventos del calendario dentro de un rango temporal.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de eventos cada vez que
     * cambia la base de datos. La UI lo recoge con `collect` y se
     * actualiza automáticamente.
     *
     * ## Por qué un `Flow` y no una función `suspend`
     *
     * Porque un `Flow` es **reactivo**: emite no solo el valor
     * actual, sino todos los valores futuros hasta que se cancele.
     * Eso permite a la UI suscribirse una vez y olvidarse: cada vez
     * que cambien los eventos, la UI se entera y se actualiza.
     *
     * @param startTime Inicio del rango en **segundos** (epoch).
     * @param endTime Fin del rango en **segundos** (epoch).
     * @return `Flow` que emite la lista de eventos.
     */
    fun getEventsFlow(startTime: Long, endTime: Long): Flow<List<CalendarEvent>>

    /**
     * Sincroniza los eventos del rango desde la red y actualiza la
     * base de datos local.
     *
     * ## Qué hace
     *
     * - Si la red va bien, actualiza Room con los eventos nuevos. El
     *   `Flow` de [getEventsFlow] notificará a la UI.
     * - Si la red falla, **no toca Room**. Los eventos anteriores se
     *   mantienen. El `Flow` sigue emitiendo lo que ya había.
     *
     * ## Cuándo se llama
     *
     * - Al abrir la agenda.
     * - Al hacer pull-to-refresh.
     * - Al abrir la app (desde `SyncReminders`).
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     */
    suspend fun refreshEvents(startTime: Long, endTime: Long)
}