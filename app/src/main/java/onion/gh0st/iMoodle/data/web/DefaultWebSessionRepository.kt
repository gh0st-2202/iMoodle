package onion.gh0st.iMoodle.data.web

import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.domain.model.web.WebCookie
import onion.gh0st.iMoodle.domain.model.web.WebSession
import onion.gh0st.iMoodle.domain.repository.web.WebSessionRepository
import java.net.HttpCookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [WebSessionRepository] sobre la sesión viva de
 * Moodle.
 *
 * ## Qué hace
 *
 * Toma la `MoodleSession` que mantiene `DefaultSessionRepository` y la
 * traduce al modelo de dominio [WebSession]:
 *
 * - El host sale de la configuración de la API
 *   (`api.config.moodleHost`).
 * - Las cookies salen del `CookieStore` de OkHttp y se convierten de
 *   `HttpCookie` a [WebCookie].
 *
 * ## Por qué la traducción vive aquí y no en la UI
 *
 * Porque `HttpCookie` y `MoodleSession` son infraestructura. La UI
 * solo debe ver tipos de dominio; si mañana cambia el cliente HTTP o
 * el almacén de cookies, esta clase es la única que se toca.
 *
 * ## Qué NO hace
 *
 * - **No modifica la sesión**: es de solo lectura.
 * - **No inyecta cookies en el `WebView`**: eso es plataforma y vive
 *   en la Activity.
 *
 * @param moodleSessionProvider Fuente de la sesión viva de Moodle.
 */
@Singleton
class DefaultWebSessionRepository @Inject constructor(
    private val moodleSessionProvider: MoodleSessionProvider,
) : WebSessionRepository {

    /**
     * Lee y traduce la sesión actual.
     *
     * @return La sesión web con host y cookies, o `null` si no hay
     *   sesión activa.
     */
    override fun currentSession(): WebSession? {
        val moodleSession = moodleSessionProvider.currentSession() ?: return null
        return WebSession(
            moodleHost = moodleSession.api.config.moodleHost,
            cookies = moodleSession.cookieManager.cookieStore.cookies
                .map { cookie -> cookie.toWebCookie() },
        )
    }

    /**
     * Convierte una cookie de OkHttp al modelo de dominio.
     *
     * @receiver Cookie de la sesión de OkHttp.
     * @return La misma cookie como dato de dominio.
     */
    private fun HttpCookie.toWebCookie(): WebCookie = WebCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = isHttpOnly,
    )
}
