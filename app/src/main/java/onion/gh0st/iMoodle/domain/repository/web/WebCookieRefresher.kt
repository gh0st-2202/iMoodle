package onion.gh0st.iMoodle.domain.repository.web

/**
 * Refresca la sesión CAS para obtener cookies válidas para el WebView.
 */
interface WebCookieRefresher {
    /**
     * Renueva la sesión CAS si no hay cookies web vigentes o si
     * [force] es `true`.
     *
     * @param force Si es `true`, ignora el TTL local y fuerza un nuevo login CAS.
     * @return `true` si hay cookies válidas tras la operación.
     */
    suspend fun refresh(force: Boolean = false): Boolean
}
