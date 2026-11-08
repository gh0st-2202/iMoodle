package onion.gh0st.iMoodle.domain.model.session

/**
 * Fotografía de la sesión activa del usuario.
 *
 * ## Qué es una "fotografía"
 *
 * Es una representación **inmutable** del estado de la sesión en un
 * momento concreto. Cuando la UI observa la sesión, recibe una
 * `ActiveSession` cada vez que cambia algo. Cada emisión es una
 * "fotografía" del estado actual.
 *
 * ## Qué contiene
 *
 * Dos piezas:
 *
 * 1. **`context`**: los datos puros de la sesión (provincia,
 *    usuario, token). Es el "quién" y el "dónde".
 * 2. **`user`**: el usuario cargado (nombre, email, avatar, datos de
 *    Pasen si se han consultado). Es el "qué sabemos de él".
 *
 * ## Qué NO contiene
 *
 * **No contiene el `MoodleSession` vivo** (cliente HTTP, cookies,
 * API REST). Eso es infraestructura pura y el dominio no debe
 * conocerla. Cuando la UI necesita el `MoodleSession` vivo (por
 * ejemplo, para abrir el WebView con cookies), lo pide aparte con
 * `MoodleSessionProvider`.
 *
 * ## Cuándo `user` es `null`
 *
 * En dos casos:
 *
 * 1. **Antes de cargar el perfil**: justo después del login, hay un
 *    instante en que `context` está listo pero `user` aún no se ha
 *    cargado de Moodle. La UI puede mostrar un placeholder.
 * 2. **Auto-login offline sin perfil**: si la app arranca sin
 *    conexión, puede recuperar la sesión guardada pero no puede
 *    cargar el perfil de Moodle. En ese caso, `user` se rellena con
 *    los datos mínimos de la sesión (id, username), pero si esos
 *    datos tampoco están, `user` queda `null`.
 *
 * ## Quién lo usa
 *
 * - `DefaultSessionRepository` lo publica como `StateFlow`.
 * - `SessionRepository.session` lo expone a la UI.
 * - Casi todos los ViewModels lo usan para saber quién es el usuario
 *   actual.
 *
 * @property context Datos puros de la sesión (provincia, usuario,
 *   token).
 * @property user Usuario cargado, o `null` si aún no se ha pedido el
 *   perfil.
 */
data class ActiveSession(
    val context: SessionContext,
    val user: User?,
)