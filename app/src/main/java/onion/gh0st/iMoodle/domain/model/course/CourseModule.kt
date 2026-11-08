package onion.gh0st.iMoodle.domain.model.course

/**
 * Módulo de Moodle dentro de una sección de curso.
 *
 * ## Qué es un módulo
 *
 * En Moodle, un módulo es cualquier actividad o recurso que aparece
 * dentro de una sección. Por ejemplo:
 *
 * - Una **tarea** (`modname = "assign"`).
 * - Un **foro** (`modname = "forum"`).
 * - Un **archivo PDF** (`modname = "resource"`).
 * - Una **carpeta** con varios archivos (`modname = "folder"`).
 * - Un **enlace a una web externa** (`modname = "url"`).
 * - Una **etiqueta de texto** (`modname = "label"`).
 * - Un **cuestionario** (`modname = "quiz"`).
 * - Una **wiki** (`modname = "wiki"`).
 * - Un **libro** (`modname = "book"`).
 *
 * Cada módulo tiene un `modname` que indica su tipo. Ese `modname` es
 * lo que usa la app para clasificar el módulo con `ModuleType`.
 *
 * ## Diferencia con `ModuleType`
 *
 * `CourseModule` es el modelo **crudo**: contiene los datos del
 * módulo sin clasificar. `ModuleType` es la **clasificación**
 * posterior: un `sealed interface` que dice qué tipo de módulo es y
 * permite a la UI hacer un `when` exhaustivo para decidir a qué
 * pantalla navegar.
 *
 * La transformación la hace `ModuleType.from(module)`, que mapea el
 * `modname` a un tipo concreto.
 *
 * ## Quién lo usa
 *
 * - `MoodleCourseRepository` lo construye a partir del JSON de
 *   Moodle.
 * - `GetCourseContents` lo envuelve en `ModuleType` antes de
 *   pasarlo a la UI.
 * - La UI del detalle de curso lo usa (a través de `ModuleType`) para
 *   decidir qué hacer al pulsar un módulo.
 *
 * @property id ID de la instancia del módulo en el curso (`cmid`).
 *   Es único dentro del curso.
 * @property instance ID interno de la instancia en su plugin. Por
 *   ejemplo, el ID de la tarea en `mod_assign`. Es distinto del `id`
 *   (`cmid`) y se usa para peticiones específicas de cada plugin.
 * @property name Nombre visible del módulo.
 * @property modname Tipo de plugin de Moodle (`"assign"`, `"forum"`,
 *   `"resource"`, `"url"`, `"label"`, etc.). Es la clave que usa la
 *   app para clasificar el módulo.
 * @property url URL canónica del módulo en Moodle, o `null` si el
 *   módulo no es navegable (por ejemplo, una etiqueta).
 * @property description Descripción en HTML, o `null` si no tiene.
 * @property files Archivos adjuntos del módulo (recursos, carpetas,
 *   adjuntos de tareas, etc.). Puede estar vacía.
 */
data class CourseModule(
    val id: Int,
    val instance: Int,
    val name: String,
    val modname: String,
    val url: String?,
    val description: String?,
    val files: List<CourseFile> = emptyList(),
)