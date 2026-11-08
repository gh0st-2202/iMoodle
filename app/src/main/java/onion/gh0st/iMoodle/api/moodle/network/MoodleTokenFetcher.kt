package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.api.cas.CasSession
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig

/**
 * Obtiene el `wstoken` de Moodle a partir de una sesión CAS ya
 * autenticada.
 *
 * ## Qué es el `wstoken`
 *
 * Es el "pase" que Moodle emite para que una aplicación pueda hacer
 * peticiones a su API REST en nombre de un usuario. A diferencia de
 * las cookies del CAS (que sirven para cualquier servicio de la
 * Junta), el `wstoken` es específico de Moodle: solo sirve para
 * hablar con la API REST de Moodle.
 *
 * ## Cómo se obtiene
 *
 * Moodle expone un endpoint interno (`launch.php`) que emite el token
 * cuando el usuario ya está autenticado (es decir, cuando el cliente
 * HTTP tiene las cookies del CAS). Ese endpoint responde con un `302`
 * cuya cabecera `Location` contiene el token codificado en Base64.
 *
 * Esta clase hace exactamente eso:
 *
 * 1. Pide `launch.php` **sin seguir redirecciones** (para poder leer
 *    la cabecera `Location`).
 * 2. Extrae la parte de la URL que contiene el token.
 * 3. La decodifica en Base64.
 * 4. Del resultado, extrae la parte que corresponde al token.
 * 5. Construye una `MoodleSession` completa con:
 *    - El usuario de la `CasSession` original.
 *    - El gestor de cookies de la `CasSession`.
 *    - El cliente HTTP de la `CasSession`.
 *    - Un `MoodleApiClient` ya configurado con el token y el
 *      refresco automático.
 *
 * ## Por qué se ejecuta en un hilo aparte
 *
 * Porque hace peticiones HTTP, que pueden tardar. Se ejecuta en
 * `Dispatchers.IO` para no congelar la interfaz.
 *
 * @param config Configuración de Moodle de la provincia. Determina la
 *   URL de `launch.php` y el User-Agent.
 * @param baseClient Cliente HTTP base a partir del cual se construyen
 *   los clientes internos. Se usa para clonar y configurar las
 *   peticiones.
 * @param onTokenRefreshed Callback que se invoca cuando el
 *   `MoodleApiClient` obtiene un token nuevo de forma transparente.
 *   Normalmente, `DefaultSessionRepository` lo usa para mantener el
 *   estado reactivo sincronizado.
 * @param onInvalidToken Callback que se invoca cuando el token deja
 *   de valer y no se ha podido refrescar. Normalmente,
 *   `DefaultSessionRepository` lo usa para forzar el cierre de
 *   sesión.
 */
