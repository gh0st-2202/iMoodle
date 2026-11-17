package onion.gh0st.iMoodle.domain.repository.web

import onion.gh0st.iMoodle.domain.model.web.WebSession

/**
 * Contrato de acceso a la sesión web (cookies + host de Moodle).
 *
 * ## Por qué existe
 *
 * El `WebView` embebido necesita dos cosas de la sesión viva: el host
 * de Moodle (para bloquear navegaciones externas) y las cookies (para
 * cargar las páginas autenticadas). Ese acceso lo resolvía antes la
 * propia Activity inyectando `MoodleSessionProvider`, lo que obligaba
 * a `ui/` a depender de `api/`.
 *
 * Con este contrato, la UI pide un [WebSession] de dominio y la
 * implementación de `data/` traduce desde la sesión de OkHttp.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `DefaultWebSessionRepository` (`data/web/`).
 * - **Tests**: un doble que devuelva cookies fijas.
 *
 * ## Quién la consume
 *
 * `GetWebSession` y, a través de él, `MoodleWebViewModel`. Ningún
 * Fragment ni Activity debe depender de esta interfaz directamente.
 */
interface WebSessionRepository {

    /**
     * Devuelve la sesión web actual, o `null` si no hay sesión activa.
     *
     * ## Cuándo devuelve `null`
     *
     * - Antes del login.
     * - Después del logout.
     * - En el breve instante del arranque en que aún no se ha
     *   recuperado la sesión guardada.
     *
     * ## Qué NO hace
     *
     * No lanza excepciones: la ausencia de sesión es un estado
     * esperado y el llamador decide qué hacer (normalmente, cerrar la
     * pantalla del `WebView`).
     *
     * @return La sesión web, o `null`.
     */
    fun currentSession(): WebSession?
}
