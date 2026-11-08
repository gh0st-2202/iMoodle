package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.UserRepository

/**
 * Implementación en memoria de [UserRepository] para tests.
 *
 * Registra el último `forceRefresh` recibido para verificar que el
 * ViewModel pasa el flag correcto.
 *
 * @property result Resultado a devolver desde [getUser].
 */
class FakeUserRepository(
    private val result: Result<User>,
) : UserRepository {

    /** Último valor de `forceRefresh` recibido, o `null` si aún no se llamó. */
    var lastForceRefresh: Boolean? = null
        private set

    /**
     * Devuelve el usuario configurado o lanza la excepción de [result].
     *
     * @param forceRefresh Flag que se registra en [lastForceRefresh].
     * @return El usuario configurado.
     * @throws Throwable si [result] es un `Result.failure`.
     */
    override suspend fun getUser(forceRefresh: Boolean): User {
        lastForceRefresh = forceRefresh
        return result.getOrThrow()
    }
}