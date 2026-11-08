package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig

/**
 * Estrategia de refresco del token de Moodle basada en las cookies
 * del CAS.
 *
 * ## Qué problema resuelve
 *
 * Los tokens de Moodle caducan al cabo de un rato. Cuando eso pasa,
 * la app tiene que obtener uno nuevo. La forma más obvia sería volver
 * a hacer el login completo (usuario + contraseña + CAS + Moodle),
 * pero eso es lento y, sobre todo, requiere tener la contraseña
 * guardada.
 *
 * Esta clase implementa una forma mucho más ligera: **reutilizar las
 * cookies del CAS**. Resulta que, aunque el token de Moodle haya
 * caducado, las cookies del CAS pueden seguir siendo válidas durante
 * más tiempo. Si lo son, basta con pedir un token nuevo a Moodle
 * usando esas cookies: no hace falta volver a autenticarse.
 *
 * ## Cómo funciona
 *
 * 1. Hace una petición `GET` a `launch.php` (el mismo endpoint que se
 *    usa en el login inicial) **sin seguir redirecciones**.
 * 2. La respuesta es un `302` con una cabecera `Location` que
 *    contiene el token nuevo codificado en Base64.
 * 3. Decodifica el token y lo devuelve.
 *
 * ## Por qué sin seguir redirecciones
 *
 * Porque si el cliente sigue la redirección, acabaría cargando la
 * página HTML que hay al final, y perderíamos la cabecera `Location`,
 * que es donde va el token. Pidiéndola sin seguir, podemos leer la
 * cabecera directamente.
 *
 * ## Cuándo devuelve `null`
 *
 * - Si la redirección no contiene `token=` (por ejemplo, porque las
 *   cookies del CAS también han caducado y Moodle nos manda al login
 *   en su lugar).
 * - Si el token decodificado no tiene el formato esperado.
 * - Si la petición falla por cualquier motivo de red.
 *
 * En todos esos casos, el `MoodleApiClient` interpretará el `null`
 * como "no se puede refrescar" y cerrará la sesión. El usuario
 * tendrá que volver a loguearse manualmente.
 *
 * @param authenticatedClient Cliente HTTP con las cookies del CAS ya
 *   cargadas. La clase lo clona internamente para desactivar el
 *   seguimiento de redirecciones.
 * @param config Configuración de Moodle de la provincia. Se usa para
 *   saber la URL de `launch.php` y el User-Agent.
 */
class CookiesBasedTokenRefresher(
    authenticatedClient: OkHttpClient,
    private val config: MoodleConfig,
) : TokenRefresher {

    /**
     * Cliente HTTP sin seguimiento de redirecciones.
     *
     * ## Por qué es un cliente distinto
     *
     * El cliente que llega por constructor sigue redirecciones
     * automáticamente (es lo que queremos para las peticiones
     * normales). Pero para leer la cabecera `Location` de la
     * respuesta `302` de `launch.php`, necesitamos que el cliente
     * **no** la siga. Si la siguiera, OkHttp cargaría la página final
     * y la `Location` ya no estaría disponible.
     *
     * ## Coste
     *
     * Clonar el cliente es barato: comparte el pool de conexiones y
     * los hilos con el original. Solo cambia la política de
     * redirecciones.
     */
    private val noRedirectClient: OkHttpClient = authenticatedClient.newBuilder()
        .followRedirects(false)
        .build()

    /**
     * Intenta obtener un token nuevo reutilizando las cookies del CAS.
     *
     * ## Comportamiento con errores
     *
     * - **Cualquier error de red** (timeout, sin conexión, error HTTP)
     *   se traga y devuelve `null`. El `MoodleApiClient` interpretará
     *   el `null` como "no se pudo refrescar" y cerrará la sesión.
     * - **La excepción de cancelación de corrutinas** sí se propaga,
     *   para respetar la cancelación estructurada. Si el usuario
     *   cierra la pantalla a mitad del refresco, no queremos que la
     *   corrutina siga viva.
     *
     * ## Por qué no propaga excepciones
     *
     * Porque el `MoodleApiClient` está diseñado para tratar el
     * refresco como "todo o nada": o devuelve un token, o `null`. Si
     * esta clase lanzara excepciones tipadas, el cliente tendría que
     * hacer `try/catch` y el código sería más enrevesado.
     *
     * @return El token nuevo, o `null` si no se ha podido obtener.
     */
    override suspend fun refresh(): String? {
        return try {
            val redirectLocation = noRedirectClient.newCall(
                Request.Builder()
                    .url(config.launchUrl)
                    .header("User-Agent", config.userAgent)
                    .build(),
            ).await().use { launchResponse ->
                launchResponse.header("Location").orEmpty()
            }

            if (!redirectLocation.contains("token=")) return null
            decodeWstoken(redirectLocation)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }

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
     * En versiones antiguas, el separador era `:` simple:
     *
     * ```
     * firma:token:privado
     * ```
     *
     * De todas las partes, la que nos interesa es la **segunda** (el
     * token en sí). Las otras son la firma y un valor privado que no
     * usamos.
     *
     * ## Por qué se normaliza el Base64
     *
     * El Base64 "URL-safe" usa `-` y `_` en lugar de `+` y `/`. Antes
     * de decodificar, hay que convertir los caracteres URL-safe a los
     * estándar. Además, el padding (`=`) a veces viene recortado,
     * así que lo recalculamos para que la longitud sea múltiplo de 4.
     *
     * @param redirectLocation Cabecera `Location` de la respuesta de
     *   `launch.php`.
     * @return El token limpio, o `null` si el formato no es válido.
     */
    private fun decodeWstoken(redirectLocation: String): String? {
        val rawToken = redirectLocation
            .substringAfter("token=")
            .substringBefore("&")
        val normalizedToken = rawToken
            .replace('-', '+')
            .replace('_', '/')
        val paddedToken = normalizedToken + "=".repeat((4 - normalizedToken.length % 4) % 4)
        val decodedToken = paddedToken.decodeBase64()?.utf8() ?: return null
        return decodedToken.split(":::").getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: decodedToken.split(":").getOrNull(2)?.takeIf { it.isNotEmpty() }
    }
}