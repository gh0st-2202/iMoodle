package onion.gh0st.iMoodle.api.moodle.auth

import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient

/**
 * Contrato para obtener el cliente REST de Moodle de la sesión activa.
 *
 * ## Qué es esto
 *
 * Es una interfaz muy pequeña: solo un método que devuelve el
 * `MoodleApiClient` de la sesión actual. Existe para que los
 * repositorios de `api/moodle/repository/` (Course, Calendar,
 * Assignment, Forum) puedan hacer peticiones REST sin acoplarse a
 * `DefaultSessionRepository`.
 *
 * ## Quién la implementa
 *
 * `DefaultSessionRepository` es la única implementación en producción.
 * Mantiene el `MoodleApiClient` vivo en memoria y lo expone a través
 * de este contrato.
 *
 * ## Quién la consume
 *
 * **Solo** los repositorios de `api/moodle/repository/`. Los
 * ViewModels y los casos de uso no la usan: ellos trabajan con los
 * contratos de dominio (`CourseRepository`, `CalendarRepository`,
 * etc.) y no saben que existe un cliente REST.
 *
 * ## Diferencia con `MoodleSessionProvider`
 *
 * - `MoodleSessionProvider` devuelve la `MoodleSession` completa
 *   (con cookies y cliente HTTP). Solo la usan componentes que
 *   necesitan las cookies, como el WebView.
 * - `SessionApiProvider` devuelve solo el `MoodleApiClient`, que ya
 *   lleva las cookies enganchadas internamente. Es lo que necesitan
 *   los repositorios para hacer peticiones REST.
 *
 * ## Por qué no está en `domain`
 *
 * Porque `MoodleApiClient` es infraestructura pura (cliente REST,
 * URLs, tokens, etc.), y `domain` no debe conocerla. Por eso este
 * contrato vive en `api/`.
 */
interface SessionApiProvider {

    /**
     * Devuelve el cliente REST de Moodle de la sesión activa.
     *
     * ## Cuándo lanza excepción
     *
     * Si no hay sesión activa, lanza `IllegalStateException`. Esto es
     * intencionado: un repositorio no debería hacer peticiones sin
     * sesión. Si ocurre, es un error de programación, no una
     * situación normal. La excepción hace que el bug salte rápido en
     * lugar de producir un fallo silencioso y confuso más adelante.
     *
     * ## Por qué el método se llama `requireApi`
     *
     * El prefijo `require` es una convención de Kotlin para indicar
     * que el método **exige** una precondición y lanza excepción si
     * no se cumple. Aquí la precondición es "hay sesión activa".
     *
     * @return El `MoodleApiClient` de la sesión activa, listo para
     *   hacer peticiones REST.
     * @throws IllegalStateException si no hay sesión activa.
     */
    fun requireApi(): MoodleApiClient
}