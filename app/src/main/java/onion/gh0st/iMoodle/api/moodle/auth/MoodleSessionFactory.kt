package onion.gh0st.iMoodle.api.moodle.auth

import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.domain.model.session.SessionContext
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fábrica que reconstruye una `MoodleSession` "viva" a partir de un
 * `SessionContext` "puro".
 *
 * ## Por qué existe esta clase
 *
 * En el dominio de la app (`domain/`) solo existen objetos "puros":
 * datos sin infraestructura. Un `SessionContext` tiene provincia,
 * usuario y token, pero no tiene cliente HTTP ni cookies, porque esos
 * son detalles de implementación.
 *
 * Sin embargo, para hacer peticiones reales a Moodle hacen falta
 * cookies y un `OkHttpClient` configurado. Esta fábrica es la que
 * convierte un `SessionContext` puro en una `MoodleSession` viva y
 * utilizable.
 *
 * ## Cuándo se usa
 *
 * Se invoca en dos flujos:
 *
 * 1. **Login**: tras `AuthRepositoryImpl.login`, cuando ya se tiene un
 *    `SessionContext` recién creado.
 * 2. **Auto-login**: tras `AuthRepositoryImpl.tryAutoLogin`, cuando
 *    se recupera una sesión guardada del disco.
 *
 * En ambos casos, el `DefaultSessionRepository` recibe el
 * `SessionContext` y le pide a esta fábrica que construya la
 * `MoodleSession` para poder usarla en las peticiones de la app.
 *
 * ## Qué construye exactamente
 *
 * - Un `CookieManager` vacío (todavía no hay cookies; se llenarán al
 *   hacer la primera petición autenticada).
 * - Un `OkHttpClient` clonado del global, con el `CookieManager`
 *   enganchado.
 * - Un `MoodleApiClient` configurado para hablar con la instancia de
 *   Moodle de la provincia, con su `TokenRefresher` ya inyectado.
 * - La `MoodleSession` que agrupa todo lo anterior.
 *
 * @param httpClient Cliente HTTP base compartido por toda la app. Se
 *   clona para engancharle el `CookieManager` de la nueva sesión,
 *   pero comparte el pool de conexiones.
 * @param credentialsStore Almacén cifrado de credenciales. Se inyecta
 *   en el `MoodleApiClient` para que, cuando el token caduque, pueda
 *   pedir uno nuevo sin molestar al usuario.
 */
@Singleton
class MoodleSessionFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val credentialsStore: CredentialsStore,
) {

    /**
     * Construye una `MoodleSession` completa a partir de un
     * `SessionContext`.
     *
     * ## Qué hace paso a paso
     *
     * 1. Crea un `CookieManager` vacío con política de aceptar todas
     *    las cookies. Este gestor acumulará las cookies que el
     *    servidor de la Junta vaya emitiendo.
     * 2. Clona el [httpClient] global y le engancha ese gestor, de
     *    forma que todas las peticiones que haga el nuevo cliente
     *    lleven las cookies automáticamente.
     * 3. Construye la configuración de Moodle para la provincia.
     * 4. Construye un `MoodleApiClient` con:
     *    - El cliente HTTP con cookies.
     *    - La configuración de Moodle.
     *    - Un `CasCredentialsTokenRefresher` ya cableado, para que el
     *      refresco silencioso funcione sin que nadie más tenga que
     *      acordarse.
     *    - Los callbacks de refresco e invalidación que ha recibido
     *      de `DefaultSessionRepository`.
     *    - El token inicial del `SessionContext`.
     * 5. Empaqueta todo en una `MoodleSession` y la devuelve.
     *
     * ## Manejo de errores
     *
     * Esta función no lanza excepciones. Solo construye objetos. Si
     * algo falla en el futuro (por ejemplo, la primera petición
     * autenticada), el error se gestiona en el `MoodleApiClient` o en
     * el llamador.
     *
     * @param context Datos puros de la sesión (provincia, usuario,
     *   token, userId).
     * @param onTokenRefreshed Callback que se invoca cuando el
     *   `MoodleApiClient` obtiene un token nuevo de forma
     *   transparente. Normalmente, `DefaultSessionRepository` lo usa
     *   para mantener el `StateFlow` de la sesión sincronizado.
     * @param onInvalidToken Callback que se invoca cuando el token ha
     *   caducado y no se ha podido refrescar. Normalmente,
     *   `DefaultSessionRepository` lo usa para forzar el cierre de
     *   sesión.
     * @return La `MoodleSession` lista para hacer peticiones.
     */
    fun create(
        context: SessionContext,
        onTokenRefreshed: suspend (String) -> Unit = {},
        onInvalidToken: suspend () -> Unit = {},
    ): MoodleSession {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val sessionHttpClient = httpClient.withCookieJar(cookieManager)
        val moodleConfig = MoodleConfig.of(context.provincia)

        val moodleApiClient = MoodleApiClient(
            client = sessionHttpClient,
            config = moodleConfig,
            tokenRefresher = CasCredentialsTokenRefresher(
                provincia = context.provincia,
                credentialsStore = credentialsStore,
                httpClient = httpClient,
            ),
            onTokenRefreshed = onTokenRefreshed,
            onInvalidToken = onInvalidToken,
            initialToken = context.wstoken,
        )

        return MoodleSession(
            user = context.user,
            cookieManager = cookieManager,
            client = sessionHttpClient,
            api = moodleApiClient,
        )
    }

    /**
     * Clona un `OkHttpClient` y le engancha un gestor de cookies.
     *
     * ## Por qué clonar y no construir uno nuevo
     *
     * Construir un `OkHttpClient` nuevo desde cero implicaría crear un
     * pool de conexiones nuevo, un pool de hilos nuevo, etc. Eso
     * desperdicia recursos. Clonando el cliente global se reutiliza
     * todo, y solo se cambia lo que hace falta: el gestor de cookies.
     *
     * @receiver Cliente original.
     * @param cookieManager Gestor de cookies que se enganchará al
     *   clon.
     * @return El cliente clonado con el gestor enganchado.
     */
    private fun OkHttpClient.withCookieJar(cookieManager: CookieManager): OkHttpClient =
        newBuilder().cookieJar(JavaNetCookieJar(cookieManager)).build()
}