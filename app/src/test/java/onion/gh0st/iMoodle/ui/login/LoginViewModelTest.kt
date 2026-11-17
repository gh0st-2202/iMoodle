package onion.gh0st.iMoodle.ui.login

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.usecase.session.Login
import onion.gh0st.iMoodle.domain.usecase.session.TryAutoLogin
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [LoginViewModel].
 *
 * ## Qué cubren
 *
 * - Login correcto: toast de bienvenida (con el nombre como argumento)
 *   y navegación a `MainActivity`.
 * - Traducción de errores de autenticación a recursos de string.
 * - Auto-login correcto, sin sesión y fallido.
 * - Estado del botón y selección de provincia.
 *
 * ## Por qué ya no se construye con `Application`
 *
 * Porque el ViewModel dejó de ser `AndroidViewModel`: ahora emite el
 * **ID** del recurso ([LoginEvent.ShowToast.messageRes]) y es la
 * Activity quien resuelve el texto. Eso permite probar la lógica en
 * JVM sin `Context` ni recursos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepo = FakeSessionRepository()

    /**
     * Crea el ViewModel con los casos de uso sobre el repositorio falso.
     *
     * @return El ViewModel listo para probar.
     */
    private fun createViewModel() = LoginViewModel(
        performLogin = Login(sessionRepo),
        autoLogin = TryAutoLogin(sessionRepo),
    )

    @Test
    fun `successful login emits welcome toast and navigate event`() = runTest {
        val activeSession = TestSessions.active(TestSessions.user(firstname = "María"))
        sessionRepo.loginResult = Result.success(activeSession)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(R.string.toast_welcome_back, toast.messageRes)
            assertTrue("Nombre inesperado: ${toast.formatArg}", toast.formatArg?.contains("María") == true)
            assertEquals(LoginEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bad credentials maps to credentials toast`() = runTest {
        sessionRepo.loginResult = Result.failure(AuthException.NoMoodleSession())
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(R.string.error_bad_credentials, toast.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network error maps to network toast`() = runTest {
        sessionRepo.loginResult = Result.failure(
            AuthException.Network(RuntimeException("timeout")),
        )
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.login(Provincia.CORDOBA, "user", "pass")
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(R.string.error_network, toast.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto login success emits welcome toast and navigate event`() = runTest {
        sessionRepo.autoLoginResult = Result.success(
            TestSessions.active(TestSessions.user(firstname = "Lucía")),
        )
        val viewModel = createViewModel()

        viewModel.events.test {
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(R.string.toast_welcome_back, toast.messageRes)
            assertTrue("Nombre inesperado: ${toast.formatArg}", toast.formatArg?.contains("Lucía") == true)
            assertEquals(LoginEvent.NavigateToMain, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto login without session stops loading silently`() = runTest {
        sessionRepo.autoLoginResult = Result.success(null)
        val viewModel = createViewModel()

        with(viewModel.uiState.value) {
            assertFalse(isLoading)
            assertFalse(isButtonEnabled)
        }
    }

    @Test
    fun `auto login failure emits recovery toast`() = runTest {
        sessionRepo.autoLoginResult = Result.failure(RuntimeException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            val toast = awaitItem() as LoginEvent.ShowToast
            assertEquals(R.string.toast_session_recovery_failed, toast.messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onInputChanged toggles button enabled state`() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.isButtonEnabled)

        viewModel.onInputChanged("user", "pass")
        assertTrue(viewModel.uiState.value.isButtonEnabled)

        viewModel.onInputChanged("user", "")
        assertFalse(viewModel.uiState.value.isButtonEnabled)
    }

    @Test
    fun `selected province is stored in the ui state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onProvinceSelected(3)

        assertEquals(3, viewModel.uiState.value.selectedCentroIndex)
    }

    @Test
    fun `selected province survives a failed login and input changes`() = runTest {
        sessionRepo.loginResult = Result.failure(AuthException.NoMoodleSession())
        val viewModel = createViewModel()
        viewModel.onProvinceSelected(3)

        viewModel.onInputChanged("user", "pass")
        viewModel.login(Provincia.CORDOBA, "user", "pass")

        assertEquals(3, viewModel.uiState.value.selectedCentroIndex)
    }

    @Test
    fun `negative province index is clamped to zero`() = runTest {
        val viewModel = createViewModel()

        viewModel.onProvinceSelected(-1)

        assertEquals(0, viewModel.uiState.value.selectedCentroIndex)
    }
}
