package onion.gh0st.iMoodle.domain.model.forum

/**
 * Un mensaje dentro de una discusión de foro de Moodle.
 *
 * ## Qué es un post
 *
 * Es un mensaje individual dentro de una discusión. El primero es el
 * mensaje inicial (el que abre el hilo), y los siguientes son
 * respuestas a él o a otros mensajes.
 *
 * ## Sobre el campo `isReply`
 *
 * Moodle distingue entre "mensaje inicial" y "respuesta" usando el
 * `parentid`. Si el `parentid` es 0, es el mensaje inicial; si es
 * distinto de 0, es una respuesta (y el `parentid` indica a qué
 * mensaje responde).
 *
 * La app simplifica esto: solo distingue entre "mensaje inicial"
 * (`isReply = false`) y "respuesta" (`isReply = true`). No muestra la
 * jerarquía de respuestas: las ordena por fecha, como una
 * conversación lineal. Es suficiente para un foro de consulta.
 *
 * ## Quién lo usa
 *
 * - `MoodleForumRepository` lo construye a partir del JSON de
 *   Moodle.
 * - `DiscussionAdapter` lo usa para mostrar la lista de posts.
 *
 * @property id ID interno del post en Moodle.
 * @property subject Asunto visible del mensaje.
 * @property message Cuerpo del mensaje, en HTML tal cual lo envía
 *   Moodle, con etiquetas y entidades. La UI lo renderiza con
 *   `HtmlUtils.renderHtml` para preservar los enlaces y el formato.
 * @property author Nombre completo del autor del post.
 * @property created Fecha de publicación en **segundos** (epoch). Si
 *   Moodle no la informa, vale `0`.
 * @property isReply `true` si el post responde a otro mensaje (tiene
 *   `parentid > 0`), `false` si es el mensaje inicial del hilo.
 */
data class ForumPost(
    val id: Int,
    val subject: String,
    val message: String,
    val author: String,
    val created: Long,
    val isReply: Boolean,
)