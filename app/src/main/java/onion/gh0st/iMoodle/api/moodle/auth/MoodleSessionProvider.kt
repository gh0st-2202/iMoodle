package onion.gh0st.iMoodle.api.moodle.auth

import onion.gh0st.iMoodle.api.moodle.network.MoodleSession

/**
 * Contrato para obtener la `MoodleSession` viva de la sesión activa.
 *
 * ## Qué es esto
 *
 * Es una interfaz muy pequeña: solo un método que devuelve la
 * `MoodleSession` actual o `null` si no hay sesión. Existe para que
 * los componentes de infraestructura que necesitan acceso a las
 * **cookies** o al **cliente HTTP** de la sesión (por ejemplo,
 * `MoodleWebViewActivity`) puedan obtenerlos sin acoplarse a la
 * implementación concreta.
 *
 * ## Quién la implementa
 *
 * `DefaultSessionRepository` es la única implementación en producción.
 * Mantiene la `MoodleSession` viva en memoria y la expone a través de
 * este contrato.
 *
 * ## Quién la consume
 *
 * Solo componentes de infraestructura que necesitan las cookies o el
 * `OkHttpClient`. Los ViewModels y los repositorios **no** la usan:
 * para hacer peticiones REST ya existe `SessionApiProvider`, que
 * devuelve el `MoodleApiClient` directamente.
 *
 * ## Por qué no está en `domain`
 *
 * Porque `MoodleSession` es infraestructura pura (cliente HTTP,
 * cookies, etc.), y `domain` no debe conocerla. Por eso este contrato
 * vive en `api/`, junto a la clase que devuelve.
 */
interface MoodleSessionProvider {

    /**
     * Devuelve la `MoodleSession` de la sesión activa, o `null` si no
     * hay sesión.
     *
     * ## Cuándo devuelve `null`
     *
     * - Antes del login.
     * - Después del logout.
     * - Durante una recreación de la app, en el breve instante entre
     *   que se arranca y se recupera la sesión guardada.
     *
     * ## Qué NO hace
     *
     * No lanza excepciones: si no hay sesión, simplemente devuelve
     * `null`. El llamador decide qué hacer en ese caso (normalmente,
     * redirigir al login).
     *
     * @return La `MoodleSession` activa, o `null`.
     */
    fun currentSession(): MoodleSession?
}