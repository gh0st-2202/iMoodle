package onion.gh0st.iMoodle.ui.courses.list

/**
 * Modelo de presentación de un curso en el listado.
 *
 * ## Qué es
 * Es una versión "adaptada" del curso para que la pantalla pueda
 * dibujarlo fácilmente. Incluye datos como el nombre, la imagen y el
 * estado de favorito.
 *
 * @property id El identificador único del curso.
 * @property name El nombre del curso que se muestra al usuario.
 * @property pendingTasks El número de tareas pendientes (actualmente 0).
 * @property imageUrl La dirección de la imagen de portada, si la tiene.
 * @property isFavorite Indica si el curso está en la lista de favoritos.
 * @property progress El porcentaje de progreso del alumno en el curso.
 */
data class CourseUi(
    val id: Int,
    val name: String,
    val pendingTasks: Int,
    val imageUrl: String?,
    val isFavorite: Boolean,
    val progress: Int,
)