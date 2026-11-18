package onion.gh0st.iMoodle.api.moodle.network

import android.util.Log
import onion.gh0st.iMoodle.BuildConfig

/**
 * Trazas de las llamadas a los *web services* de Moodle.
 *
 * ## Qué registra
 *
 * - La función invocada (`mod_wiki_get_page_for_editing`, …).
 * - Los parámetros enviados, con los valores sensibles redactados.
 * - El cuerpo JSON de la respuesta, recortado si es muy largo.
 * - El tiempo que tardó y, si falla, el tipo y el mensaje del error.
 *
 * ## Qué NO registra nunca
 *
 * - **El `wstoken`**: no forma parte del mapa de parámetros; lo añade
 *   [MoodleApiClient] al firmar la petición, así que no pasa por aquí.
 * - **Credenciales**: el login (CAS y `login/token.php`) no usa este
 *   cliente. Además, cualquier parámetro cuyo nombre contenga `token`,
 *   `password`, `passwd` o `secret` se sustituye por `***`.
 * - **HTML de otras pantallas**: esto solo se llama desde
 *   [MoodleApiClient], que únicamente habla con
 *   `webservice/rest/server.php` y recibe JSON.
 *
 * El logger HTTP anterior volcaba cuerpos de respuesta de cualquier
 * origen y filtró una contraseña del CAS (§9.40). Por eso este es
 * deliberadamente más estrecho: no es un interceptor de OkHttp.
 *
 * ## Cuándo escribe
 *
 * Solo en compilaciones `debug` ([BuildConfig.DEBUG]). En release las
 * funciones salen inmediatamente sin tocar Logcat.
 *
 * Para verlo: filtrar Logcat por la etiqueta [TAG] o ejecutar
 * `adb logcat -s iMoodleNet`.
 */
internal object NetworkLogger {

    /** Etiqueta de Logcat que identifica las llamadas a Moodle. */
    const val TAG = "iMoodleNet"

    /** Longitud máxima del cuerpo que se vuelca (Logcat corta sobre 4 000). */
    private const val MAX_BODY_LENGTH = 3_000

    /** Texto que sustituye a un valor sensible. */
    private const val REDACTED = "***"

    /** Nombres de parámetro que nunca deben aparecer en claro. */
    private val SENSITIVE_KEY = Regex("(?i)(token|password|passwd|secret)")

    /**
     * Registra el inicio de una petición.
     *
     * @param function Función de Moodle que se va a ejecutar.
     * @param params Parámetros ya aplanados.
     */
    fun request(function: String, params: Map<String, String>) {
        if (!BuildConfig.DEBUG) return

        val printable = params.entries.joinToString(separator = ", ") { (key, value) ->
            val shownValue = if (SENSITIVE_KEY.containsMatchIn(key)) REDACTED else value
            "$key=$shownValue"
        }
        Log.d(TAG, "→ $function ($printable)")
    }

    /**
     * Registra la respuesta de una petición.
     *
     * @param function Función de Moodle ejecutada.
     * @param body Cuerpo devuelto por Moodle.
     * @param elapsedMs Milisegundos que tardó la petición.
     */
    fun response(function: String, body: String, elapsedMs: Long) {
        if (!BuildConfig.DEBUG) return

        val shownBody = if (body.length > MAX_BODY_LENGTH) {
            body.take(MAX_BODY_LENGTH) + "… (recortado)"
        } else {
            body
        }
        Log.d(TAG, "← $function ${elapsedMs}ms $shownBody")
    }

    /**
     * Registra un fallo de red o de Moodle.
     *
     * @param function Función de Moodle ejecutada.
     * @param error Fallo producido.
     */
    fun failure(function: String, error: Throwable) {
        if (!BuildConfig.DEBUG) return

        Log.w(TAG, "✖ $function ${error.javaClass.simpleName}: ${error.message}")
    }
}
