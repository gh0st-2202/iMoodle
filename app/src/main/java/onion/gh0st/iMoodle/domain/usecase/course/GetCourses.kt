package onion.gh0st.iMoodle.domain.usecase.course

import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: observar los cursos de un usuario.
 *
 * ## Qué devuelve
 *
 * Un `Flow` con la lista local de cursos. Room emite una nueva lista
 * cada vez que cambia (por ejemplo, tras un refresco de red), así que
 * la pantalla se actualiza sola sin volver a pedir nada.
 *
 * ## Por qué recibe `userId`
 *
 * Porque la caché local está separada por usuario. Si dos cuentas
 * comparten dispositivo, cada una ve solo sus cursos. El `userId` lo
 * aporta la sesión (`ObserveSession.currentUserId`), no la UI.
 *
 * @param courseRepository Contrato de acceso a cursos.
 */
class GetCourses @Inject constructor(
    private val courseRepository: CourseRepository,
) {

    /**
     * Observa los cursos del usuario indicado.
     *
     * @param userId ID de Moodle del usuario.
     * @return Flujo con la lista de cursos (puede empezar vacía).
     */
    operator fun invoke(userId: Int): Flow<List<Course>> =
        courseRepository.getCoursesFlow(userId)
}
