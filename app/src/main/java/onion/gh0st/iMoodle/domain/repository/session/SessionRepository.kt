package onion.gh0st.iMoodle.domain.repository.session

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.model.session.User

/**
 * Contrato del gestor del ciclo de vida de la sesión.
 *
 * ## Qué es "el ciclo de vida de la sesión"
 *
 * Es todo lo que ocurre desde que el usuario abre la app hasta que la
 * cierra:
 *
 * - **Auto-login**: al arrancar, intentar recuperar la sesión
 *   guardada.
 * - **Login**: si no hay sesión guardada, autenticarse.
 * - **Uso normal**: la sesión está activa, la UI observa el estado.
 * - **Refresco de tokens**: cuando el token caduca, obtener uno
 *   nuevo sin molestar al usuario.
 * - **Invalidación**: cuando el token no se puede refrescar, cerrar
 *   la sesión y notificar a la UI.
 * - **Logout**: cuando el usuario cierra sesión voluntariamente.
 *
 * Este contrato expone todas esas operaciones.
 *
 * ## Qué expone
 *
 * - [session]: un `StateFlow` con la sesión activa. La UI lo observa
 *   para reaccionar a cambios.
 * - [events]: un `SharedFlow` con eventos puntuales (por ejemplo,
 *   "sesión expirada"). La UI los consume una sola vez.
 * - Los métodos para hacer login, auto-login, logout, invalidación y
 *   actualización del usuario.
 *
 * ## Qué NO expone
 *
 * **No expone el `MoodleApiClient` ni el `MoodleSession`**. Esos son
 * infraestructura pura. Los repositorios de `api/` que necesitan el
 * cliente REST lo piden a través de `SessionApiProvider`. Los
 * componentes que necesitan las cookies lo piden a través de
 * `MoodleSessionProvider`. Los ViewModels y casos de uso **no**
 * necesitan ni uno ni otro.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `DefaultSessionRepository`.
 * - **Tests**: `FakeSessionRepository`.
 *
 * ## Quién la consume
 *
 * - Casi todos los ViewModels (para saber quién es el usuario
 *   actual).
 * - `MainActivity` (para reaccionar a `Expired`).
 * - `LoginViewModel` (para hacer login y auto-login).
 * - `ProfileViewModel` (para hacer logout y actualizar el usuario).
 * - `CoursesViewModel` (para observar el `userId`).
 * - `GetCurrentUser` (caso de uso).
 */
interface SessionRepository {

    /**
     * Sesión activa, o `null` si el usuario no está autenticado.
     *
     * ## Por qué es un `StateFlow`
     *
     * Porque la UI necesita **observar** la sesión y reaccionar a
     * sus cambios. Un `StateFlow` emite el valor actual y todos los
     * futuros hasta que se cancele. La UI se suscribe una vez y se
     * olvida.
     *
     * ## Por qué puede ser `null`
     *
     * Porque puede no haber sesión: antes del login, después del
     * logout, o durante el instante entre arrancar la app y recuperar
     * la sesión guardada.
     */
    val session: StateFlow<ActiveSession?>

    /**
     * Eventos puntuales de sesión.
     *
     * ## Por qué es un `SharedFlow` y no un `StateFlow`
     *
     * Porque los eventos son **de un solo uso**. Si estuvieran en un
     * `StateFlow`, se re-emitirían cada vez que la UI se
     * re-suscribiera (al rotar, al volver de segundo plano, etc.),
     * provocando toasts repetidos o navegaciones en bucle.
     *
     * Un `SharedFlow` sin `replay` no re-emite eventos antiguos: solo
     * los que ocurren a partir de la suscripción.
     *
     * ## Qué eventos puede emitir
     *
     * - `SessionEvent.Expired`: la sesión ha dejado de ser válida.
     * - `SessionEvent.TokenRefreshed`: el token se ha refrescado.
     *
     * ## Quién los consume
     *
     * - `MainActivity` (para `Expired`).
     * - Cualquier componente que necesite reaccionar a un refresco.
     */
    val events: SharedFlow<SessionEvent>

    /**
     * Realiza el login completo.
     *
     * ## Qué hace
     *
     * 1. Hace login CAS + Moodle.
     * 2. Persiste la sesión y las credenciales.
     * 3. Construye la `MoodleSession` viva.
     * 4. Carga el perfil del usuario.
     * 5. Publica la sesión activa.
     *
     * ## Manejo de errores
     *
     * Devuelve un `Result` con la sesión o con la excepción. El
     * `LoginViewModel` decide qué hacer con el error.
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param credentials Credenciales del usuario.
     * @return `Result` con la sesión o con la excepción.
     */
    suspend fun login(
        provincia: Provincia,
        credentials: CasCredentials,
    ): Result<ActiveSession>

    /**
     * Intenta recuperar la sesión guardada.
     *
     * ## Qué hace
     *
     * 1. Lee la sesión guardada más reciente.
     * 2. Valida el token contra Moodle.
     * 3. Si es válido, reconstruye la sesión viva y la publica.
     * 4. Si no, devuelve `null`.
     *
     * @return `Result` con la sesión recuperada (o `null` si no hay),
     *   o con la excepción.
     */
    suspend fun tryAutoLogin(): Result<ActiveSession?>

    /**
     * Cierra la sesión y limpia todo.
     *
     * ## Qué limpia
     *
     * - La sesión activa en memoria.
     * - La sesión guardada en disco.
     * - Las credenciales guardadas.
     * - Las alarmas programadas.
     * - Los datos locales (Room).
     * - La caché del usuario.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa "Cerrar sesión" en el perfil.
     */
    suspend fun logout()

    /**
     * Invalida la sesión y emite un evento `Expired`.
     *
     * ## Diferencia con [logout]
     *
     * - **`logout`**: voluntario. No emite evento (la UI ya sabe lo
     *   que ha pasado).
     * - **`invalidate`**: involuntario. Emite `Expired` para que la
     *   UI redirija al login.
     *
     * ## Cuándo se llama
     *
     * Cuando `MoodleApiClient` no puede refrescar el token.
     */
    suspend fun invalidate()

    /**
     * Actualiza el `User` de la sesión activa.
     *
     * ## Cuándo se llama
     *
     * - Tras cargar el perfil enriquecido con datos de Pasen.
     * - Tras refrescar el usuario desde Moodle.
     *
     * ## Por qué no es `suspend`
     *
     * Porque solo actualiza un `StateFlow` en memoria. No hay
     * operación de red ni de disco, así que no hay nada que esperar.
     *
     * @param user Usuario actualizado.
     */
    fun updateUser(user: User)

    /**
     * Refresca el `User` desde la red.
     *
     * ## Qué hace
     *
     * Pide el usuario a Moodle, actualiza el estado y la caché. Si
     * falla, **mantiene el usuario anterior** para no dejar al
     * usuario sin datos.
     *
     * ## Cuándo se llama
     *
     * - Al abrir el perfil con `forceRefresh = true`.
     * - Tras un pull-to-refresh (cuando se implemente).
     */
    suspend fun refreshUser()
}