package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: refrescar los cursos desde Moodle.
 *
 * ## Qué hace
 *
 * Pide al repositorio que descargue los cursos del usuario y los
 * guarde en la base local. La pantalla no lee el resultado
 * directamente: sigue observando `GetCourses`, que emitirá la lista
 * actualizada cuando Room la tenga.
 *
 * ## Manejo de errores
 *
 * Propaga la excepción. El ViewModel decide cómo avisar (en el
 * listado, un toast genérico) y mantiene la lista anterior visible.
 *
 * @param courseRepository Contrato de acceso a cursos.
 */
class RefreshCourses @Inject constructor(
    private val courseRepository: CourseRepository,
) {

    /**
     * Descarga los cursos del usuario.
     *
     * @param userId ID de Moodle del usuario.
     */
    suspend operator fun invoke(userId: Int) {
        courseRepository.refreshCourses(userId)
    }
}
