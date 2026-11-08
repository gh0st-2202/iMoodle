package onion.gh0st.iMoodle.api.moodle.auth

/**
 * Catálogo de errores que pueden ocurrir durante el proceso de
 * autenticación completo contra la Junta (CAS + Moodle).
 *
 * ## Por qué existe esta clase
 *
 * Cuando un usuario intenta iniciar sesión, pueden fallar muchas
 * cosas distintas: que la contraseña esté mal, que el servidor de la
 * Junta esté caído, que el formulario de login haya cambiado, que la
 * red del móvil no funcione… Si cada una de esas situaciones lanzara
 * una excepción genérica (`Exception`, `IOException`, etc.), la
 * pantalla de login no podría distinguir "contraseña incorrecta" de
 * "sin conexión" y el usuario vería siempre el mismo mensaje
 * desconcertante.
 *
 * Para evitarlo, esta clase define **un tipo de error por cada
 * situación significativa**. La pantalla de login puede entonces
 * hacer algo así:
 *
 * ```
 * when (error) {
 *     es LoginFailed           → "Usuario o contraseña incorrectos"
 *     es Network               → "Sin conexión a internet"
 *     es NoExecutionToken      → "El servidor de la Junta ha cambiado"
 *     ...
 * }
 * ```
 *
 * Como la clase es `sealed` (sellada), el compilador **obliga** a
 * contemplar todos los casos posibles. Si en el futuro se añade un
 * tipo nuevo de error, cualquier `when` que no lo contemple dará error
 * de compilación. Así es imposible olvidarse de traducir un error a
 * un mensaje para el usuario.
 *
 * ## Cómo se organizan los errores
 *
 * Los errores se agrupan en tres bloques según en qué punto del flujo
 * ocurren:
 *
 * 1. **Errores del CAS** (el servidor de identificación de la Junta):
 *    credenciales incorrectas, formulario inesperado, ausencia de la
 *    cookie que demuestra que el login ha ido bien.
 * 2. **Errores de Moodle** (el servicio al que se accede después):
 *    respuesta inesperada al pedir el token de la API, formato del
 *    token incorrecto, token caducado sin posibilidad de refresco.
 * 3. **Errores de red**: cualquier fallo de transporte (sin
 *    conexión, timeout, DNS, socket cerrado…).
 *
 * @param message Mensaje técnico del error. Se usa para logs, no para
 *   mostrar al usuario final.
 * @param cause Excepción original que provocó este error, si la hay.
 *   Permite reconstruir la cadena completa para depuración.
 */
sealed class AuthException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    // --- Errores del CAS ---

    /**
     * El `POST` con las credenciales ha devuelto un código HTTP
     * distinto de 2xx (por ejemplo, un 500 del servidor).
     *
     * ## Cuándo se lanza
     *
     * El flujo del CAS no ha podido completarse porque el servidor
     * ha respondido con un error HTTP en el paso de envío de
     * credenciales. No es lo mismo que "contraseña incorrecta": en
     * ese caso el CAS responde con un `302` de vuelta al formulario,
     * no con un error HTTP. Aquí se trata de un fallo real del
     * servidor.
     *
     * @param reason Descripción breve del fallo (por ejemplo,
     *   `"HTTP 500"`).
     */
    class LoginFailed(reason: String) : AuthException("Login CAS fallido: $reason")

    /**
     * El HTML del formulario de login del CAS ya no contiene el campo
     * oculto `execution`.
     *
     * ## Qué significa
     *
     * El CAS incluye un token anti-fraude (`execution`) en cada
     * formulario de login. Si el HTML ya no lo tiene, es porque la
     * Junta ha cambiado el diseño de su página y esta app ya no sabe
     * rellenar el formulario. No es un fallo del usuario: es un
     * cambio en el servidor que habría que adaptar.
     */
    class NoExecutionToken : AuthException(
        "No se encontró 'execution' en el HTML del CAS",
    )

    /**
     * El login CAS ha terminado sin que aparezca la cookie de Moodle.
     *
     * ## Qué significa
     *
     * Cuando el CAS acepta las credenciales, redirige al servicio de
     * destino (Moodle de la provincia), que emite una cookie llamada
     * `MoodleSession`. Si tras todo el flujo esa cookie no está, las
     * dos causas más probables son:
     *
     * - El usuario o la contraseña son incorrectos.
     * - El servicio de Moodle está teniendo problemas y no ha podido
     *   emitir la cookie.
     *
     * En la práctica, la pantalla de login lo traduce como
     * "credenciales incorrectas", porque es el caso más habitual.
     */
    class NoMoodleSession : AuthException(
        "Login OK pero sin cookie MoodleSession",
    )

    // --- Errores de Moodle ---

    /**
     * La petición de token a Moodle no ha devuelto una URL con
     * `token=`.
     *
     * ## Qué significa
     *
     * Para obtener el `wstoken` de Moodle, esta app hace una petición
     * a un endpoint interno (`launch.php`) que responde con una
     * redirección (`302`) cuya cabecera `Location` contiene el token.
     * Si esa redirección no contiene la palabra `token=`, el flujo ha
     * cambiado y no podemos extraerlo.
     *
     * @param location Valor de la cabecera `Location` recibida. Se
     *   incluye en el mensaje para depuración.
     */
    class NoRedirectToken(location: String) : AuthException(
        "No hay 'token=' en la redirección: $location",
    )

    /**
     * El token decodificado no tiene el formato esperado.
     *
     * ## Qué significa
     *
     * El `wstoken` viene codificado en Base64 y, una vez decodificado,
     * tiene un formato concreto (varias partes separadas por `:::` o
     * `:`). Si al decodificarlo no encontramos la parte que
     * corresponde al token, es que el formato ha cambiado.
     *
     * ## Nota de seguridad
     *
     * El mensaje **no incluye** el contenido recibido. Un token real
     * podría acabar en un log o en un toast si lo incluyéramos, y eso
     * sería una fuga de seguridad.
     */
    class BadTokenFormat : AuthException(
        "Formato de token inesperado",
    )

    /**
     * El `wstoken` de Moodle ha caducado y no se ha podido refrescar
     * automáticamente.
     *
     * ## Qué significa
     *
     * Moodle emite tokens de corta duración. Cuando uno caduca, la
     * app intenta obtener uno nuevo de forma transparente (sin pedir
     * la contraseña otra vez). Si ese refresco falla (porque la
     * contraseña ha cambiado, porque la sesión se ha cerrado en otro
     * dispositivo, etc.), se lanza este error. La app reacciona
     * cerrando la sesión y mandando al usuario a la pantalla de
     * login.
     */
    class InvalidToken : AuthException(
        "Token inválido y no se pudo refrescar (hace falta re-loguear)",
    )

    // --- Errores de red ---

    /**
     * Cualquier fallo de transporte: timeout, DNS, socket cerrado,
     * falta de conexión, etc.
     *
     * ## Por qué se incluye el tipo de la causa
     *
     * El mensaje resultante incluye el nombre de la clase de la
     * excepción original (`UnknownHostException`, `SocketTimeoutException`,
     * etc.) para que, al mirar los logs, sea fácil saber qué tipo de
     * problema de red ha ocurrido. Al usuario final no se le muestra
     * este detalle: la pantalla de login lo traduce a un mensaje
     * genérico de "sin conexión".
     *
     * @param cause Excepción original que provocó el fallo.
     */
    class Network(cause: Throwable) : AuthException(
        buildString {
            append("Error de red [").append(cause::class.simpleName).append("]: ")
            append(cause.message ?: "(sin mensaje)")
        },
        cause,
    )
}