package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.repository.UserRepositoryFactory
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.UserRepository

/**
 * Fábrica de [FakeUserRepository] para tests.
 *
 * @property result Resultado que devolverá el fake creado.
 */
class FakeUserRepositoryFactory(
    var result: Result<User>,
) : UserRepositoryFactory {

    /** Último repositorio creado, para verificar el `forceRefresh` recibido. */
    var lastRepo: FakeUserRepository? = null
        private set

    /**
     * Crea un [FakeUserRepository] con [result] y lo guarda en [lastRepo].
     *
     * @param api Cliente REST (ignorado por el fake).
     * @return El repositorio fake.
     */
    override fun create(api: MoodleApiClient): UserRepository {
        val repo = FakeUserRepository(result)
        lastRepo = repo
        return repo
    }
}