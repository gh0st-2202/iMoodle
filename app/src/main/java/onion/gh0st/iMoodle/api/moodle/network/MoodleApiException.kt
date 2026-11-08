package onion.gh0st.iMoodle.api.moodle.network

/**
 * Error devuelto por la API REST de Moodle que **no** es un problema
 * de autenticación.
 *
 * ## Qué representa
 *
 * Moodle devuelve errores de muchas formas distintas. Algunos son
 * problemas de autenticación (token caducado, credenciales malas);
 * esos se gestionan aparte, con `AuthException`. Los demás (permisos
 * insuficientes, parámetros inválidos, error interno del servidor,
 * etc.) se representan con esta clase.
 *
 * ## Por qué existe una clase propia en lugar de `Exception` genérica
 *
 * Porque los repositorios necesitan distinguir "error de Moodle" de
 * "error de red" o "error de parseo". Si todo fuera `Exception`, no
 * podrían hacer `when` sobre el tipo. Con esta clase, pueden hacer:
 *
 * ```
 * try {
 *     api.callREST("core_x")
 * } catch (error: MoodleApiException) {
 *     // Error específico de Moodle, con código y mensaje
 * } catch (error: Exception) {
 *     // Otros errores (red, cancelación…)
 * }
 * ```
 *
 * ## Cómo se rellena
 *
 * Se construye a partir de:
 *
 * - Un JSON de error de Moodle (`{"exception": "...", "errorcode": "...", "message": "..."}`).
 * - Una respuesta HTTP no exitosa (por ejemplo, un 500 del servidor).
 *
 * @property errorCode Código de error de Moodle. Por ejemplo,
 *   `"accessexception"` (sin permisos), `"invalidparameter"`
 *   (parámetro incorrecto), o `"http_500"` (error HTTP 500 del
 *   servidor, que no viene de Moodle como tal sino del transporte).
 */
class MoodleApiException(
    val errorCode: String,
    message: String,
) : Exception(message)