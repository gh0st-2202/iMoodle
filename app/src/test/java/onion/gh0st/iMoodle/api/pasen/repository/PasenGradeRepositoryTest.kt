package onion.gh0st.iMoodle.api.pasen.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Before
import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import onion.gh0st.iMoodle.data.pasen.PasenGradeCache
import onion.gh0st.iMoodle.domain.model.session.Enrollment
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.fakes.FakeCredentialsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PasenGradeRepository].
 *
 * Cubren el flujo completo (login, matrícula y descarga) y el parseo
 * defensivo de `subjects`, cuyo esquema no está documentado por
 * iPasen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasenGradeRepositoryTest {

    private val credentials = JuntaCredentials("usuario", "contraseña")
    private val credentialsStore = FakeCredentialsStore()
    private val apiClient = mockk<PasenApiClient>()
    private val gradeCache = mockk<PasenGradeCache>(relaxed = true)

    @Before
    fun setup() {
        coEvery { gradeCache.get(any()) } returns null
    }

    @Test
    fun `parses convocatorias and subjects`() = runTest {
        credentialsStore.save(credentials)
        coEvery { apiClient.login(credentials) } returns "token"
        coEvery { apiClient.getSessionInfo("token") } returns Enrollment("123", "41000000", "Centro")
        coEvery { apiClient.getNotasConvocatorias("token", "123") } returns VALID_JSON

        val convocatorias = PasenGradeRepository(apiClient, credentialsStore, gradeCache).getGrades()

        assertEquals(1, convocatorias.size)
        with(convocatorias.single()) {
            assertEquals("1ª Evaluación", course)
            assertTrue(isActive)
            assertEquals("01/10/2026", dateStart)
            assertEquals("22/12/2026", dateFinish)
            assertEquals(9_000_001, idCenter)
            assertEquals(2, subjects.size)
        }
        with(convocatorias.single().subjects.first()) {
            assertEquals("Acceso a datos", name)
            assertEquals("8,5", grade)
            assertEquals("7,2", average)
            assertEquals("Buen trabajo", annotation)
            assertTrue(fields.any { (key, value) -> key == "observaciones" && value == "Sin incidencias" })
        }
        with(convocatorias.single().subjects.last()) {
            assertEquals("Programación", name)
            assertNull(grade)
            assertNull(average)
            assertNull(annotation)
        }
        coVerify { apiClient.getNotasConvocatorias("token", "123") }
    }

    @Test
    fun `returns an empty list when the response is not successful`() = runTest {
        credentialsStore.save(credentials)
        coEvery { apiClient.login(credentials) } returns "token"
        coEvery { apiClient.getSessionInfo("token") } returns Enrollment("123", "41000000", "Centro")
        coEvery { apiClient.getNotasConvocatorias("token", "123") } returns
            """{"success":false,"message":"Token expirado"}"""

        val convocatorias = PasenGradeRepository(apiClient, credentialsStore, gradeCache).getGrades()

        assertTrue(convocatorias.isEmpty())
    }

    @Test
    fun `returns an empty list when data is missing`() = runTest {
        credentialsStore.save(credentials)
        coEvery { apiClient.login(credentials) } returns "token"
        coEvery { apiClient.getSessionInfo("token") } returns Enrollment("123", "41000000", "Centro")
        coEvery { apiClient.getNotasConvocatorias("token", "123") } returns """{"success":true}"""

        val convocatorias = PasenGradeRepository(apiClient, credentialsStore, gradeCache).getGrades()

        assertTrue(convocatorias.isEmpty())
    }

    @Test
    fun `fails when there are no stored credentials`() = runTest {
        val result = runCatching {
            PasenGradeRepository(apiClient, credentialsStore, gradeCache).getGrades()
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    private companion object {
        const val VALID_JSON = """
            {
              "success": true,
              "data": [
                {
                  "course": "1ª Evaluación",
                  "isFinished": false,
                  "dateStart": "01/10/2026",
                  "dateFinish": "22/12/2026",
                  "isActive": true,
                  "idCenter": 9000001,
                  "subjects": [
                    {
                      "subject": "Acceso a datos",
                      "grade": "8,5",
                      "average": "7,2",
                      "annotation": "Buen trabajo",
                      "observaciones": "Sin incidencias"
                    },
                    {
                      "name": "Programación"
                    }
                  ]
                }
              ]
            }
        """
    }
}
