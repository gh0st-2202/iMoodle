package onion.gh0st.iMoodle.domain.model.session

/**
 * Eventos puntuales emitidos por el repositorio de sesión.
 *
 * ## Qué es un "evento de sesión"
 *
 * A diferencia de `ActiveSession` (que representa el **estado**
 * actual), un evento de sesión es algo que **ocurre** en un momento
 * concreto y que la UI debe procesar una sola vez. Por ejemplo, "la
 * sesión ha expirado" no es un estado: es un suceso. Una vez
 * procesado, no vuelve a ocurrir.
 *
 * ## Por qué se separan los eventos del estado
 *
 * Porque si los eventos estuvieran en el estado, se re-emitirían cada
 * vez que la UI se re-suscribiera (al rotar, al volver de segundo
 * plano, etc.). El usuario vería, por ejemplo, un toast de "sesión
 * expirada" varias veces, o la app intentaría redirigir al login en
 * bucle.
 *
 * Con eventos separados, se procesan una sola vez. La UI los consume
 * y ya está.
 *
 * ## Eventos disponibles
 *
 * - **`Expired`**: la sesión ha dejado de ser válida (token caducado
 *   sin posibilidad de refresco, contraseña cambiada, etc.). La UI
 *   debe redirigir al login.
 * - **`TokenRefreshed`**: el token de Moodle se ha refrescado de
 *   forma transparente. La UI puede usar el nuevo token si lo
 *   necesita (normalmente no hace falta: los repositorios ya lo usan
 *   automáticamente).
 *
 * ## Quién los emite y quién los consume
 *
 * - **Emite**: `DefaultSessionRepository`.
 * - **Consume**: `MainActivity` (para `Expired`), y cualquier
 *   componente que necesite reaccionar a un refresco de token.
 */
sealed interface SessionEvent {

    /**
     * La sesión ha dejado de ser válida.
     *
     * ## Cuándo se emite
     *
     * - Cuando `MoodleApiClient` no puede refrescar el token de
     *   Moodle (porque las credenciales guardadas ya no valen, porque
     *   las cookies del CAS han caducado, etc.).
     * - Cuando el usuario cambia la contraseña en otro dispositivo y
     *   el refresco falla.
     *
     * ## Qué debe hacer la UI
     *
     * Redirigir al usuario a la pantalla de login y limpiar la pila
     * de navegación. La sesión ya está cerrada en el repositorio;
     * solo falta que la UI se entere.
     *
     * ## Por qué es un `data object`
     *
     * Porque no lleva datos. Solo indica "la sesión ha expirado".
     * `data object` es la forma de Kotlin de representar un objeto
     * sin estado que se compara por identidad.
     */
    data object Expired : SessionEvent

    /**
     * El token de Moodle se ha refrescado de forma transparente.
     *
     * ## Cuándo se emite
     *
     * - Cuando `MoodleApiClient` detecta un `invalidtoken` y consigue
     *   refrescarlo.
     *
     * ## Qué debe hacer la UI
     *
     * Normalmente, nada. Los repositorios ya usan el token nuevo
     * automáticamente. El evento existe por si algún componente
     * necesita saber cuándo se ha refrescado (por ejemplo, para
     * actualizar una vista que mostraba información obsoleta).
     *
     * @property token Nuevo token válido para futuras peticiones.
     */
    data class TokenRefreshed(val token: String) : SessionEvent
}