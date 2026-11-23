package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.model.page.PageContent
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener el contenido de una página de Moodle.
 *
 * @param repository Contrato de acceso a los cursos.
 */
class GetPageContent @Inject constructor(
    private val repository: CourseRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo `page`.
     * @param forceRefresh Si `true`, ignora la caché.
     * @return El contenido, o `null` si no se encuentra.
     */
    suspend operator fun invoke(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean = false,
    ): PageContent? = repository.getPageContent(courseId, instanceId, forceRefresh)
}
