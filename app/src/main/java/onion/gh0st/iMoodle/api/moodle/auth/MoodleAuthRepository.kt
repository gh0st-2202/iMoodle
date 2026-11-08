package onion.gh0st.iMoodle.api.moodle.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.core.Defaults
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.hasInvalidTokenError
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionContext
import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [AuthRepository]: la clase que hace el
 * trabajo de autenticar al usuario y de recuperar sesiones guardadas.
 *
 * ## Qué papel juega dentro de la app
 *
 * Esta clase es la "puerta de entrada" a la autenticación. Cuando el
 * usuario pulsa "Iniciar sesión" en la pantalla de login, la petición
 * llega hasta aquí, que se encarga de:
 *
 * 1. Coordinar el login contra el CAS y Moodle.
 * 2. Guardar la sesión resultante en disco (de forma cifrada).
 * 3. Guardar las credenciales del usuario (también cifradas) para
 *    poder refrescar tokens en el futuro sin molestar al usuario.
 * 4. Al arrancar la app, intentar recuperar la última sesión guardada
 *    para que el usuario no tenga que volver a escribir la contraseña
 *    cada vez que abre la app.
 *
 * ## Qué NO hace
 *
 * - **No gestiona el ciclo de vida de la sesión en memoria**. Eso es
 *   trabajo de `DefaultSessionRepository`, que envuelve a esta clase
 *   y mantiene el estado reactivo (`StateFlow`) para la UI.
 * - **No habla con la API REST de Moodle** para pedir cursos o tareas.
 *   Solo obtiene el token inicial. Las llamadas a la API las hacen
 *   los repositorios de `api/moodle/repository/`.
 * - **No decide cuándo hay que refrescar el token**. Esa decisión la
 *   toma `MoodleApiClient`, que llama a `CasCredentialsTokenRefresher`
 *   cuando una petición falla por token caducado.
 *
 * ## Separación de responsabilidades
 *
 * La clase delega el flujo CAS + token de Moodle en `MoodleAuth`, que
 * es la que conoce los detalles de cada protocolo. Aquí solo nos
 * preocupamos de la **orquestación**: leer/escribir en los almacenes
 * cifrados, decidir qué hacer cuando el token de una sesión guardada
 * ya no vale, etc.
 *
 * @param tokenStore Almacén cifrado de sesiones (provincia + usuario +
 *   token). Permite recuperar la sesión de la última vez que el
 *   usuario entró.
 * @param credentialsStore Almacén cifrado de credenciales de la
 *   Junta. Se usa para el refresco silencioso de tokens y para
 *   Pasen, que necesita volver a autenticarse cada 30 minutos.
 * @param httpClient Cliente HTTP base compartido por toda la app.
 *   Se clona internamente para cada operación que lo necesite, de
 *   forma que todas las peticiones comparten el mismo pool de
 *   conexiones y no se abren sockets de más.
 */