class MoodleTokenFetcher(
    private val config: MoodleConfig,
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val onTokenRefreshed: suspend (String) -> Unit = {},
    private val onInvalidToken: suspend () -> Unit = {},
) {

    /**
     * Extrae el token de `launch.php` y construye la `MoodleSession`
     * completa.
     *
     * ## Comportamiento con errores
     *
     * - **Errores tipados de autenticación** (`AuthException`) se
     *   propagan tal cual. El llamador (la pantalla de login) sabe
     *   traducirlos a mensajes para el usuario.
     * - **Cualquier otro error** (red, parseo inesperado) se envuelve
     *   en `AuthException.Network` para que el llamador lo trate como
     *   un problema de conexión.
     * - **La excepción de cancelación** se propaga tal cual, para
     *   respetar la cancelación estructurada.
     *
     * ## Por qué se ejecuta en `Dispatchers.IO`
     *
     * La petición HTTP puede tardar varios segundos. Hacerla en el
     * hilo principal congelaría la interfaz.
     *
     * @param casSession Sesión CAS ya autenticada (con las cookies del
     *   usuario).
     * @return La `MoodleSession` completa, lista para hacer peticiones
     *   a la API REST de Moodle.
     * @throws AuthException si el flujo falla en cualquier paso.
     */
    suspend fun fetchToken(casSession: CasSession): MoodleSession =
        try {
            withContext(Dispatchers.IO) { doFetch(casSession) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AuthException) {
            throw exception
        } catch (exception: Exception) {
            throw AuthException.Network(exception)
        }

    /**
     * Implementación real del flujo. No gestiona errores genéricos:
     * deja que [fetchToken] se encargue.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pide `launch.php` sin seguir redirecciones y lee la cabecera
     *    `Location`.
     * 2. Comprueba que la `Location` contiene `token=`. Si no, el
     *    flujo ha cambiado y lanza `AuthException.NoRedirectToken`.
     * 3. Decodifica el token del Base64.
     * 4. Construye una `MoodleSession` con todos los datos.
     *
     * @param casSession Sesión CAS autenticada.
     * @return La `MoodleSession` lista.
     * @throws AuthException.NoRedirectToken si la redirección no
     *   contiene `token=`.
     * @throws AuthException.BadTokenFormat si el token no se puede
     *   decodificar.
     */
    private suspend fun doFetch(casSession: CasSession): MoodleSession {
        val redirectLocation = fetchLaunchLocation(casSession)
        if (!redirectLocation.contains("token=")) {
            throw AuthException.NoRedirectToken(redirectLocation)
        }
        val wstoken = decodeWstoken(redirectLocation)
        return MoodleSession(
            user = casSession.user,
            cookieManager = casSession.cookieManager,
            client = casSession.client,
            api = buildApi(casSession, wstoken),
        )
    }

    /**
     * Pide `launch.php` sin seguir redirecciones para leer la
     * cabecera `Location`.
     *
     * ## Por qué sin seguir redirecciones
     *
     * Si el cliente sigue la redirección, acaba cargando la página
     * HTML que hay al final y pierde la cabecera `Location`, que es
     * donde va el token. Pidiéndola sin seguir, leemos la cabecera
     * directamente.
     *
     * @param casSession Sesión CAS autenticada (con las cookies).
     * @return El valor de la cabecera `Location`, o cadena vacía si
     *   no la hay.
     */
    private suspend fun fetchLaunchLocation(casSession: CasSession): String {
        val noFollowClient = casSession.client.newBuilder().followRedirects(false).build()
        return noFollowClient.newCall(
            Request.Builder()
                .url(config.launchUrl)
                .header("User-Agent", config.userAgent)
                .build(),
        ).await().use { launchResponse ->
            launchResponse.header("Location").orEmpty()
        }
    }

    /**
     * Construye el `MoodleApiClient` para la sesión autenticada.
     *
     * ## Qué hace
     *
     * Crea un `MoodleApiClient` configurado con:
     *
     * - El cliente HTTP de la `CasSession` (que ya lleva las cookies).
     * - La configuración de Moodle de la provincia.
     * - Un `CookiesBasedTokenRefresher` (para refrescar el token
     *   reutilizando las cookies del CAS cuando caduque).
     * - Los callbacks de refresco e invalidación que ha recibido esta
     *   clase.
     * - El token inicial recién obtenido.
     *
     * @param casSession Sesión CAS autenticada.
     * @param token Token inicial de Moodle.
     * @return El `MoodleApiClient` listo para hacer peticiones.
     */
    private fun buildApi(casSession: CasSession, token: String): MoodleApiClient =
        MoodleApiClient(
            client = casSession.client,
            config = config,
            tokenRefresher = CookiesBasedTokenRefresher(casSession.client, config),
            onTokenRefreshed = onTokenRefreshed,
            onInvalidToken = onInvalidToken,
            initialToken = token,
        )

    /**
     * Decodifica el `wstoken` del query string de la redirección.
     *
     * ## Formato del token
     *
     * El token viene codificado en Base64 (variante URL-safe) y, una
     * vez decodificado, tiene un formato con tres partes separadas
     * por `:::`:
     *
     * ```
     * firma:::token:::privado
     * ```
     *
     * En versiones antiguas, el separador era `:` simple.
     *
     * De todas las partes, la que nos interesa es la **segunda** (el
     * token en sí).
     *
     * ## Por qué se normaliza el Base64
     *
     * El Base64 "URL-safe" usa `-` y `_` en lugar de `+` y `/`. Antes
     * de decodificar, hay que convertir los caracteres URL-safe a los
     * estándar. Además, el padding (`=`) a veces viene recortado, así
     * que lo recalculamos para que la longitud sea múltiplo de 4.
     *
     * @param redirectLocation Cabecera `Location` de la respuesta de
     *   `launch.php`.
     * @return El token limpio.
     * @throws AuthException.BadTokenFormat si el token no se puede
     *   decodificar o no tiene el formato esperado.
     */
    private fun decodeWstoken(redirectLocation: String): String {
        val rawToken = redirectLocation
            .substringAfter("token=")
            .substringBefore("&")
        val normalizedToken = rawToken
            .replace('-', '+')
            .replace('_', '/')
        val paddedToken = normalizedToken + "=".repeat((4 - normalizedToken.length % 4) % 4)
        val decodedToken = paddedToken.decodeBase64()?.utf8()
            ?: throw AuthException.BadTokenFormat()
        return decodedToken.split(":::").getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: decodedToken.split(":").getOrNull(2)?.takeIf { it.isNotEmpty() }
            ?: throw AuthException.BadTokenFormat()
    }
}