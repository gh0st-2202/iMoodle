package onion.gh0st.iMoodle.core

import android.util.Log

/**
 * Helper de registro (logging) seguro para toda la app.
 *
 * ## Qué es un log
 *
 * Un log es un mensaje que la app escribe en un "cuaderno" interno
 * (logcat en Android) para que los desarrolladores puedan
 * diagnosticar problemas. Los usuarios no los ven: solo los ven los
 * desarrolladores cuando conectan el móvil a un ordenador.
 *
 * ## Qué problema resuelve esta clase
 *
 * Cuando algo falla (una petición de red, un parseo, etc.), el
 * mensaje de error técnico es muy útil para el desarrollador, pero
 * **no** debe mostrarse al usuario: sería confuso y, en algunos
 * casos, podría filtrar información sensible (URLs internas,
 * mensajes del servidor, etc.).
 *
 * La estrategia que sigue esta clase es:
 *
 * - **Al usuario**: mensajes genéricos y amables (`"Ha ocurrido un
 *   error inesperado"`, `"Sin conexión a internet"`).
 * - **Al log**: el detalle técnico completo, para que el
 *   desarrollador pueda investigar.
 *
 * Esta clase se encarga de la parte del log.
 *
 * ## Regla de oro: nunca loguear datos sensibles
 *
 * Hay información que **nunca** debe aparecer en un log:
 *
 * - Contraseñas.
 * - Tokens de sesión.
 * - Cuerpos de respuesta HTTP completos (pueden contener datos
 *   personales).
 * - Cookies.
 *
 * Si alguien accede al log (por ejemplo, con una app maliciosa con
 * permisos de depuración), podría leer esa información. Esta clase
 * solo loguea el **tipo** y el **mensaje** de la excepción, no los
 * datos.
 *
 * ## Compatibilidad con tests
 *
 * `android.util.Log` es una clase de Android. En tests unitarios
 * puros (JVM, sin emulador), esa clase no existe y lanzaría una
 * excepción al llamarla. Para que los tests no exploten, esta clase
 * captura la excepción y cae a `System.err` (la salida de error
 * estándar de Java), que sí funciona en tests. En producción, `Log.e`
 * funciona normal y escribe en logcat.
 */
internal object Logging {

    /**
     * Registra un error de red o de API con contexto.
     *
     * ## Qué información se loguea
     *
     * - La etiqueta (`tag`), normalmente el nombre de la clase.
     * - Una descripción corta de la operación que falló.
     * - El tipo y el mensaje de la excepción.
     * - La traza completa (stack trace).
     *
     * ## Qué NO se loguea
     *
     * - Datos de la excepción que puedan contener información
     *   sensible. Por eso se usa `exception::class.simpleName` y
     *   `exception.message` en lugar de, por ejemplo, el cuerpo de una
     *   respuesta HTTP.
     *
     * ## Formato del mensaje
     *
     * ```
     * <operación> falló: <tipo de excepción> - <mensaje de la excepción>
     * ```
     *
     * Por ejemplo:
     *
     * ```
     * refreshCourses(userId=42) falló: UnknownHostException - Unable to resolve host
     * ```
     *
     * @param tag Etiqueta de log. Normalmente el nombre de la clase
     *   que llama. Ayuda a filtrar los logs en logcat.
     * @param operation Descripción corta de la operación que falló.
     *   Debe ser lo bastante específica para que, al verla en el
     *   log, se entienda qué se estaba intentando hacer. Por ejemplo,
     *   `"refreshCourses(userId=42)"`.
     * @param exception Excepción capturada.
     */
    fun logError(tag: String, operation: String, exception: Throwable) {
        val message = "$operation falló: ${exception::class.simpleName} - ${exception.message}"
        try {
            Log.e(tag, message, exception)
        } catch (notMockedException: RuntimeException) {
            System.err.println("[$tag] $message")
            exception.printStackTrace(System.err)
        }
    }
}