package onion.gh0st.iMoodle.api.moodle.network

/**
 * Utilidades para detectar y lanzar errores de token en respuestas de
 * Moodle.
 *
 * ## Contexto
 *
 * Moodle devuelve los errores en el **cuerpo** de la respuesta HTTP,
 * no en el código HTTP. Es decir, una respuesta puede tener
 * `HTTP 200 OK` y, dentro del JSON, un error. Eso obliga a mirar el
 * cuerpo para detectar errores.
 *
 * Esta clase añade funciones de extensión sobre `String` para que
 * `MoodleApiClient` pueda hacer:
 *
 * ```
 * if (responseBody.hasInvalidTokenError()) { ... }
 * responseBody.throwIfMoodleException()
 * ```
 *
 * en lugar de inspeccionar el JSON a mano en cada sitio.
 *
 * ## Dos tipos de errores de Moodle
 *
 * Moodle tiene dos errores que, aunque parezcan similares, son muy
 * distintos:
 *
 * - **`invalidtoken`**: el token ya no vale (ha caducado o se ha
 *   revocado). Hay que refrescarlo.
 * - **`accessexception`**: el token **sí es válido**, pero el
 *   usuario no tiene permisos para esa función concreta. Por
 *   ejemplo, un alumno llamando a una función de administrador. No
 *   hay que refrescar nada: el usuario simplemente no puede hacer
 *   esa operación.
 *
 * Esta distinción es fundamental: si tratáramos `accessexception`
 * como "token inválido", la app cerraría la sesión cada vez que un
 * alumno intentara acceder a algo para lo que no tiene permiso. Eso
 * sería un desastre de usabilidad.
 *
 * ## Por qué se usan expresiones regulares en lugar de `org.json`
 *
 * Por dos motivos:
 *
 * 1. **Rendimiento**: las regex son más baratas que parsear el JSON
 *    completo cuando solo se quiere buscar un campo concreto.
 * 2. **Compatibilidad con tests**: `org.json` depende del runtime de
 *    Android, así que no está disponible en tests JVM puros. Las
 *    regex, en cambio, funcionan en cualquier sitio.
 */

/**
 * Comprueba si el cuerpo de una respuesta de Moodle representa un
 * error de token inválido.
 *
 * ## Cómo lo detecta
 *
 * Busca la cadena `"errorcode":"invalidtoken"` en el cuerpo. Antes
 * elimina todos los espacios en blanco para que el JSON esté
 * "compactado" y la búsqueda sea más fiable (Moodle a veces devuelve
 * el JSON con saltos de línea e indentación).
 *
 * ## Qué NO se considera token inválido
 *
 * - **`accessexception`**: el token es válido, pero el usuario no
 *   tiene permisos para esa función. Tratarlo como token inválido
 *   forzaría un cierre de sesión innecesario.
 * - Cualquier otro `errorcode`.
 *
 * @receiver Cuerpo de la respuesta de Moodle.
 * @return `true` si el cuerpo contiene un error de token inválido.
 */
internal fun String.hasInvalidTokenError(): Boolean {
    val compactBody = replace(Regex("\\s+"), "")
    return compactBody.contains("\"errorcode\":\"invalidtoken\"")
}

/** Regex para detectar el prefijo de un JSON de error de Moodle. */
private val EXCEPTION_PREFIX_REGEX = Regex("""^\s*\{\s*"exception"\s*:""")

/** Regex para extraer el campo `errorcode` de un JSON de error. */
private val ERROR_CODE_REGEX = Regex(""""errorcode"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/** Regex para extraer el campo `message` de un JSON de error. */
private val MESSAGE_REGEX = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/** Regex para encontrar secuencias de escape Unicode (`\uXXXX`). */
private val UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9a-fA-F]{4})""")

/**
 * Lanza [MoodleApiException] si el cuerpo es un JSON de error de
 * Moodle.
 *
 * ## Cómo lo detecta
 *
 * Comprueba si el cuerpo empieza por `{` seguido de `"exception"`.
 * Solo mira el **primer** campo del objeto raíz, así que una
 * respuesta correcta que contenga la palabra "exception" dentro de
 * sus datos no se confunde con un error.
 *
 * Por ejemplo, una respuesta como:
 *
 * ```
 * [{"name":"exception handling"}]
 * ```
 *
 * **no** se trata como error, porque el `exception` está dentro de un
 * array de datos, no como campo raíz.
 *
 * ## Qué hace
 *
 * - Si no es un error: no hace nada (devuelve `Unit`).
 * - Si es un error: extrae `errorcode` y `message` del JSON, los
 *   deserializa (por si tienen caracteres escapados) y lanza
 *   `MoodleApiException`.
 *
 * ## Antes de llamar a este método
 *
 * Los `invalidtoken` se tratan **antes**, con
 * [hasInvalidTokenError]. Este método se encarga del resto de errores
 * de Moodle (accessexception, invalidparameter, etc.).
 *
 * @receiver Cuerpo de la respuesta de Moodle.
 * @throws MoodleApiException si el cuerpo es un JSON de error de
 *   Moodle.
 */
internal fun String.throwIfMoodleException() {
    if (!EXCEPTION_PREFIX_REGEX.containsMatchIn(this)) return
    val errorCode = ERROR_CODE_REGEX.find(this)?.groupValues?.get(1)?.unescapeJson()
        ?: "unknown"
    val errorMessage = MESSAGE_REGEX.find(this)?.groupValues?.get(1)?.unescapeJson()
        ?: "Error de Moodle ($errorCode)"
    throw MoodleApiException(errorCode, errorMessage)
}

/**
 * Deserializa las secuencias de escape que puede contener un string
 * JSON.
 *
 * ## Qué secuencias maneja
 *
 * - **`\uXXXX`**: caracteres Unicode representados en hexadecimal.
 *   Por ejemplo, `\u00f3` es `ó`.
 * - **`\"`**: comillas dobles escapadas.
 * - **`\/`**: barras inclinadas escapadas (JSON permite escaparlas,
 *   aunque no es obligatorio).
 * - **`\n`**: saltos de línea.
 * - **`\\`**: barras invertidas escapadas.
 *
 * ## Por qué es necesario
 *
 * El campo `message` de Moodle puede contener caracteres especiales
 * escapados (por ejemplo, tildes codificadas como `\u00f3`). Sin
 * deserializarlos, el usuario vería `Funci\u00f3n no disponible` en
 * lugar de `Función no disponible`.
 *
 * @receiver Cadena con secuencias de escape.
 * @return La cadena con las secuencias ya resueltas.
 */
private fun String.unescapeJson(): String =
    replace(UNICODE_ESCAPE_REGEX) { matchResult ->
        matchResult.groupValues[1].toInt(16).toChar().toString()
    }
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")