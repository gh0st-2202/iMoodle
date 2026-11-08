package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.domain.repository.session.TokenStore

/**
 * Implementación en memoria de [TokenStore] para tests.
 *
 * Almacena las sesiones en un `MutableMap` indexado por slug de
 * provincia. No usa cifrado ni persistencia real.
 */
class FakeTokenStore : TokenStore {

    /** Estado interno: una sesión por provincia. */
    private val store = mutableMapOf<String, StoredSession>()

    /**
     * Guarda o sobreescribe la sesión de una provincia.
     *
     * @param session Sesión a persistir.
     */
    override suspend fun save(session: StoredSession) {
        store[session.provinciaSlug] = session
    }

    /**
     * Recupera la sesión de una provincia.
     *
     * @param provinciaSlug Slug de la provincia.
     * @return La sesión guardada, o `null` si no existe.
     */
    override suspend fun load(provinciaSlug: String): StoredSession? =
        store[provinciaSlug]

    /**
     * Devuelve la sesión con `lastUsedAt` más alto.
     *
     * @return La sesión más reciente, o `null` si no hay ninguna.
     */
    override suspend fun mostRecent(): StoredSession? =
        store.values.maxByOrNull { it.lastUsedAt }

    /**
     * Borra la sesión de una provincia.
     *
     * @param provinciaSlug Slug de la provincia a eliminar.
     */
    override suspend fun clear(provinciaSlug: String) {
        store.remove(provinciaSlug)
    }

    /**
     * Borra todas las sesiones.
     */
    override suspend fun clearAll() {
        store.clear()
    }
}