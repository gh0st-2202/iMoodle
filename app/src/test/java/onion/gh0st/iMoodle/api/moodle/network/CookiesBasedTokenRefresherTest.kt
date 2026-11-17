package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.fakes.FakeHttp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Base64

/**
 * Tests de [CookiesBasedTokenRefresher].
 *
 * ## Qué protegen
 *
 * La diferencia entre un fallo **definitivo** y uno **transitorio**:
 *
 * - `null` solo cuando la sesión CAS ya no sirve (redirección sin
 *   token o `401`/`403`). El cliente cerrará la sesión.
 * - Excepción en fallos transitorios (red, `429`, `5xx`). El cliente
 *   fallará la petición actual, pero **conservará** la sesión.
 *
 * Antes, cualquier error se convertía en `null` y un simple corte de
 * red expulsaba al usuario y borraba sus datos locales.
 */
class CookiesBasedTokenRefresherTest {

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
     * Construye un refresher que apunta al servidor simulado.
     *
     * @param client Cliente HTTP base (por defecto, uno real).
     * @return El refresher listo para la prueba.
     */
    private fun refresher(client: OkHttpClient = OkHttpClient()) =
        CookiesBasedTokenRefresher(
            authenticatedClient = client,
            config = MoodleConfig(
                provinciaSlug = "cordoba",
                moodleHost = "${server.hostName}:${server.port}",
                protocol = "http",
            ),
        )

    /** Codifica un token con el formato real (`firma:::token:::privado`). */
    private fun encodedToken(token: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("firma:::$token:::privado".toByteArray())

    @Test
    fun `http 5xx is thrown as ServerError and does not return null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { refresher().refresh() }.exceptionOrNull()

        assertTrue(
            "Un 5xx debe ser transitorio, no un cierre de sesión",
            error is AuthException.ServerError,
        )
        assertEquals(503, (error as AuthException.ServerError).httpCode)
    }

    @Test
    fun `http 429 is thrown as a transient network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val error = runCatching { refresher().refresh() }.exceptionOrNull()

        assertTrue(error is AuthException.Network)
    }

    @Test
    fun `http 401 means the server rejected the session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        assertNull(refresher().refresh())
    }

    @Test
    fun `http 403 means the server rejected the session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertNull(refresher().refresh())
    }

    @Test
    fun `redirect without token means the CAS cookies expired`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://cas.example/login"),
        )

        assertNull(refresher().refresh())
    }

    @Test
    fun `redirect with a valid token is decoded`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "moodlemobile://token=${encodedToken("wstoken-123")}"),
        )

        assertEquals("wstoken-123", refresher().refresh())
    }

    @Test
    fun `transport failure is thrown as a network error`() = runTest {
        val offlineClient = FakeHttp.client { throw IOException("sin conexión") }

        val error = runCatching { refresher(offlineClient).refresh() }.exceptionOrNull()

        assertTrue(error is AuthException.Network)
    }
}
