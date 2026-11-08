package onion.gh0st.iMoodle.domain.model.course

/**
 * Sección de un curso tal cual la devuelve Moodle.
 *
 * ## Qué es una sección
 *
 * En Moodle, un curso se organiza en **secciones** (también llamadas
 * "temas"). Cada sección agrupa una serie de **módulos** (tareas,
 * foros, recursos, enlaces, etc.). Es la estructura típica de un
 * curso:
 *
 * ```
 * Curso: Acceso a datos
 * ├── Sección "Tema 1: Introducción"
 * │   ├── Módulo: PDF de apuntes
 * │   ├── Módulo: Foro de dudas
 * │   └── Módulo: Tarea "Práctica 1"
 * ├── Sección "Tema 2: Ficheros"
 * │   ├── ...
 * ```
 *
 * ## Diferencia con `CourseSection`
 *
 * `CourseContent` es el modelo **crudo**: los módulos aún no están
 * clasificados por tipo. `CourseSection` es el modelo **listo para la
 * UI**: los módulos ya están clasificados como `ModuleType.Assignment`,
 * `ModuleType.Forum`, etc.
 *
 * La transformación de `CourseContent` a `CourseSection` la hace el
 * caso de uso `GetCourseContents`. Se separan porque:
 *
 * - `CourseContent` representa exactamente lo que devuelve Moodle
 *   (sin añadir lógica).
 * - `CourseSection` representa lo que la UI necesita (módulos ya
 *   tipados, listos para enrutar al pulsarlos).
 *
 * ## Quién lo usa
 *
 * - `MoodleCourseRepository.getCourseContents` lo construye a partir
 *   del JSON de Moodle.
 * - `GetCourseContents` lo transforma en `CourseSection` antes de
 *   pasarlo a la UI.
 *
 * @property id ID de la sección en Moodle.
 * @property name Nombre visible de la sección (por ejemplo, "Tema 1:
 *   Introducción"). Puede estar vacío en secciones "huérfanas" (sin
 *   título).
 * @property modules Módulos que contiene la sección.
 */
data class CourseContent(
    val id: Int,
    val name: String,
    val modules: List<CourseModule>,
)