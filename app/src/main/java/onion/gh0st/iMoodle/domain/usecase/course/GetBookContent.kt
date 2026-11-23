package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.model.book.BookContent
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener el contenido de un libro de Moodle.
 *
 * @param repository Contrato de acceso a los cursos.
 */
class GetBookContent @Inject constructor(
    private val repository: CourseRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo `book`.
     * @param forceRefresh Si `true`, ignora la caché.
     * @return El contenido, o `null` si no se encuentra.
     */
    suspend operator fun invoke(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean = false,
    ): BookContent? = repository.getBookContent(courseId, instanceId, forceRefresh)
}
