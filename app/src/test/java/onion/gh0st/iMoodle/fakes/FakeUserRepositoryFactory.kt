package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.CompletableDeferred
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

    /**
     * Si no es `null`, los repositorios creados esperan a que se complete
     * antes de devolver el usuario. Permite probar qué ocurre cuando algo
     * (por ejemplo, un cierre de sesión) sucede mientras la petición está
     * en vuelo.
     */
    var gate: CompletableDeferred<Unit>? = null

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
        val repo = FakeUserRepository(result, gate)
        lastRepo = repo
        return repo
    }
}