package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import onion.gh0st.iMoodle.data.local.dao.CalendarDao
import onion.gh0st.iMoodle.data.local.entity.CalendarEventEntity

/**
 * Implementación en memoria de [CalendarDao] para tests.
 *
 * Sustituye a Room en los tests unitarios. Expone [events] como
 * snapshot para asserts.
 */
class FakeCalendarDao : CalendarDao {

    /** Flujo interno con el estado actual de los eventos. */
    private val eventsFlow = MutableStateFlow<List<CalendarEventEntity>>(emptyList())

    /** Snapshot de los eventos insertados, útil para asserts. */
    val events: List<CalendarEventEntity> get() = eventsFlow.value

    /**
     * Observa los eventos dentro de un rango temporal.
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     * @return `Flow` filtrado y ordenado por `timestart`.
     */
    override fun getEvents(startTime: Long, endTime: Long): Flow<List<CalendarEventEntity>> =
        eventsFlow.asStateFlow().map { entities ->
            entities
                .filter { it.timestart in startTime..endTime }
                .sortedBy { it.timestart }
        }

    /**
     * Reemplaza el contenido por el lote indicado.
     *
     * @param events Eventos a guardar.
     */
    override suspend fun insertEvents(events: List<CalendarEventEntity>) {
        eventsFlow.value = events
    }

    /**
     * Borra todos los eventos.
     */
    override suspend fun clearAll() {
        eventsFlow.value = emptyList()
    }
}