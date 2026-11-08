package onion.gh0st.iMoodle.domain.usecase.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests unitarios de [GetCurrentUser].
 *
 * Tras el refactor, este caso de uso ya no toca `api` directamente:
 * delega el refresco en [onion.gh0st.iMoodle.domain.repository.session.SessionRepository.refreshUser]
 * y devuelve `session.value?.user`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCurrentUserTest {

    private val testUser = TestSessions.user()

    @Test
    fun `no session returns null`() = runTest {
        val sessionRepo = FakeSessionRepository()
        val useCase = GetCurrentUser(sessionRepo)

        assertNull(useCase())
    }

    @Test
    fun `session with user returns user`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val useCase = GetCurrentUser(sessionRepo)

        assertEquals(testUser, useCase())
    }

    /** La carga inicial no debe tocar la red: `forceRefresh = false`. */
    @Test
    fun `initial load does not refresh`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val useCase = GetCurrentUser(sessionRepo)

        useCase()

        assertEquals(0, sessionRepo.refreshUserCalls)
    }

    /** `forceRefresh = true` debe delegar en `refreshUser()` del repo. */
    @Test
    fun `forceRefresh calls refreshUser`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val useCase = GetCurrentUser(sessionRepo)

        useCase(forceRefresh = true)

        assertEquals(1, sessionRepo.refreshUserCalls)
    }

    /** Tras el refresco, devuelve el usuario actualizado del repo. */
    @Test
    fun `refresh returns the refreshed user`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val refreshed = testUser.copy(firstname = "Jane")
        sessionRepo.refreshUserResult = Result.success(refreshed)
        val useCase = GetCurrentUser(sessionRepo)

        assertEquals(refreshed, useCase(forceRefresh = true))
    }
}