package onion.gh0st.iMoodle.ui.login

import android.app.Application
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [LoginViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val app = mockStringApp()
    private val sessionRepo = FakeSessionRepository()

    @Test
    fun `successful login emits welcome toast and navigate event`() = runTest {
        val activeSession = TestSessions.active(TestSessions.user(firstname = "María"))
        sessionRepo.loginResult = Result.success(activeSession)
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertTrue("Toast inesperado: ${toast.message}", toast.message.contains("María"))
            assertEquals(LoginEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bad credentials maps to credentials toast`() = runTest {
        sessionRepo.loginResult = Result.failure(AuthException.NoMoodleSession())
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(BAD_CREDENTIALS, toast.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network error maps to network toast`() = runTest {
        sessionRepo.loginResult = Result.failure(
            AuthException.Network(RuntimeException("timeout")),
        )
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertTrue("Toast inesperado: ${toast.message}", toast.message.contains("network"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto login success emits welcome toast and navigate event`() = runTest {
        sessionRepo.autoLoginResult = Result.success(
            TestSessions.active(TestSessions.user(firstname = "Lucía")),
        )
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.events.test {
            val toast = awaitItem() as LoginEvent.ShowToast
            assertTrue("Toast inesperado: ${toast.message}", toast.message.contains("Lucía"))
            assertEquals(LoginEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto login without session stops loading silently`() = runTest {
        sessionRepo.autoLoginResult = Result.success(null)
        val viewModel = LoginViewModel(app, sessionRepo)

        with(viewModel.uiState.value) {
            assertFalse(isLoading)
            assertFalse(isButtonEnabled)
        }
    }

    @Test
    fun `auto login failure emits recovery toast`() = runTest {
        sessionRepo.autoLoginResult = Result.failure(RuntimeException("boom"))
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.events.test {
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(RECOVERY_FAILED, toast.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onInputChanged toggles button enabled state`() = runTest {
        val viewModel = LoginViewModel(app, sessionRepo)
        assertFalse(viewModel.uiState.value.isButtonEnabled)

        viewModel.onInputChanged("user", "pass")
        assertTrue(viewModel.uiState.value.isButtonEnabled)

        viewModel.onInputChanged("user", "")
        assertFalse(viewModel.uiState.value.isButtonEnabled)
    }

    @Test
    fun `selected province is stored in the ui state`() = runTest {
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.onProvinceSelected(3)

        assertEquals(3, viewModel.uiState.value.selectedCentroIndex)
    }

    @Test
    fun `selected province survives a failed login and input changes`() = runTest {
        sessionRepo.loginResult = Result.failure(AuthException.NoMoodleSession())
        val viewModel = LoginViewModel(app, sessionRepo)
        viewModel.onProvinceSelected(3)

        viewModel.onInputChanged("user", "pass")
        viewModel.login(Provincia.CORDOBA, "user", "pass")

        assertEquals(3, viewModel.uiState.value.selectedCentroIndex)
    }

    @Test
    fun `negative province index is clamped to zero`() = runTest {
        val viewModel = LoginViewModel(app, sessionRepo)

        viewModel.onProvinceSelected(-1)

        assertEquals(0, viewModel.uiState.value.selectedCentroIndex)
    }

    private fun mockStringApp(): Application = mockk(relaxed = true) {
        every { getString(R.string.action_login) } returns "login"
        every { getString(R.string.loading_connecting) } returns "loading"
        every { getString(R.string.toast_session_recovery_failed) } returns RECOVERY_FAILED
        every { getString(R.string.error_bad_credentials) } returns BAD_CREDENTIALS
        every { getString(R.string.error_network) } returns "network error"
        every { getString(R.string.error_unexpected) } returns "unexpected error"
        every { getString(R.string.toast_welcome_back, *anyVararg()) } answers {
            "welcome ${extractFirstVararg(args)}"
        }
    }

    private fun extractFirstVararg(args: List<Any?>): String {
        val varargs = args.getOrNull(1) as? Array<*> ?: return ""
        return varargs.firstOrNull()?.toString().orEmpty()
    }

    private companion object {
        const val BAD_CREDENTIALS = "bad credentials"
        const val RECOVERY_FAILED = "recovery failed"
    }
}