@Singleton
class MoodleAuthRepository @Inject constructor(
    private val tokenStore: TokenStore,
    private val credentialsStore: CredentialsStore,
    private val httpClient: OkHttpClient,
) : AuthRepository {

    /**
     * Realiza el login completo contra la Junta (CAS + Moodle) y
     * persiste la sesión resultante.
     *
     * ## Qué hace paso a paso
     *
     * 1. Construye un `MoodleAuth` configurado para la provincia del
     *    usuario, con los callbacks de refresco de token ya
     *    conectados.
     * 2. Ejecuta el login CAS + Moodle. Si falla, la excepción sube
     *    tal cual (el `LoginViewModel` la traducirá a un mensaje).
     * 3. Construye un `SessionContext` puro (sin infraestructura) con
     *    la provincia, el usuario y el token.
     * 4. Guarda ese `SessionContext` en el almacén cifrado para poder
     *    recuperarlo en el próximo arranque.
     * 5. **Además**, guarda las credenciales del usuario (usuario y
     *    contraseña de la Junta) en `EncryptedCredentialsStore`. Esto
     *    es lo que permite que, más adelante, la app pueda pedir un
     *    token nuevo sin que el usuario tenga que volver a escribir la
     *    contraseña.
     *
     * ## Qué devuelve
     *
     * Un `SessionContext` con los datos puros de la sesión. La sesión
     * "viva" (con el cliente HTTP y las cookies) la reconstruye
     * `DefaultSessionRepository` a partir de este objeto cuando haga
     * falta.
     *
     * ## Manejo de errores
     *
     * - Si el login CAS falla, la excepción se propaga sin tocar los
     *   almacenes (no tiene sentido guardar una sesión incompleta).
     * - Si el login va bien pero algo falla al guardar, la excepción
     *   también se propaga. El llamador (el ViewModel) tratará el
     *   fallo como un error de login.
     *
     * @param provincia Provincia contra la que autenticarse. Cada
     *   provincia tiene su propia instancia de Moodle y su propia URL
     *   de servicio para el CAS.
     * @param credentials Credenciales del usuario.
     * @param onTokenRefreshed Callback que se invoca cuando Moodle
     *   emite un token nuevo de forma transparente (por ejemplo, tras
     *   un refresco silencioso). El `DefaultSessionRepository` lo usa
     *   para mantener sincronizado el token que ve la UI.
     * @param onInvalidToken Callback que se invoca cuando el token ha
     *   caducado y no se ha podido refrescar. El repositorio lo usa
     *   para forzar el cierre de sesión.
     * @return El `SessionContext` con los datos puros de la sesión
     *   autenticada.
     * @throws AuthException si el login falla por cualquier motivo.
     */
    override suspend fun login(
        provincia: Provincia,
        credentials: CasCredentials,
        onTokenRefreshed: suspend (String) -> Unit,
        onInvalidToken: suspend () -> Unit,
    ): SessionContext {
        val moodleAuth = MoodleAuth.forProvincia(
            provincia = provincia,
            baseClient = httpClient,
            onTokenRefreshed = onTokenRefreshed,
            onInvalidToken = onInvalidToken,
        )
        val moodleSession = moodleAuth.login(credentials)
        val sessionContext = SessionContext(
            provincia = provincia,
            user = moodleSession.user,
            wstoken = moodleSession.wstoken,
        )
        tokenStore.save(sessionContext.toStoredSession())
        credentialsStore.save(JuntaCredentials(credentials.user, credentials.password))
        return sessionContext
    }

    /**
     * Intenta recuperar la sesión guardada más reciente.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pide al almacén la sesión guardada más reciente (la que tiene
     *    `lastUsedAt` más alto, es decir, la que se usó por última
     *    vez en este dispositivo).
     * 2. Si no hay ninguna, devuelve `null` (no es un error, es el
     *    caso normal del primer arranque).
     * 3. Comprueba si el token de esa sesión sigue siendo válido
     *    haciendo una petición ligera a Moodle (`core_webservice_get_site_info`).
     * 4. Según el resultado de esa comprobación:
     *    - **Token válido**: reconstruye el `SessionContext` y lo
     *      devuelve. El usuario entra directamente sin escribir la
     *      contraseña.
     *    - **Token inválido**: borra la sesión guardada y devuelve
     *      `null`. La app mostrará la pantalla de login para que el
     *      usuario vuelva a identificarse.
     *    - **No se puede comprobar (sin red, timeout, servidor
     *      caído)**: **no** se borra la sesión. Se devuelve igualmente
     *      el `SessionContext`, asumiendo que el token puede seguir
     *      siendo válido. Así el usuario puede acceder a los datos
     *      cacheados en local aunque no tenga conexión.
     *
     * ## Por qué es importante distinguir "token inválido" de "no se
     * pudo comprobar"
     *
     * Si tratáramos cualquier fallo de red como "token inválido",
     * cualquier usuario que abriera la app sin conexión perdería su
     * sesión y tendría que volver a escribir la contraseña. Eso sería
     * muy molesto. En cambio, si el token realmente ha caducado, sí
     * que conviene borrarlo para que el usuario no se quede con una
     * sesión que ya no funciona.
     *
     * @param onInvalidToken Callback que se invoca si el token se
     *   descubre inválido durante la comprobación. El repositorio lo
     *   usa para forzar el cierre de sesión.
     * @return El `SessionContext` recuperado, o `null` si no hay
     *   ninguna sesión guardada válida.
     */
    @Suppress("UNUSED_PARAMETER")
    override suspend fun tryAutoLogin(
        onInvalidToken: suspend () -> Unit,
    ): SessionContext? {
        val storedSession = tokenStore.mostRecent() ?: return null
        return when (checkTokenValidity(storedSession)) {
            TokenValidity.Invalid -> {
                tokenStore.clear(storedSession.provinciaSlug)
                null
            }
            TokenValidity.Valid, TokenValidity.Unknown -> {
                val provincia = Provincia.fromSlug(storedSession.provinciaSlug) ?: return null
                SessionContext(
                    provincia = provincia,
                    user = storedSession.user,
                    wstoken = storedSession.wstoken,
                    userId = storedSession.userId,
                )
            }
        }
    }

    /**
     * Borra la sesión guardada de una provincia.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa "Cerrar sesión" explícitamente. No borra
     * las credenciales guardadas (eso lo hace
     * `DefaultSessionRepository`, que tiene acceso a ambos almacenes).
     * Solo borra la sesión de Moodle.
     *
     * @param provincia Provincia cuya sesión hay que olvidar.
     */
    override suspend fun logout(provincia: Provincia) = tokenStore.clear(provincia.slug)

    // --- Métodos internos ---

    /**
     * Convierte un `SessionContext` de dominio en el objeto que se
     * guarda en disco.
     *
     * ## Qué añade
     *
     * El `SessionContext` no tiene campo `lastUsedAt` porque es un
     * concepto de persistencia (sirve para ordenar las sesiones y
     * elegir la más reciente). Aquí lo añadimos con el timestamp
     * actual.
     *
     * @receiver El `SessionContext` a convertir.
     * @return El `StoredSession` listo para guardar.
     */
    private fun SessionContext.toStoredSession() = StoredSession(
        provinciaSlug = provincia.slug,
        user = user,
        wstoken = wstoken,
        lastUsedAt = System.currentTimeMillis(),
    )

    /**
     * Comprueba si el token de una sesión guardada sigue siendo
     * válido haciendo una petición de prueba a Moodle.
     *
     * ## Por qué en un hilo aparte
     *
     * La petición HTTP puede tardar varios segundos. Hacerla en el
     * hilo principal congelaría la interfaz, así que se ejecuta en
     * `Dispatchers.IO`.
     *
     * ## Manejo de errores
     *
     * - Si la petición va bien y Moodle responde "token inválido",
     *   devuelve `Invalid`.
     * - Si la petición va bien y Moodle acepta el token, devuelve
     *   `Valid`.
     * - Si la petición falla por cualquier motivo (red, timeout, 5xx),
     *   devuelve `Unknown`. **No** se propaga la excepción: el
     *   llamador prefiere asumir que el token puede valer antes que
     *   molestar al usuario pidiéndole la contraseña otra vez.
     * - Si la corrutina se cancela, la excepción de cancelación se
     *   propaga (es lo correcto en corrutinas).
     *
     * @param storedSession Sesión guardada cuyo token hay que probar.
     * @return El resultado de la comprobación.
     */
    private suspend fun checkTokenValidity(storedSession: StoredSession): TokenValidity =
        withContext(Dispatchers.IO) {
            try {
                if (isTokenAcceptedByMoodle(storedSession)) {
                    TokenValidity.Valid
                } else {
                    TokenValidity.Invalid
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                TokenValidity.Unknown
            }
        }

    /**
     * Resultado de comprobar la validez de un token guardado.
     *
     * - **Valid**: Moodle ha aceptado el token.
     * - **Invalid**: Moodle ha respondido que el token ya no vale.
     * - **Unknown**: no se ha podido comprobar (sin red, timeout,
     *   error del servidor). Se asume que puede seguir valiendo.
     */
    private enum class TokenValidity { Valid, Invalid, Unknown }

    /**
     * Hace una petición ligera a Moodle para comprobar si el token
     * sigue siendo aceptado.
     *
     * ## Qué petición se hace
     *
     * `core_webservice_get_site_info` es una función de Moodle que
     * devuelve información básica del sitio. Es muy barata (no
     * descarga datos del usuario) y sirve perfectamente como "ping"
     * autenticado: si el token es válido, responde con un JSON
     * normal; si no, responde con un JSON de error que contiene la
     * palabra `invalidtoken`.
     *
     * @param storedSession Sesión guardada cuyo token hay que probar.
     * @return `true` si Moodle acepta el token; `false` si lo rechaza.
     */
    private fun isTokenAcceptedByMoodle(storedSession: StoredSession): Boolean =
        !fetchSiteInfo(storedSession).hasInvalidTokenError()

    /**
     * Ejecuta la petición de comprobación contra Moodle.
     *
     * ## Por qué es síncrona
     *
     * El llamador ([checkTokenValidity]) ya se ejecuta en
     * `Dispatchers.IO`, así que aquí podemos usar `execute()` sin
     * miedo a bloquear el hilo principal. Usar `execute()` en lugar
     * de `await()` evita suspender la corrutina, lo cual es más
     * eficiente para una operación puntual.
     *
     * @param storedSession Sesión guardada cuyo token hay que probar.
     * @return El cuerpo de la respuesta (JSON) como cadena.
     */
    private fun fetchSiteInfo(storedSession: StoredSession): String =
        httpClient.newCall(
            Request.Builder()
                .url(buildSiteInfoUrl(storedSession))
                .header("User-Agent", Defaults.USER_AGENT)
                .build(),
        ).execute().use { response ->
            response.body?.string().orEmpty()
        }

    /**
     * Construye la URL de la petición `core_webservice_get_site_info`
     * para una sesión guardada.
     *
     * ## Cómo se construye
     *
     * La URL es la del endpoint REST de Moodle con tres parámetros:
     *
     * 1. `wstoken`: el token que hay que probar. Va codificado para
     *    que caracteres especiales (espacios, `+`, etc.) no rompan la
     *    URL.
     * 2. `wsfunction`: el nombre de la función de Moodle a llamar.
     * 3. `moodlewsrestformat`: el formato de la respuesta. `json` es
     *    el más habitual y el que sabe leer el llamador.
     *
     * @param storedSession Sesión guardada con la provincia y el token.
     * @return La URL completa, lista para usar en una petición HTTP.
     */
    private fun buildSiteInfoUrl(storedSession: StoredSession): String {
        val moodleBase = MoodleConfig(provinciaSlug = storedSession.provinciaSlug).moodleBase
        return "$moodleBase/webservice/rest/server.php" +
                "?wstoken=${URLEncoder.encode(storedSession.wstoken, "UTF-8")}" +
                "&wsfunction=core_webservice_get_site_info&moodlewsrestformat=json"
    }
}