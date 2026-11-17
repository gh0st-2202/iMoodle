package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import java.io.IOException

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
 * **Solo cuando el fallo es definitivo**, es decir, cuando no hay
 * ninguna posibilidad de obtener un token sin volver a pedirle las
 * credenciales al usuario:
 *
 * - La redirección no contiene `token=`. Es lo que ocurre cuando las
 *   cookies del CAS también han caducado y Moodle manda al login en
 *   su lugar.
 * - El servidor responde `401`/`403`, señal de que la sesión CAS ya
 *   no está autorizada.
 * - El token decodificado no tiene el formato esperado.
 *
 * En esos casos, el `MoodleApiClient` interpretará el `null` como
 * "hace falta un login manual" y cerrará la sesión.
 *
 * ## Cuándo lanza una excepción
 *
 * Los fallos **transitorios** no se convierten en `null`: se lanzan
 * como [AuthException.Network] (sin conexión, timeout, `429`…) o
 * [AuthException.ServerError] (HTTP 5xx). Así el `MoodleApiClient`
 * falla solo la petición actual y la sesión del usuario se conserva;
 * basta con reintentar cuando el servidor o la red vuelvan.
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
     * - **Sin conexión, timeout u otro fallo de transporte**: se lanza
     *   [AuthException.Network]. La sesión **no** se cierra.
     * - **HTTP 5xx**: se lanza [AuthException.ServerError]. La sesión
     *   **no** se cierra.
     * - **Códigos 4xx distintos de 401/403**: también se tratan como
     *   transitorios ([AuthException.Network]), porque no demuestran
     *   que las credenciales hayan sido rechazadas.
     * - **401/403 o redirección sin `token=`**: se devuelve `null`,
     *   que significa "definitivamente no se puede refrescar".
     * - **Cancelación de la corrutina**: se propaga tal cual, para
     *   respetar la cancelación estructurada.
     *
     * @return El token nuevo, o `null` si hace falta un login manual.
     * @throws AuthException.Network si el fallo es transitorio de red.
     * @throws AuthException.ServerError si el servidor responde 5xx.
     */
    override suspend fun refresh(): String? {
        val response = try {
            noRedirectClient.newCall(
                Request.Builder()
                    .url(config.launchUrl)
                    .header("User-Agent", config.userAgent)
                    .build(),
            ).await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AuthException) {
            throw exception
        } catch (exception: Exception) {
            throw AuthException.Network(exception)
        }

        return response.use { launchResponse ->
            val httpCode = launchResponse.code
            when {
                httpCode in 500..599 -> throw AuthException.ServerError(httpCode)
                httpCode == 401 || httpCode == 403 -> null
                httpCode in 400..499 -> throw AuthException.Network(
                    IOException("HTTP $httpCode inesperado en launch.php"),
                )
                else -> {
                    val redirectLocation = launchResponse.header("Location").orEmpty()
                    if (!redirectLocation.contains("token=")) return@use null
                    redirectLocation.decodeWstokenOrNull()
                }
            }
        }
    }
}