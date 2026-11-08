package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.model.course.CourseFile
import javax.inject.Inject

/**
 * Caso de uso: obtener los archivos de un módulo concreto de un
 * curso.
 *
 * ## Qué hace
 *
 * Reutiliza [GetCourseContents] (que ya sabe cargar y clasificar los
 * módulos de un curso) y filtra por `moduleId` para quedarse solo con
 * los archivos del módulo indicado.
 *
 * ## Por qué es un caso de uso propio
 *
 * Porque la pantalla de archivos de un módulo necesita una lista
 * plana de archivos, no toda la estructura del curso. Sin este caso
 * de uso, el ViewModel tendría que:
 *
 * 1. Llamar a `GetCourseContents`.
 * 2. Recorrer todas las secciones.
 * 3. Buscar el módulo con el `moduleId`.
 * 4. Extraer sus archivos.
 *
 * Eso sería lógica de negocio en el ViewModel, que no es su sitio.
 * Con este caso de uso, el ViewModel solo hace una llamada y recibe
 * lo que necesita.
 *
 * ## Quién lo usa
 *
 * - `CourseFilesViewModel`.
 *
 * @param getCourseContents Caso de uso que carga los contenidos del
 *   curso.
 */
class GetCourseFiles @Inject constructor(
    private val getCourseContents: GetCourseContents,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Qué hace paso a paso
     *
     * 1. Llama a `getCourseContents` para cargar todas las secciones
     *    del curso.
     * 2. Recorre todos los módulos de todas las secciones.
     * 3. Busca el primero cuyo `id` coincida con `moduleId`.
     * 4. Devuelve sus archivos.
     *
     * ## Qué pasa si el módulo no existe
     *
     * Devuelve una lista vacía. No es un error: puede pasar si el
     * módulo fue borrado entre que el usuario abrió el curso y pulsó
     * el módulo. La UI mostrará una lista vacía (o un mensaje de "no
     * hay archivos").
     *
     * ## Qué pasa si el módulo existe pero no tiene archivos
     *
     * Devuelve una lista vacía. La UI mostrará el mismo mensaje que
     * en el caso anterior.
     *
     * @param courseId ID del curso.
     * @param moduleId ID del módulo (cmid) cuyos archivos se quieren.
     * @return Lista de archivos del módulo, o vacía si no hay.
     */
    suspend operator fun invoke(courseId: Int, moduleId: Int): List<CourseFile> =
        getCourseContents(courseId)
            .flatMap { section -> section.modules }
            .firstOrNull { moduleType -> moduleType.module.id == moduleId }
            ?.module
            ?.files
            ?: emptyList()
}