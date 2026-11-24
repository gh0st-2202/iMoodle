package onion.gh0st.iMoodle.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.auth.MoodleAuth
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.session.CasCredentials
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.web.WebCookieRefresher
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renueva únicamente las cookies web de la sesión activa.
 *
 * ## Por qué no llama a `SessionRepository.login()`
 *
 * Relanzar un login completo recrearía el `OkHttpClient`, volvería a
 * pedir `core_webservice_get_site_info` y, sobre todo, reemplazaría la
 * sesión viva mientras el resto de la app está haciendo peticiones
 * REST. Aquí solo se autentica de nuevo contra CAS y se copian las
 * cookies frescas al `CookieManager` que ya usa la sesión.
 *
 * ## TTL y concurrencia
 *
 * Un `Mutex` serializa los refrescos. Mientras la sesión siga teniendo
 * una cookie `MoodleSession` no caducada, se reutiliza durante 15
 * minutos; pasado ese tiempo se renueva. `force = true` salta el TTL
 * (lo usa el WebView cuando Moodle lo redirige al login).
 *
 * @param sessionRepository Fuente de la provincia de la sesión activa.
 * @param moodleSessionProvider Sesión viva cuyo `CookieManager` se
 *   actualiza.
 * @param credentialsStore Credenciales cifradas para repetir el login.
 * @param httpClient Cliente base compartido; `MoodleAuth` lo clona.
 */
@Singleton
class DefaultWebCookieRefresher @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val moodleSessionProvider: MoodleSessionProvider,
    private val credentialsStore: CredentialsStore,
    private val httpClient: OkHttpClient,
) : WebCookieRefresher {

    /** Evita refrescos CAS simultáneos. */
    private val refreshMutex = Mutex()

    /** Sesión a la que corresponde [lastRefreshAtMs]. */
    private var lastRefreshedSession: MoodleSession? = null

    /** Momento del último login CAS realizado por esta clase. */
    private var lastRefreshAtMs: Long = 0L

    override suspend fun refresh(force: Boolean): Boolean = refreshMutex.withLock {
        val activeSession = sessionRepository.session.value ?: return@withLock false
        val moodleSession = moodleSessionProvider.currentSession() ?: return@withLock false

        if (!force && hasFreshWebCookies(moodleSession)) {
            return@withLock true
        }

        val credentials = credentialsStore.load() ?: return@withLock false

        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val freshSession = MoodleAuth.forProvincia(
                    provincia = activeSession.context.provincia,
                    baseClient = httpClient,
                    onTokenRefreshed = {},
                    onInvalidToken = {},
                ).login(CasCredentials(credentials.username, credentials.password))

                val targetStore = moodleSession.cookieManager.cookieStore
                val freshCookies = freshSession.cookieManager.cookieStore.cookies
                val defaultHost = moodleSession.api.config.moodleHost

                targetStore.removeAll()
                freshCookies.forEach { cookie ->
                    val host = cookie.domain
                        ?.trimStart('.')
                        ?.takeIf { it.isNotBlank() }
                        ?: defaultHost
                    targetStore.add(URI.create("https://$host"), cookie)
                }

                lastRefreshedSession = moodleSession
                lastRefreshAtMs = System.currentTimeMillis()
                true
            }.onFailure { error ->
                Logging.logError("DefaultWebCookieRefresher", "refresh", error)
            }.getOrDefault(false)
        }
    }

    /**
     * `true` si la sesión tiene una cookie `MoodleSession` vigente y el
     * último refresco no supera el TTL.
     */
    private fun hasFreshWebCookies(moodleSession: MoodleSession): Boolean {
        val hasMoodleSessionCookie = moodleSession.cookieManager.cookieStore.cookies.any { cookie ->
            cookie.name.startsWith(MOODLE_SESSION_COOKIE_PREFIX, ignoreCase = true) &&
                !cookie.hasExpired()
        }
        if (!hasMoodleSessionCookie) return false

        val now = System.currentTimeMillis()
        if (lastRefreshedSession !== moodleSession) {
            // Sesión nueva (login o auto-login): sus cookies son recién
            // obtenidas, así que arrancamos el contador aquí.
            lastRefreshedSession = moodleSession
            lastRefreshAtMs = now
            return true
        }
        return (now - lastRefreshAtMs) < COOKIE_TTL_MS
    }

    private companion object {
        const val MOODLE_SESSION_COOKIE_PREFIX = "MoodleSession"
        const val COOKIE_TTL_MS = 15 * 60 * 1000L
    }
}
