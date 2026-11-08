package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient

/**
 * Implementación de test de [SessionApiProvider].
 *
 * A diferencia de `FakeSessionRepository`, este fake solo implementa
 * la parte que necesitan los repositorios de `api/` (Course, Calendar,
 * Assignment, Forum): el acceso al [MoodleApiClient] de la sesión.
 *
 * @param api Cliente REST a devolver, o `null` para simular "sin sesión".
 */
class FakeSessionApiProvider(
    private val api: MoodleApiClient? = null,
) : SessionApiProvider {

    /**
     * Devuelve el cliente configurado.
     *
     * @return El `MoodleApiClient`.
     * @throws IllegalStateException si [api] es `null`.
     */
    override fun requireApi(): MoodleApiClient =
        api ?: error("FakeSessionApiProvider: sin sesión activa")
}