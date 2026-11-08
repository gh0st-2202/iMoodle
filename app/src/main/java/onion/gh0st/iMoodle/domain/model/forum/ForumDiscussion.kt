package onion.gh0st.iMoodle.domain.model.forum

/**
 * Una discusión de un foro de Moodle.
 *
 * ## Qué es una discusión
 *
 * En Moodle, un foro se organiza en **discusiones** (también llamadas
 * "hilos" o "temas"). Cada discusión es una conversación iniciada por
 * alguien, con un mensaje inicial y, normalmente, varias respuestas.
 *
 * Ejemplo:
 *
 * ```
 * Foro: "Dudas del Tema 3"
 * ├── Discusión: "No entiendo el ejercicio 5"
 * │   ├── Post inicial: "Hola, no entiendo..."
 * │   ├── Respuesta 1: "Mira el ejemplo de la página 42"
 * │   └── Respuesta 2: "Gracias, ya lo entendí"
 * └── Discusión: "¿El examen entra el tema 4?"
 *     └── ...
 * ```
 *
 * ## Diferencia con `ForumPost`
 *
 * `ForumDiscussion` es la **cabecera** de la conversación (título,
 * autor, número de respuestas). `ForumPost` es cada mensaje
 * individual dentro de la discusión.
 *
 * La app muestra primero la lista de discusiones, y al pulsar una,
 * la lista de posts.
 *
 * ## Quién lo usa
 *
 * - `MoodleForumRepository` lo construye a partir del JSON de
 *   Moodle.
 * - `ForumAdapter` lo usa para mostrar la lista de discusiones.
 * - La UI de detalle de discusión lo usa para mostrar el título.
 *
 * @property id ID interno de la discusión en Moodle.
 * @property name Asunto/título de la discusión.
 * @property message Primer mensaje del hilo, en HTML tal cual lo
 *   envía Moodle.
 * @property author Nombre completo de quien abrió la discusión.
 * @property created Fecha de creación en **segundos** (epoch).
 * @property numReplies Número de respuestas recibidas. Si es 0, la
 *   UI oculta el contador.
 */
data class ForumDiscussion(
    val id: Int,
    val name: String,
    val message: String,
    val author: String,
    val created: Long,
    val numReplies: Int,
)