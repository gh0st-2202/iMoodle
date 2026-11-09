package onion.gh0st.iMoodle.domain.model.assignment

/**
 * El "formulario de entrega" que rellena el alumno.
 *
 * ## ¿Qué es esto?
 * Cuando pulsas el botón de "Entregar", la app necesita saber qué quieres enviar exactamente.
 * Esta clase es como una cesta de la compra donde se guarda lo que has escrito y los archivos
 * que has seleccionado de tu teléfono.
 *
 * @property assignmentId El número de la tarea a la que estás entregando.
 * @property courseId El número del curso.
 * @property cmId El número del módulo del curso.
 * @property onlineText El comentario o texto que has escrito en el cuadro de texto (si lo hay).
 * @property newFileUris La lista de rutas (direcciones) de los archivos nuevos que has elegido en tu teléfono.
 * @property existingFileUrls La lista de enlaces de los archivos que YA estaban subidos y quieres conservar.
 *   Esto es importante porque Moodle, al guardar, borra todo lo anterior a menos que se lo volvamos a pasar.
 */
data class AssignmentSubmission(
    val assignmentId: Int,
    val courseId: Int,
    val cmId: Int,
    val onlineText: String,
    val newFileUris: List<String>,
    val existingFileUrls: List<String> = emptyList(),
)