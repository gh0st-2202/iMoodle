package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener la URL externa real de un módulo de tipo `url`.
 *
 * ## Qué es un módulo `url`
 *
 * En Moodle, un módulo de tipo `url` es simplemente un enlace a una
 * web externa: un artículo, un vídeo, un documento en Drive, un PDF
 * alojado fuera de Moodle, etc. El profesor lo crea para apuntar a un
 * recurso externo.
 *
 * ## Por qué es un caso de uso propio
 *
 * Porque obtener la URL externa es una operación aparte. El listado
 * de contenidos del curso solo dice "aquí hay un módulo de tipo
 * url", pero no dice a qué URL apunta. Para eso hay que hacer una
 * petición específica (`mod_url_get_urls_by_courses`). Este caso de
 * uso encapsula esa operación.
 *
 * ## Cuándo se llama
 *
 * Solo cuando el usuario pulsa un módulo de tipo `url`. Así se evita
 * hacer la petición para todos los módulos del curso (que sería
 * costoso).
 *
 * ## Quién lo usa
 *
 * - `CourseDetailViewModel` (al pulsar un módulo `url`).
 *
 * @param repository Contrato de acceso a los cursos.
 */
class GetUrlExternalUrl @Inject constructor(
    private val repository: CourseRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Qué hace
     *
     * Delega en el repositorio. No añade lógica adicional, pero
     * encapsula la operación para que el ViewModel no dependa
     * directamente del repositorio.
     *
     * ## Cuándo devuelve `null`
     *
     * Si el módulo no existe, si la petición falla, o si la URL no se
     * encuentra en la respuesta. La UI trata el `null` mostrando un
     * toast genérico.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo `url`.
     * @param forceRefresh Si `true`, ignora la caché del repositorio.
     * @return La URL externa, o `null` si no se encuentra.
     */
    suspend operator fun invoke(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean = false,
    ): String? = repository.getUrlExternalUrl(courseId, instanceId, forceRefresh)
}