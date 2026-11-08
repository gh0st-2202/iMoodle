package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.model.course.CourseSection
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener las secciones de un curso con sus módulos ya
 * clasificados por tipo.
 *
 * ## Qué hace
 *
 * El repositorio devuelve `CourseContent` (secciones con módulos
 * crudos). Este caso de uso transforma esos módulos crudos en
 * `ModuleType` (Assignment, Forum, Resource, etc.), para que la UI
 * pueda hacer un `when` exhaustivo y decidir a qué pantalla navegar
 * al pulsar cada módulo.
 *
 * ## Por qué es un caso de uso y no parte del repositorio
 *
 * Porque la clasificación de módulos es una **decisión de negocio**,
 * no un detalle de red. El repositorio solo sabe traer datos de
 * Moodle; qué tipos de módulos existen y cómo se clasifican es una
 * regla del dominio. Manteniéndolo aquí:
 *
 * - El repositorio puede ser sustituido sin tocar la clasificación.
 * - La clasificación puede testearse sin red.
 * - Si mañana se añade un tipo de módulo nuevo, solo se toca este
 *   caso de uso (y `ModuleType`).
 *
 * ## Caché
 *
 * El repositorio real tiene caché en memoria por `courseId`. Este
 * caso de uso propaga el `forceRefresh` para que el pull-to-refresh
 * de la UI pueda bypasear la caché.
 *
 * ## Quién lo usa
 *
 * - `CourseDetailViewModel` (al abrir el detalle de un curso).
 * - `GetCourseFiles` (para filtrar los archivos de un módulo).
 *
 * @param repository Contrato de acceso a los cursos.
 */
class GetCourseContents @Inject constructor(
    private val repository: CourseRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pide los contenidos crudos al repositorio.
     * 2. Recorre cada sección.
     * 3. Para cada módulo de cada sección, lo clasifica con
     *    `ModuleType.from`.
     * 4. Devuelve una lista de `CourseSection` (secciones con módulos
     *    ya clasificados).
     *
     * ## Manejo de errores
     *
     * Cualquier excepción del repositorio (red, parseo, etc.) se
     * propaga tal cual. El ViewModel la captura y muestra un mensaje
     * genérico.
     *
     * @param courseId ID del curso.
     * @param forceRefresh Si `true`, ignora la caché del repositorio y
     *   fuerza una llamada a la red.
     * @return Lista de secciones con módulos clasificados.
     */
    suspend operator fun invoke(
        courseId: Int,
        forceRefresh: Boolean = false,
    ): List<CourseSection> {
        val courseContents = repository.getCourseContents(courseId, forceRefresh)
        return courseContents.map { courseContent -> toSection(courseContent) }
    }

    /**
     * Convierte un `CourseContent` (modelo crudo) en una
     * `CourseSection` (modelo listo para la UI).
     *
     * ## Qué hace
     *
     * Copia el `id` y el `name`, y transforma cada `CourseModule` en
     * su `ModuleType` correspondiente.
     *
     * @param courseContent Sección cruda del repositorio.
     * @return La sección lista para la UI.
     */
    private fun toSection(courseContent: CourseContent): CourseSection =
        CourseSection(
            id = courseContent.id,
            name = courseContent.name,
            modules = courseContent.modules.map { module -> ModuleType.from(module) },
        )
}