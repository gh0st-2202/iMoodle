package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore

/**
 * Implementación en memoria de [CredentialsStore] para tests.
 *
 * No cifra nada. Registra si se ha llamado a [clear] para verificar
 * el flujo de logout. Se usa en `AuthRepositoryImplTest` y
 * `DefaultSessionRepositoryTest` para no depender de Android Keystore.
 */
class FakeCredentialsStore : CredentialsStore {

    /** Credenciales almacenadas, o `null` si no hay. */
    private var stored: JuntaCredentials? = null

    /** Número de veces que se ha llamado a [clear]. */
    var clearCalls: Int = 0
        private set

    /**
     * Guarda las credenciales en memoria.
     *
     * @param credentials Credenciales a persistir.
     */
    override suspend fun save(credentials: JuntaCredentials) {
        stored = credentials
    }

    /**
     * Devuelve las credenciales guardadas, o `null` si no hay.
     *
     * @return Las credenciales, o `null`.
     */
    override suspend fun load(): JuntaCredentials? = stored

    /**
     * Borra las credenciales y cuenta la llamada.
     */
    override suspend fun clear() {
        clearCalls++
        stored = null
    }
}