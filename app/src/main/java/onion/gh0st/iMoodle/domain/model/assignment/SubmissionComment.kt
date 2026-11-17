package onion.gh0st.iMoodle.domain.model.assignment

/**
 * Un mensaje dentro de la conversación de una entrega.
 *
 * ## Qué es
 *
 * Cada entrega de una tarea tiene un hilo de comentarios (parecido a un
 * chat) en el que el alumno puede escribir al profesor y este responder.
 * Esta clase representa un mensaje de ese hilo.
 *
 * ## Quién puede borrarlo
 *
 * Solo el autor puede borrar su comentario. En lugar de deducirlo
 * comparando nombres (frágil: dos personas pueden llamarse igual) se usan
 * dos datos fiables que aporta Moodle: [canDelete] y [authorId].
 *
 * @property id Identificador del comentario en Moodle.
 * @property author Nombre completo de quien lo escribió.
 * @property content Texto del mensaje; puede contener HTML o enlaces.
 * @property createdAt Fecha y hora de creación, en segundos desde 1970 (hora Unix).
 * @property authorId Identificador del usuario que lo escribió (`0` si no se conoce).
 * @property canDelete `true` si Moodle indica que el usuario actual puede borrarlo.
 */
data class SubmissionComment(
    val id: Int,
    val author: String,
    val content: String,
    val createdAt: Long,
    val authorId: Int = 0,
    val canDelete: Boolean = false,
)
