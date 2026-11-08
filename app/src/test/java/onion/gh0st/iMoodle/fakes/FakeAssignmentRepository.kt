package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.assignment.Assignment
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository

/**
 * Implementación en memoria de [AssignmentRepository] para tests.
 *
 * Devuelve los datos configurados, sin parsing de JSON ni red.
 */
class FakeAssignmentRepository : AssignmentRepository {

    /** Tareas a devolver por [getAssignments]. */
    var assignments: List<Assignment> = emptyList()

    /** Detalle a devolver por [getAssignmentDetail]. */
    var assignmentDetail: AssignmentDetail? = null

    /** Si es no-null, se lanza al invocar [getAssignmentDetail]. */
    var detailError: Throwable? = null

    /**
     * Devuelve [assignments].
     *
     * @param courseIds IDs de los cursos (ignorados por el fake).
     * @return Lista configurada.
     */
    override suspend fun getAssignments(courseIds: List<Int>): List<Assignment> = assignments

    /**
     * Devuelve [assignmentDetail] o lanza [detailError].
     *
     * @param courseId ID del curso (ignorado por el fake).
     * @param assignmentId ID de la tarea (ignorado por el fake).
     * @return El detalle configurado, o `null`.
     * @throws Throwable si [detailError] no es `null`.
     */
    override suspend fun getAssignmentDetail(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail? {
        detailError?.let { throw it }
        return assignmentDetail
    }
}