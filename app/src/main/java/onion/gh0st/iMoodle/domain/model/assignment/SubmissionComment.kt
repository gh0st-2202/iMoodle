package onion.gh0st.iMoodle.domain.model.assignment

/**
 * Un mensaje dentro de la conversación de una entrega.
 *
 * ## ¿Qué es esto?
 * Es como un mensaje de WhatsApp, pero dentro de la tarea de Moodle. Tú puedes escribir uno
 * para avisar de algo, y el profesor puede escribir otro para corregirte o darte ánimos.
 *
 * @property id El número identificador del comentario.
 * @property author El nombre de la persona que escribió el comentario.
 * @property content El texto del mensaje. A veces viene con enlaces web.
 * @property createdAt La fecha y hora en la que se escribió (en segundos de ordenador).
 */
data class SubmissionComment(
    val id: Int,
    val author: String,
    val content: String,
    val createdAt: Long,
)