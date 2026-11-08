package onion.gh0st.iMoodle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.data.local.entity.CalendarEventEntity

/**
 * DAO de eventos del calendario (`calendar_events`).
 *
 * ## Diferencias con cursos
 *
 * A diferencia de los cursos, los eventos **no** están indexados por
 * usuario: la tabla se reemplaza entera en cada `refreshEvents`. Por
 * eso el logout borra la tabla completa (`clearAll`), evitando que un
 * usuario vea los eventos de otro en el mismo dispositivo.
 *
 * ## Unidades de tiempo
 *
 * Los timestamps (`startTime`, `endTime`) están en **segundos**
 * (epoch), igual que `CalendarEvent.timestart` en dominio. La UI
 * multiplica por 1000 al formatear.
 */
@Dao
interface CalendarDao {

    /**
     * Observa los eventos dentro de un rango temporal.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de eventos cada vez que
     * cambian los eventos que caen dentro del rango. La agenda se
     * actualiza sola tras cada sincronización.
     *
     * ## Orden
     *
     * Los eventos se devuelven ordenados por `timestart` ascendente,
     * que es lo que la agenda necesita para agrupar por día.
     *
     * @param startTime Inicio del rango en **segundos**.
     * @param endTime Fin del rango en **segundos**.
     * @return `Flow` que emite los eventos ordenados por `timestart`.
     */
    @Query(
        "SELECT * FROM calendar_events " +
                "WHERE timestart >= :startTime AND timestart <= :endTime " +
                "ORDER BY timestart ASC",
    )
    fun getEvents(startTime: Long, endTime: Long): Flow<List<CalendarEventEntity>>

    /**
     * Inserta o reemplaza un lote de eventos.
     *
     * ## Cómo se llama
     *
     * Se llama dentro de una transacción junto con [clearAll], para
     * que la UI no vea una lista vacía intermedia.
     *
     * ## Por qué `REPLACE`
     *
     * Porque un evento puede haber cambiado (nombre, fecha, etc.) y
     * queremos que la versión nueva sobreescriba a la antigua.
     *
     * @param events Lista de eventos a insertar o actualizar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    /**
     * Borra todos los eventos del calendario.
     *
     * ## Cuándo se llama
     *
     * - Antes de insertar la nueva lista en `refreshEvents`.
     * - En el logout (desde `LocalDataCleaner`).
     *
     * @throws Exception si la base de datos no está disponible.
     */
    @Query("DELETE FROM calendar_events")
    suspend fun clearAll()
}