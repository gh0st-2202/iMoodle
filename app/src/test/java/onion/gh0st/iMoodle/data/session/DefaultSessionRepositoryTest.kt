package onion.gh0st.iMoodle.data.session

import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionFactory
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.fakes.FakeCredentialsStore
import onion.gh0st.iMoodle.fakes.FakeLocalDataCleaner
import onion.gh0st.iMoodle.fakes.FakeNotificationScheduler
import onion.gh0st.iMoodle.fakes.FakeTokenStore
import onion.gh0st.iMoodle.fakes.FakeUserRepositoryFactory
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [DefaultSessionRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSessionRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUser = User(
        id = 1,
        username = "jdoe",
        firstname = "John",
        lastname = "Doe",
        fullname = "John Doe",
        email = "jdoe@example.com",
        profileImageUrl = null,
    )

    private val authRepo = mockk<AuthRepository>()
    private val tokenStore = FakeTokenStore()
    private val credentialsStore = FakeCredentialsStore()
    private val notificationScheduler = FakeNotificationScheduler()
    private val localData = FakeLocalDataCleaner()
    private val userRepoFactory = FakeUserRepositoryFactory(Result.success(testUser))
    private val sessionFactory = MoodleSessionFactory(OkHttpClient(), credentialsStore)

    private val repo = DefaultSessionRepository(
        authRepo = authRepo,
        tokenStore = tokenStore,
        sessionFactory = sessionFactory,
        notificationScheduler = notificationScheduler,
        credentialsStore = credentialsStore,
        userRepositoryFactory = userRepoFactory,
        localDataCleaner = localData,
    )

    // --- Login ---

    @Test
    fun `login publishes active session on success`() = runTest {
        coEvery { authRepo.login(any(), any(), any(), any()) } returns TestSessions.context()

        val result = repo.login(Provincia.CORDOBA, credentials())

        assertTrue(result.isSuccess)
        val activeSession = result.getOrThrow()
        assertEquals(Provincia.CORDOBA, activeSession.context.provincia)
        assertEquals(testUser, activeSession.user)
        assertEquals(activeSession, repo.session.value)
        coVerify(exactly = 1) { authRepo.login(Provincia.CORDOBA, any(), any(), any()) }
    }

    @Test
    fun `login failure does not publish session and propagates error`() = runTest {
        coEvery { authRepo.login(any(), any(), any(), any()) } throws
                AuthException.NoMoodleSession()

        val result = repo.login(Provincia.CORDOBA, credentials())

        assertTrue(result.isFailure)
        assertNull(repo.session.value)
    }

    // --- Auto-login ---

    @Test
    fun `tryAutoLogin reconstructs session from persisted data`() = runTest {
        coEvery { authRepo.tryAutoLogin(any()) } returns
                TestSessions.context(provincia = Provincia.SEVILLA)

        val result = repo.tryAutoLogin()

        val activeSession = result.getOrThrow()
        assertNotNull(activeSession)
        assertEquals(Provincia.SEVILLA, activeSession?.context?.provincia)
        assertEquals(testUser, activeSession?.user)
        assertEquals(activeSession, repo.session.value)
    }

    @Test
    fun `tryAutoLogin returns null without session`() = runTest {
        coEvery { authRepo.tryAutoLogin(any()) } returns null

        val result = repo.tryAutoLogin()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        assertNull(repo.session.value)
    }

    // --- Logout ---

    @Test
    fun `logout clears session delegates auth repo and cancels alarms`() = runTest {
        seedSession()
        coEvery { authRepo.logout(any()) } just Runs

        repo.logout()

        assertNull(repo.session.value)
        coVerify(exactly = 1) { authRepo.logout(Provincia.CORDOBA) }
        assertEquals(1, notificationScheduler.cancelAllCalls)
    }

    @Test
    fun `logout wipes the stored credentials`() = runTest {
        credentialsStore.save(JuntaCredentials("user", "pass"))
        seedSession()
        coEvery { authRepo.logout(any()) } just Runs

        repo.logout()

        assertEquals(1, credentialsStore.clearCalls)
        assertNull(credentialsStore.load())
    }

    @Test
    fun `logout without session still wipes persisted data`() = runTest {
        tokenStore.save(storedSession(token = "tok"))

        repo.logout()

        assertNull(repo.session.value)
        assertNull(tokenStore.mostRecent())
        coVerify(exactly = 0) { authRepo.logout(any()) }
        assertEquals(1, notificationScheduler.cancelAllCalls)
        assertEquals(1, localData.clearCalls)
    }

    @Test
    fun `logout wipes every stored token and local data`() = runTest {
        tokenStore.save(storedSession(token = "tok"))
        tokenStore.save(storedSession(token = "tok2").copy(provinciaSlug = "sevilla", lastUsedAt = 5L))
        seedSession()
        coEvery { authRepo.logout(any()) } just Runs

        repo.logout()

        assertNull(tokenStore.mostRecent())
        assertEquals(1, localData.clearCalls)
    }

    // --- Invalidación ---

    @Test
    fun `invalidate clears session emits expired and cancels alarms`() = runTest {
        seedSession()
        coEvery { authRepo.logout(any()) } just Runs

        repo.events.test {
            repo.invalidate()
            assertEquals(SessionEvent.Expired, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(repo.session.value)
        coVerify(exactly = 1) { authRepo.logout(Provincia.CORDOBA) }
        assertEquals(1, notificationScheduler.cancelAllCalls)
    }

    // --- updateUser ---

    @Test
    fun `updateUser replaces user in active session`() = runTest {
        seedSession()
        val newUser = testUser.copy(id = 99, username = "nuevo")

        repo.updateUser(newUser)

        assertEquals(newUser, repo.session.value?.user)
    }

    // --- SessionApiProvider ---

    @Test
    fun `requireApi throws when no session`() = runTest {
        val exception = runCatching { repo.requireApi() }.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `requireApi returns api of active session`() = runTest {
        seedSession()
        val api = repo.requireApi()
        assertEquals("fake_token", api.token.value)
    }

    // --- Refresco de token ---

    @Test
    fun `token refresh emits event and updates token store`() = runTest {
        tokenStore.save(storedSession(token = "old_token"))
        val refreshedSlot = slot<suspend (String) -> Unit>()
        coEvery {
            authRepo.login(any(), any(), capture(refreshedSlot), any())
        } returns TestSessions.context(token = "old_token")

        repo.login(Provincia.CORDOBA, credentials())

        repo.events.test {
            refreshedSlot.captured("new_token")
            val event = awaitItem() as SessionEvent.TokenRefreshed
            assertEquals("new_token", event.token)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("new_token", tokenStore.load("cordoba")?.wstoken)
    }

    @Test
    fun `invalidate without session wipes token but emits no event`() = runTest {
        tokenStore.save(storedSession(token = "tok"))

        repo.events.test {
            repo.invalidate()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(tokenStore.mostRecent())
    }

    @Test
    fun `repeated invalidations emit a single Expired event`() = runTest {
        seedSession()
        coEvery { authRepo.logout(any()) } just Runs

        repo.events.test {
            repo.invalidate()
            repo.invalidate()
            repo.invalidate()
            assertEquals(SessionEvent.Expired, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Login: perfil e id de usuario ---

    @Test
    fun `login persists the user id`() = runTest {
        tokenStore.save(storedSession(token = "tok"))
        coEvery { authRepo.login(any(), any(), any(), any()) } returns TestSessions.context()

        repo.login(Provincia.CORDOBA, credentials())

        assertEquals(testUser.id, tokenStore.load("cordoba")?.userId)
    }

    @Test
    fun `login fails and rolls back when the profile cannot be loaded`() = runTest {
        coEvery { authRepo.login(any(), any(), any(), any()) } returns TestSessions.context()
        coEvery { authRepo.logout(any()) } just Runs
        userRepoFactory.result = Result.failure(AuthException.Network(RuntimeException("offline")))

        val result = repo.login(Provincia.CORDOBA, credentials())

        assertTrue(result.isFailure)
        assertNull(repo.session.value)
        coVerify(exactly = 1) { authRepo.logout(Provincia.CORDOBA) }
    }

    @Test
    fun `login does not swallow coroutine cancellation`() = runTest {
        coEvery { authRepo.login(any(), any(), any(), any()) } throws CancellationException("cancelada")

        val outcome = runCatching { repo.login(Provincia.CORDOBA, credentials()) }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }

    // --- Auto-login sin red ---

    @Test
    fun `tryAutoLogin offline builds the user from the persisted id`() = runTest {
        userRepoFactory.result = Result.failure(AuthException.Network(RuntimeException("offline")))
        coEvery { authRepo.tryAutoLogin(any()) } returns
                TestSessions.context(provincia = Provincia.CORDOBA, userId = 42)

        val activeSession = repo.tryAutoLogin().getOrThrow()

        assertNotNull(activeSession)
        assertEquals(42, activeSession?.user?.id)
        assertEquals("jdoe", activeSession?.user?.username)
        assertEquals(activeSession, repo.session.value)
    }

    @Test
    fun `tryAutoLogin offline without persisted id leaves user null`() = runTest {
        userRepoFactory.result = Result.failure(AuthException.Network(RuntimeException("offline")))
        coEvery { authRepo.tryAutoLogin(any()) } returns
                TestSessions.context(provincia = Provincia.CORDOBA, userId = 0)

        val activeSession = repo.tryAutoLogin().getOrThrow()

        assertNotNull(activeSession)
        assertNull(activeSession?.user)
    }

    @Test
    fun `tryAutoLogin returns no session when the token turns out invalid`() = runTest {
        userRepoFactory.result = Result.failure(AuthException.InvalidToken())
        coEvery { authRepo.tryAutoLogin(any()) } returns
                TestSessions.context(provincia = Provincia.CORDOBA, userId = 42)

        val result = repo.tryAutoLogin()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        assertNull(repo.session.value)
    }

    @Test
    fun `tryAutoLogin refreshes the persisted user id`() = runTest {
        tokenStore.save(storedSession(token = "tok"))
        coEvery { authRepo.tryAutoLogin(any()) } returns
                TestSessions.context(provincia = Provincia.CORDOBA, userId = 0)

        repo.tryAutoLogin()

        assertEquals(testUser.id, tokenStore.load("cordoba")?.userId)
    }

    // --- Helpers ---

    private suspend fun seedSession() {
        coEvery { authRepo.login(any(), any(), any(), any()) } returns TestSessions.context()
        repo.login(Provincia.CORDOBA, credentials())
    }

    private fun credentials() = CasCredentials("user", "pass")

    private fun storedSession(token: String) = StoredSession(
        provinciaSlug = "cordoba",
        user = "jdoe",
        wstoken = token,
        lastUsedAt = 0L,
    )
}