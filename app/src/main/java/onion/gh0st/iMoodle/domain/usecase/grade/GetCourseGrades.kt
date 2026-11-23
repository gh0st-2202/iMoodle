package onion.gh0st.iMoodle.domain.usecase.grade

import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.repository.grade.GradeRepository
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import javax.inject.Inject

/**
 * Caso de uso: obtener las notas de un curso.
 *
 * ## Qué hace
 *
 * Resuelve el alumno de la sesión activa y delega en
 * [GradeRepository]. Se separa del repositorio porque decidir **de
 * quién** son las notas es una regla de negocio: siempre las del
 * usuario que tiene la sesión abierta.
 *
 * ## Manejo de errores
 *
 * Cualquier fallo del repositorio (red, permisos, parseo) se propaga
 * tal cual. El ViewModel lo traduce a un mensaje para el usuario.
 *
 * ## Quién lo usa
 *
 * - `GradesViewModel`.
 *
 * @param gradeRepository Contrato de acceso a las notas.
 * @param observeSession Lectura síncrona de la sesión activa.
 */
class GetCourseGrades @Inject constructor(
    private val gradeRepository: GradeRepository,
    private val observeSession: ObserveSession,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param courseId Identificador del curso.
     * @return Las notas del alumno en ese curso.
     */
    suspend operator fun invoke(courseId: Int): CourseGrade =
        gradeRepository.getCourseGrades(courseId, observeSession.currentUserId)
}
