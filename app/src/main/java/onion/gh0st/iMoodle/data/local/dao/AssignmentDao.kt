package onion.gh0st.iMoodle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import onion.gh0st.iMoodle.data.local.entity.AssignmentEntity

/**
 * DAO de tareas (`assignments`).
 *
 * ## Estrategia de uso
 *
 * Las tareas se insertan en lote tras sincronizar los cursos con la
 * red (desde `MoodleCourseRepository.refreshCourses`) y se eliminan
 * en cascada al borrar su curso padre. Por eso este DAO no necesita
 * operaciones de borrado individuales en el flujo habitual: la
 * `ForeignKey` de `AssignmentEntity` se encarga.
 *
 * ## Sobre el borrado
 *
 * Aunque este DAO expone `clearAssignmentsForCourse`, el flujo actual
 * no lo usa: el borrado por usuario se hace vía
 * `CourseDao.clearCoursesForUser`, y la cascada arrastra las tareas.
 * El método está ahí para futuros usos (por ejemplo, sincronización
 * selectiva de un solo curso).
 */
@Dao
interface AssignmentDao {

    /**
     * Inserta o reemplaza un lote de tareas.
     *
     * ## Por qué `REPLACE`
     *
     * Porque un refresco puede traer tareas ya existentes (con
     * cambios de nombre, fecha, etc.) y queremos sobrescribirlas sin
     * tener que hacer un borrado previo. El reemplazo es atómico por
     * fila.
     *
     * ## Transacción
     *
     * La transacción la abre el repositorio con `TransactionRunner`,
     * agrupando el borrado de cursos y la inserción de tareas. Así,
     * o se hace todo, o no se hace nada.
     *
     * @param assignments Lista de tareas a insertar o actualizar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<AssignmentEntity>)

    /**
     * Borra todas las tareas de un curso concreto.
     *
     * ## Cuándo se usa
     *
     * Actualmente no se llama desde ningún flujo: el borrado por
     * usuario de `CourseDao` arrastra las tareas en cascada. Está
     * reservado para futuros usos (por ejemplo, sincronización
     * selectiva de un solo curso, o un refresco manual que quiera
     * limpiar antes de insertar).
     *
     * @param courseId ID del curso cuyas tareas se eliminan.
     */
    @Query("DELETE FROM assignments WHERE courseId = :courseId")
    suspend fun clearAssignmentsForCourse(courseId: Int)
}