package onion.gh0st.iMoodle.domain.model.course

/**
 * Sección de un curso con sus módulos ya clasificados.
 *
 * ## Diferencia con `CourseContent`
 *
 * `CourseContent` es el modelo **crudo** que devuelve Moodle: los
 * módulos aún no están clasificados. `CourseSection` es el modelo
 * **listo para la UI**: los módulos ya están envueltos en
 * `ModuleType`, lo que permite a la UI hacer un `when` exhaustivo
 * para decidir qué hacer al pulsar cada uno.
 *
 * La transformación de `CourseContent` a `CourseSection` la hace el
 * caso de uso `GetCourseContents`. Se separan porque cada uno tiene
 * una responsabilidad distinta:
 *
 * - `CourseContent` representa fielmente lo que devuelve Moodle.
 * - `CourseSection` representa lo que la UI necesita para
 *   renderizar.
 *
 * ## Quién lo usa
 *
 * - `GetCourseContents` lo construye.
 * - `CourseDetailViewModel` lo usa para construir los items del
 *   adapter.
 * - La UI del detalle de curso lo consume.
 *
 * @property id ID de la sección en Moodle.
 * @property name Nombre visible de la sección.
 * @property modules Módulos ya clasificados por tipo. Cada uno es un
 *   `ModuleType` (Assignment, Forum, Resource, etc.).
 */
data class CourseSection(
    val id: Int,
    val name: String,
    val modules: List<ModuleType>,
)