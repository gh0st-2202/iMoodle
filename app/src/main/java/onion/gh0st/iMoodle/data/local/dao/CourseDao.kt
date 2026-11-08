package onion.gh0st.iMoodle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.data.local.entity.CourseEntity
import onion.gh0st.iMoodle.data.local.relation.CourseWithAssignments

/**
 * DAO (Data Access Object) de cursos.
 *
 * ## Qué es un DAO
 *
 * Un DAO es una interfaz que declara las operaciones que la app puede
 * hacer sobre una tabla de la base de datos. Room genera la
 * implementación por detrás a partir de las anotaciones
 * (`@Query`, `@Insert`, etc.).
 *
 * Esta interfaz, en concreto, gestiona la tabla `courses`.
 *
 * ## Estrategia offline-first
 *
 * El DAO es la fuente de verdad para la pantalla de listado de
 * cursos. Expone un `Flow` que emite la lista cada vez que cambian
 * los datos, de forma que la UI se actualiza automáticamente tras
 * cada sincronización con la red.
 *
 * ## Sobre el borrado por usuario
 *
 * Cada curso pertenece a un usuario (`userId`). Al refrescar, se
 * borran todos los cursos de ese usuario y se insertan los nuevos.
 * Para que la UI no vea un instante con la lista vacía, ese borrado
 * y la inserción se hacen dentro de una transacción (en el
 * repositorio, con `TransactionRunner`).
 */
@Dao
interface CourseDao {

    /**
     * Observa los cursos de un usuario con sus tareas anidadas.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de cursos cada vez que
     * cambian los cursos o sus tareas asociadas. Room se encarga de
     * re-ejecutar la consulta cuando hay cambios relevantes.
     *
     * ## Por qué `@Transaction`
     *
     * La anotación `@Transaction` garantiza que la relación
     * (`CourseWithAssignments`) se resuelve de forma consistente
     * aunque haya escrituras concurrentes. Sin ella, la consulta del
     * curso y la de sus tareas podrían ejecutarse en momentos
     * distintos y devolver datos incoherentes.
     *
     * ## Orden
     *
     * Los cursos se devuelven ordenados por `fullname` ascendente,
     * para que la UI los muestre alfabéticamente sin tener que
     * ordenarlos ella.
     *
     * @param userId ID del usuario logueado.
     * @return `Flow` que emite la lista de cursos con sus tareas.
     */
    @Transaction
    @Query("SELECT * FROM courses WHERE userId = :userId ORDER BY fullname ASC")
    fun getCoursesWithAssignments(userId: Int): Flow<List<CourseWithAssignments>>

    /**
     * Inserta o reemplaza un lote de cursos.
     *
     * ## Por qué `REPLACE`
     *
     * `OnConflictStrategy.REPLACE` significa: "si ya existe una fila
     * con esta clave primaria, bórrala y vuelve a insertarla con los
     * datos nuevos". Es lo que queremos al sincronizar: si el curso
     * ya existe (porque el usuario ya lo había cargado antes), se
     * actualiza con los datos frescos; si no, se inserta.
     *
     * ## Por qué es una operación de lote
     *
     * Porque la sincronización trae todos los cursos de golpe.
     * Hacerlo uno a uno sería mucho más lento.
     *
     * ## Transacción
     *
     * La transacción la abre el repositorio con `TransactionRunner`,
     * no este método. Así se puede agrupar el "borrar + insertar"
     * en una sola unidad atómica.
     *
     * @param courses Lista de cursos a insertar o actualizar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    /**
     * Borra todos los cursos de un usuario.
     *
     * ## Qué pasa con las tareas
     *
     * Las tareas asociadas se borran en cascada gracias a la
     * `ForeignKey` con `onDelete = CASCADE` definida en
     * `AssignmentEntity`. No hace falta borrarlas a mano.
     *
     * ## Cuándo se llama
     *
     * Antes de insertar la nueva lista en `refreshCourses`, para
     * evitar que queden cursos obsoletos que ya no están en Moodle.
     *
     * @param userId ID del usuario cuyos cursos se eliminan.
     */
    @Query("DELETE FROM courses WHERE userId = :userId")
    suspend fun clearCoursesForUser(userId: Int)

    /**
     * Lee los cursos de un usuario de forma puntual (no reactiva).
     *
     * ## Cuándo se usa
     *
     * Para operaciones que no necesitan observar cambios, como
     * comprobaciones de existencia o validaciones puntuales. Para el
     * listado de la UI se debe usar [getCoursesWithAssignments], que
     * es reactivo.
     *
     * @param userId ID del usuario logueado.
     * @return Lista de cursos (sin tareas anidadas).
     */
    @Query("SELECT * FROM courses WHERE userId = :userId")
    suspend fun getCoursesOnce(userId: Int): List<CourseEntity>
}