package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository

/**
 * Implementación en memoria de [UserProfileRepository] para tests.
 *
 * @property result Transformación a aplicar al `User`. Por defecto,
 *   identidad.
 * @property error Si no es `null`, se lanza al llamar a [enrich].
 */
class FakeUserProfileRepository : UserProfileRepository {

    /** Transformación a aplicar al `User` en [enrich]. */
    var result: (User) -> User = { it }

    /** Si no es `null`, se lanza al llamar a [enrich]. */
    var error: Throwable? = null

    /** Número de veces que se ha llamado a [enrich]. */
    var enrichCalls: Int = 0
        private set

    /**
     * Aplica [result] o lanza [error].
     *
     * @param user Usuario a enriquecer.
     * @return El usuario transformado.
     * @throws Throwable si [error] no es `null`.
     */
    override suspend fun enrich(user: User): User {
        enrichCalls++
        error?.let { throw it }
        return result(user)
    }
}