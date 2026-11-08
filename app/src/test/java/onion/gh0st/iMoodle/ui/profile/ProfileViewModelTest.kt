package onion.gh0st.iMoodle.ui.profile

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.usecase.session.GetCurrentUser
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.FakeUserProfileRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [ProfileViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUser = TestSessions.user()
    private val profileRepo = FakeUserProfileRepository()

    private fun createViewModel(sessionRepo: FakeSessionRepository): ProfileViewModel =
        ProfileViewModel(
            sessionRepo = sessionRepo,
            getCurrentUser = GetCurrentUser(sessionRepo),
            userProfileRepository = profileRepo,
        )

    @Test
    fun `init without session leaves user null and stops loading`() = runTest {
        val sessionRepo = FakeSessionRepository()

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertNull(user)
            assertNull(provincia)
            assertFalse(isLoading)
        }
    }

    @Test
    fun `init with session seeds user and provincia then refreshes`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(testUser, user)
            assertEquals("Córdoba", provincia)
            assertFalse(isLoading)
        }
    }

    @Test
    fun `successful refresh invokes updateUser on session repo`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )

        createViewModel(sessionRepo)
        advanceUntilIdle()

        assertEquals(testUser, sessionRepo.session.value?.user)
    }

    @Test
    fun `enrichment fills profile fields and updates ui state`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        profileRepo.result = { user ->
            user.copy(
                centerName = "IES Test",
                birthDate = "01/01/2005",
                age = "21 años",
                gender = "Hombre",
            )
        }

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        with(vm.uiState.value.user!!) {
            assertEquals("IES Test", centerName)
            assertEquals("01/01/2005", birthDate)
            assertEquals("21 años", age)
            assertEquals("Hombre", gender)
        }
    }

    @Test
    fun `enrichment failure is swallowed and user stays as-is`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        profileRepo.error = RuntimeException("offline")

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        assertEquals(testUser, vm.uiState.value.user)
    }

    @Test
    fun `identity refresh preserves already enriched profile fields`() = runTest {
        val enrichedUser = testUser.copy(
            centerName = "IES Test",
            birthDate = "01/01/2005",
            age = "21 años",
            gender = "Hombre",
        )
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(enrichedUser, Provincia.CORDOBA),
        )
        sessionRepo.refreshUserResult = Result.success(testUser.copy(firstname = "Jane"))
        profileRepo.result = { it }

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        with(vm.uiState.value.user!!) {
            assertEquals("Jane", firstname)
            assertEquals("IES Test", centerName)
            assertEquals("01/01/2005", birthDate)
            assertEquals("21 años", age)
            assertEquals("Hombre", gender)
        }
    }

    @Test
    fun `refresh failure exposes error and stops loading`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        sessionRepo.refreshUserResult = Result.failure(RuntimeException("boom"))

        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `logout calls repo and emits navigate event`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        vm.events.test {
            vm.logout()
            assertEquals(ProfileEvent.NavigateToLogin, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, sessionRepo.logoutCalls)
    }

    @Test
    fun `initial load does not refresh`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )

        createViewModel(sessionRepo)
        advanceUntilIdle()

        assertEquals(0, sessionRepo.refreshUserCalls)
    }

    @Test
    fun `refresh forces network`() = runTest {
        val sessionRepo = FakeSessionRepository(
            TestSessions.active(testUser, Provincia.CORDOBA),
        )
        val vm = createViewModel(sessionRepo)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, sessionRepo.refreshUserCalls)
    }
}