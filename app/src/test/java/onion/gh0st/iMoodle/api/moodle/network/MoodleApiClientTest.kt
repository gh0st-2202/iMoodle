package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.fakes.FakeHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests de [MoodleApiClient]: refresco de token, concurrencia y mapeo
 * de errores. Usan un cliente HTTP falso (sin red).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleApiClientTest {

    private val okBody = """[{"id":1}]"""

    private fun client(
        refresher: TokenRefresher = TokenRefresher { null },
        onInvalid: suspend () -> Unit = {},
        onRefreshed: suspend (String) -> Unit = {},
        handler: (Request) -> Response,
    ) = MoodleApiClient(
        client = FakeHttp.client(handler),
        config = MoodleConfig(provinciaSlug = "cordoba"),
        tokenRefresher = refresher,
        onTokenRefreshed = onRefreshed,
        onInvalidToken = onInvalid,
        initialToken = "old",
    )

    /** Moodle acepta solo el token "new"; con cualquier otro responde `invalidtoken`. */
    private fun expiredUnlessNew(request: Request): Response =
        if (request.url.queryParameter("wstoken") == "new") {
            FakeHttp.response(request, okBody)
        } else {
            FakeHttp.response(request, FakeHttp.INVALID_TOKEN_JSON)
        }

    // --- Refresco de token ---

    @Test
    fun `expired token is refreshed and the call retried`() = runTest {
        val refreshed = mutableListOf<String>()
        val api = client(
            refresher = TokenRefresher { "new" },
            onRefreshed = { refreshed += it },
            handler = { expiredUnlessNew(it) },
        )

        assertEquals(okBody, api.callREST("core_x"))
        assertEquals("new", api.token.value)
        assertEquals(listOf("new"), refreshed)
    }

    @Test
    fun `concurrent calls with an expired token refresh only once`() = runTest {
        val refreshCalls = AtomicInteger()
        val api = client(
            refresher = TokenRefresher {
                refreshCalls.incrementAndGet()
                delay(50)
                "new"
            },
            handler = { expiredUnlessNew(it) },
        )

        val results = withContext(Dispatchers.Default) {
            (1..8).map { async { api.callREST("core_x") } }.awaitAll()
        }

        assertTrue(results.all { it == okBody })
        assertEquals("El refresco debe ser single-flight", 1, refreshCalls.get())
    }

    // --- Token irrecuperable ---

    @Test
    fun `failed refresh invalidates exactly once even with parallel calls`() = runTest {
        val invalidations = AtomicInteger()
        val api = client(
            refresher = TokenRefresher { null },
            onInvalid = { invalidations.incrementAndGet() },
            handler = { FakeHttp.response(it, FakeHttp.INVALID_TOKEN_JSON) },
        )

        val errors = withContext(Dispatchers.Default) {
            (1..6).map { async { runCatching { api.callREST("core_x") }.exceptionOrNull() } }
                .awaitAll()
        }

        assertTrue(errors.all { it is AuthException.InvalidToken })
        assertEquals(1, invalidations.get())
    }

    @Test
    fun `later calls after invalidation do not notify again`() = runTest {
        val invalidations = AtomicInteger()
        val api = client(
            onInvalid = { invalidations.incrementAndGet() },
            handler = { FakeHttp.response(it, FakeHttp.INVALID_TOKEN_JSON) },
        )

        repeat(3) { runCatching { api.callREST("core_x") } }

        assertEquals(1, invalidations.get())
    }

    // --- Errores de Moodle / HTTP ---

    @Test
    fun `moodle exception becomes MoodleApiException and is not an invalid token`() = runTest {
        val refreshCalls = AtomicInteger()
        val invalidations = AtomicInteger()
        val api = client(
            refresher = TokenRefresher { refreshCalls.incrementAndGet(); "new" },
            onInvalid = { invalidations.incrementAndGet() },
            handler = {
                FakeHttp.response(
                    it,
                    """{"exception":"webservice_access_exception","errorcode":"accessexception","message":"Access control exception"}""",
                )
            },
        )

        val error = runCatching { api.callREST("core_x") }.exceptionOrNull()

        assertTrue(error is MoodleApiException)
        error as MoodleApiException
        assertEquals("accessexception", error.errorCode)
        assertEquals("Access control exception", error.message)
        assertEquals(0, refreshCalls.get())
        assertEquals(0, invalidations.get())
    }

    @Test
    fun `unicode escapes in the moodle message are decoded`() = runTest {
        val api = client(
            handler = {
                FakeHttp.response(
                    it,
                    """{"exception":"moodle_exception","errorcode":"x","message":"Funci\u00f3n no disponible"}""",
                )
            },
        )

        val error = runCatching { api.callREST("core_x") }.exceptionOrNull()

        assertEquals("Función no disponible", error?.message)
    }

    @Test
    fun `non successful http status becomes MoodleApiException`() = runTest {
        val api = client(handler = { FakeHttp.response(it, "<html>oops</html>", code = 500) })

        val error = runCatching { api.callREST("core_x") }.exceptionOrNull()

        assertTrue(error is MoodleApiException)
        assertEquals("http_500", (error as MoodleApiException).errorCode)
    }

    @Test
    fun `successful payloads that mention exception are not treated as errors`() = runTest {
        val bodies = listOf(
            """[{"name":"exception handling"}]""",
            """{"events":[],"warnings":[{"warningcode":"exception"}]}""",
        )
        bodies.forEach { body ->
            val api = client(handler = { FakeHttp.response(it, body) })
            assertEquals(body, api.callREST("core_x"))
        }
    }

    @Test
    fun `io failure becomes AuthException Network`() = runTest {
        val api = client(handler = { throw IOException("sin conexión") })

        val error = runCatching { api.callREST("core_x") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is AuthException.Network)
    }
}
