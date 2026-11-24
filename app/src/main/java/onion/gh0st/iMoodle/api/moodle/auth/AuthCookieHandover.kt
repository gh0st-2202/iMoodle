package onion.gh0st.iMoodle.api.moodle.auth

import java.net.HttpCookie
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puente en memoria dentro de `api/` para transferir las cookies
 * recién obtenidas en [MoodleAuthRepository.login] hacia la sesión
 * viva que construye [MoodleSessionFactory.create], sin filtrar
 * infraestructura a `domain/`.
 */
@Singleton
class AuthCookieHandover @Inject constructor() {

    private val lock = Any()
    private var pendingProvinciaSlug: String? = null
    private var pendingCookies: List<Pair<URI?, HttpCookie>> = emptyList()

    fun store(provinciaSlug: String, cookies: List<Pair<URI?, HttpCookie>>) = synchronized(lock) {
        pendingProvinciaSlug = provinciaSlug
        pendingCookies = cookies
    }

    fun consume(provinciaSlug: String): List<Pair<URI?, HttpCookie>> = synchronized(lock) {
        if (pendingProvinciaSlug != provinciaSlug) return emptyList()
        val result = pendingCookies
        pendingProvinciaSlug = null
        pendingCookies = emptyList()
        result
    }

    fun clear() = synchronized(lock) {
        pendingProvinciaSlug = null
        pendingCookies = emptyList()
    }
}
