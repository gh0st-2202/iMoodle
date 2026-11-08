package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.data.local.dao.AssignmentDao
import onion.gh0st.iMoodle.data.local.entity.AssignmentEntity

/**
 * Implementación en memoria de [AssignmentDao] para tests.
 *
 * Sustituye a Room en los tests unitarios de repositorios. No hay
 * persistencia real: el estado vive en [assignments].
 */
class FakeAssignmentDao : AssignmentDao {

    /** Snapshot de las tareas insertadas, útil para asserts. */
    var assignments: List<AssignmentEntity> = emptyList()
        private set

    /**
     * Reemplaza el contenido por el lote indicado.
     *
     * @param assignments Tareas a guardar.
     */
    override suspend fun insertAssignments(assignments: List<AssignmentEntity>) {
        this.assignments = assignments
    }

    /**
     * Elimina las tareas de un curso.
     *
     * @param courseId ID del curso cuyas tareas se eliminan.
     */
    override suspend fun clearAssignmentsForCourse(courseId: Int) {
        assignments = assignments.filterNot { it.courseId == courseId }
    }
}