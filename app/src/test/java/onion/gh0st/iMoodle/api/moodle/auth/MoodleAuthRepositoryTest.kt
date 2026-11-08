package onion.gh0st.iMoodle.api.moodle.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.fakes.FakeCredentialsStore
import onion.gh0st.iMoodle.fakes.FakeHttp
import onion.gh0st.iMoodle.fakes.FakeTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Tests de [MoodleAuthRepository.tryAutoLogin].
 *
 * El caso crítico es el arranque **sin red**: cualquier excepción al
 * validar el token debe interpretarse como "sin conexión", no como
 * "token inválido". Si se interpretara como inválido, la app borraría
 * la sesión guardada y el usuario perdería el acceso a sus datos
 * cacheados.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleAuthRepositoryTest {

    /** Almacén de tokens de prueba. */
    private val tokenStore = FakeTokenStore()

    /** Almacén de credenciales de prueba. */
    private val credentialsStore = FakeCredentialsStore()

    /**
     * Construye el repositorio con las dependencias por defecto.
     *
     * @param client Cliente HTTP que decide las respuestas.
     * @return El repositorio listo para test.
     */
    private fun createRepo(client: OkHttpClient): MoodleAuthRepository =
        MoodleAuthRepository(
            tokenStore = tokenStore,
            credentialsStore = credentialsStore,
            httpClient = client,
        )

    /**
     * Guarda una sesión de prueba en el almacén.
     *
     * @param token Token a guardar.
     */
    private suspend fun seedStored(token: String = "tok") {
        tokenStore.save(
            StoredSession(
                provinciaSlug = Provincia.CORDOBA.slug,
                user = "jdoe",
                wstoken = token,
                lastUsedAt = 1L,
                userId = 7,
            ),
        )
    }

    @Test
    fun `no stored session returns null without touching the network`() = runTest {
        var callCount = 0
        val repo = createRepo(FakeHttp.client { callCount++; error("no debe llamar") })

        assertNull(repo.tryAutoLogin())
        assertEquals(0, callCount)
    }

    @Test
    fun `offline start keeps the stored session and recovers it`() = runTest {
        seedStored()
        val repo = createRepo(FakeHttp.client { throw UnknownHostException("offline") })

        val recoveredSession = repo.tryAutoLogin()

        assertNotNull("Sin red la sesión debe recuperarse igualmente", recoveredSession)
        assertEquals(Provincia.CORDOBA, recoveredSession!!.provincia)
        assertEquals(7, recoveredSession.userId)
        assertNotNull(
            "El token no debe borrarse sin red",
            tokenStore.load(Provincia.CORDOBA.slug),
        )
    }

    @Test
    fun `timeout also keeps the stored session`() = runTest {
        seedStored()
        val repo = createRepo(FakeHttp.client { throw SocketTimeoutException("slow") })

        assertNotNull(repo.tryAutoLogin())
        assertNotNull(tokenStore.load(Provincia.CORDOBA.slug))
    }

    @Test
    fun `token rejected by moodle clears the stored session`() = runTest {
        seedStored()
        val repo = createRepo(
            FakeHttp.client { FakeHttp.response(it, FakeHttp.INVALID_TOKEN_JSON) },
        )

        assertNull(repo.tryAutoLogin())
        assertNull(tokenStore.load(Provincia.CORDOBA.slug))
    }

    @Test
    fun `valid token recovers the session and keeps it stored`() = runTest {
        seedStored()
        val repo = createRepo(
            FakeHttp.client { FakeHttp.response(it, """{"sitename":"Aulas"}""") },
        )

        val recoveredSession = repo.tryAutoLogin()

        assertNotNull(recoveredSession)
        assertEquals("jdoe", recoveredSession!!.user)
        assertNotNull(tokenStore.load(Provincia.CORDOBA.slug))
    }

    @Test
    fun `server error page is not mistaken for an invalid token`() = runTest {
        seedStored()
        val repo = createRepo(
            FakeHttp.client { FakeHttp.response(it, "<html>Bad gateway</html>", code = 502) },
        )

        assertNotNull(repo.tryAutoLogin())
        assertNotNull(tokenStore.load(Provincia.CORDOBA.slug))
    }

    @Test
    fun `token is url encoded when probing`() = runTest {
        seedStored(token = "a b+c")
        var sentToken: String? = null
        val repo = createRepo(
            FakeHttp.client {
                sentToken = it.url.queryParameter("wstoken")
                FakeHttp.response(it, "{}")
            },
        )

        repo.tryAutoLogin()

        assertEquals("a b+c", sentToken)
    }
}