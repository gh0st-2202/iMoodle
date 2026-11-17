package onion.gh0st.iMoodle.api.cas

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.session.CasCredentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [CasAuthenticator] frente a errores del servidor.
 *
 * ## Qué protegen
 *
 * Un error interno del CAS (HTTP 5xx) **no** es una contraseña
 * incorrecta. Antes se trataba como `LoginFailed`, lo que hacía que la
 * app borrase las credenciales guardadas durante una simple caída de la
 * Junta (bug #23). Estos tests fijan que un 5xx produce
 * [AuthException.ServerError], un `401`/`403` sigue siendo
 * [AuthException.LoginFailed] y un `429` (rate limit) es un fallo
 * transitorio [AuthException.Network] que **no** debe borrar las
 * credenciales.
 */
class CasAuthenticatorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * Crea un autenticador que apunta al servidor simulado.
     *
     * @return El autenticador de la prueba.
     */
    private fun authenticator() = CasAuthenticator(
        CasConfig(
            serviceUrl = "https://moodle.example/login/index.php",
            casUrl = server.url("/cas/login").toString(),
        ),
    )

    /**
     * Encola la página de login del CAS con un token `execution` válido.
     */
    private fun enqueueLoginPage() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<form><input type="hidden" name="execution" value="abc123"/></form>"""),
        )
    }

    private val credentials = CasCredentials(user = "alumno", password = "secreto")

    @Test
    fun `server error on the login page is reported as ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { authenticator().authenticate(credentials) }.exceptionOrNull()

        assertTrue(error is AuthException.ServerError)
        assertEquals(503, (error as AuthException.ServerError).httpCode)
    }

    @Test
    fun `server error when posting the credentials is reported as ServerError`() = runTest {
        enqueueLoginPage()
        server.enqueue(MockResponse().setResponseCode(502))

        val error = runCatching { authenticator().authenticate(credentials) }.exceptionOrNull()

        assertTrue(error is AuthException.ServerError)
        assertEquals(502, (error as AuthException.ServerError).httpCode)
    }

    @Test
    fun `rejected credentials are still reported as LoginFailed`() = runTest {
        enqueueLoginPage()
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { authenticator().authenticate(credentials) }.exceptionOrNull()

        assertTrue(error is AuthException.LoginFailed)
    }

    @Test
    fun `forbidden credentials are reported as LoginFailed`() = runTest {
        enqueueLoginPage()
        server.enqueue(MockResponse().setResponseCode(403))

        val error = runCatching { authenticator().authenticate(credentials) }.exceptionOrNull()

        assertTrue(error is AuthException.LoginFailed)
    }

    @Test
    fun `rate limit while posting is reported as a transient Network error`() = runTest {
        enqueueLoginPage()
        server.enqueue(MockResponse().setResponseCode(429))

        val error = runCatching { authenticator().authenticate(credentials) }.exceptionOrNull()

        assertTrue(
            "Un 429 no es una contraseña incorrecta y no debe borrar credenciales",
            error is AuthException.Network,
        )
    }
}
