package onion.gh0st.iMoodle.ui.courses.list

/**
 * Modelo de presentación de un curso en la pantalla de listado.
 *
 * ## Qué es un "modelo de presentación"
 *
 * Es un objeto específico de la capa de UI, diseñado para que el
 * adapter de la lista lo consuma directamente. No es el modelo de
 * dominio (`Course`), sino una versión adaptada:
 *
 * - Algunos campos se renombran (`name` en lugar de `fullname`).
 * - Algunos se aplanan (por ejemplo, `progress` de `Int?` a `Int`,
 *   con `0` en lugar de `null`).
 * - Algunos se añaden (por ejemplo, `isFavorite`, que no existe en
 *   el dominio pero se prevé para el futuro).
 *
 * ## Por qué separar el modelo de dominio del de presentación
 *
 * Porque el dominio no debe conocer los detalles de la UI (por
 * ejemplo, si un curso se muestra como favorito). Si el adapter
 * usara `Course` directamente:
 *
 * - Tendría que leer campos con nombres que no encajan con la UI.
 * - Tendría que manejar los `null` del dominio.
 * - No podría añadir campos de UI sin contaminar el dominio.
 *
 * Con `CourseUi`, cada capa tiene su propio modelo.
 *
 * ## Quién lo construye
 *
 * `CoursesViewModel.toUi`, que mapea un `Course` de dominio a
 * `CourseUi`.
 *
 * ## Quién lo consume
 *
 * - `CourseAdapter`, para pintar la tarjeta de cada curso.
 * - `CoursesFragment`, para el click.
 *
 * @property id ID del curso.
 * @property name Nombre completo del curso, ya adaptado para la UI.
 * @property pendingTasks Número de tareas pendientes de entrega.
 *   Actualmente siempre `0` (deuda: pendiente de implementar).
 * @property imageUrl URL de portada, o `null` si no tiene. Se usa
 *   para pintar la imagen real; si es `null`, el adapter genera un
 *   patrón SVG.
 * @property isFavorite `true` si el usuario lo ha marcado como
 *   favorito. Actualmente siempre `false` (deuda: pendiente de
 *   implementar).
 * @property progress Progreso del usuario en el curso (0-100). `0`
 *   si Moodle no lo informa (se normaliza desde `null` en el
 *   mapeo).
 */
data class CourseUi(
    val id: Int,
    val name: String,
    val pendingTasks: Int,
    val imageUrl: String?,
    val isFavorite: Boolean,
    val progress: Int,
)