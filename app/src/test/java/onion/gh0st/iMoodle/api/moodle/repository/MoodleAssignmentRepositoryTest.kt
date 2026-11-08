package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiException
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.fakes.FakeHttp
import onion.gh0st.iMoodle.fakes.FakeSessionApiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests de [MoodleAssignmentRepository].
 *
 * Cubre especialmente el bug P0 #3: si `get_submission_status` falla,
 * el detalle se construye igualmente con `statusUnknown = true` para
 * que la UI muestre "Estado desconocido" en lugar de "Sin entregar".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleAssignmentRepositoryTest {

    @Test
    fun `detail with submission status fills grade and submission fields`() = runTest {
        val repo = repo(assignments = ASSIGNMENTS_JSON, submission = SUBMISSION_JSON)

        val detail = repo.getAssignmentDetail(courseId = 1, assignmentId = 500)

        assertNotNull(detail)
        detail!!
        assertEquals(500, detail.id)
        assertEquals("Tarea 1", detail.name)
        assertEquals("submitted", detail.submissionStatus)
        assertEquals(7.5, detail.submissionGrade!!, 0.001)
        assertEquals("7,5 / 10", detail.gradeDisplay)
        assertTrue(detail.submissionGraded)
        assertFalse(detail.statusUnknown)
    }

    @Test
    fun `submission status failure marks the detail as statusUnknown`() = runTest {
        val repo = repo(assignments = ASSIGNMENTS_JSON, submission = null)

        val detail = repo.getAssignmentDetail(courseId = 1, assignmentId = 500)

        assertNotNull(detail)
        detail!!
        assertEquals(500, detail.id)
        assertNull(detail.submissionStatus)
        assertNull(detail.submissionGrade)
        assertNull(detail.gradeDisplay)
        assertFalse(detail.submissionGraded)
        assertTrue("El estado debe marcarse como desconocido", detail.statusUnknown)
    }

    @Test
    fun `unknown assignment returns null`() = runTest {
        val repo = repo(assignments = ASSIGNMENTS_JSON, submission = SUBMISSION_JSON)

        assertNull(repo.getAssignmentDetail(courseId = 1, assignmentId = 999))
    }

    @Test
    fun `http error on assignments propagates as MoodleApiException`() = runTest {
        val client = FakeHttp.client { request ->
            FakeHttp.response(request, "<html>oops</html>", code = 500)
        }
        val repo = MoodleAssignmentRepository(FakeSessionApiProvider(api(client)))

        val exception = runCatching {
            repo.getAssignmentDetail(courseId = 1, assignmentId = 500)
        }.exceptionOrNull()

        assertTrue(exception is MoodleApiException)
    }

    // --- Helpers ---

    /**
     * Construye el repositorio con respuestas pregrabadas.
     *
     * @param assignments JSON a devolver para `mod_assign_get_assignments`
     *   (o `null` para simular error de red).
     * @param submission JSON a devolver para
     *   `mod_assign_get_submission_status` (o `null` para simular error
     *   de red).
     */
    private fun repo(
        assignments: String?,
        submission: String?,
    ): MoodleAssignmentRepository {
        val client = FakeHttp.client { request ->
            when (request.url.queryParameter("wsfunction")) {
                "mod_assign_get_assignments" ->
                    if (assignments == null) throw IOException("offline")
                    else FakeHttp.response(request, assignments)

                "mod_assign_get_submission_status" ->
                    if (submission == null) throw IOException("offline")
                    else FakeHttp.response(request, submission)

                else -> error("wsfunction inesperado: ${request.url}")
            }
        }
        return MoodleAssignmentRepository(FakeSessionApiProvider(api(client)))
    }

    private fun api(client: OkHttpClient): MoodleApiClient = MoodleApiClient(
        client = client,
        config = MoodleConfig(provinciaSlug = "cordoba"),
        tokenRefresher = TokenRefresher { null },
        initialToken = "test_token",
    )

    private companion object {
        const val ASSIGNMENTS_JSON = """
            {
              "courses": [
                {
                  "id": 1,
                  "assignments": [
                    {
                      "id": 500, "course": 1, "name": "Tarea 1",
                      "intro": "<p>Enunciado</p>",
                      "duedate": 1800000000,
                      "allowsubmissionsfromdate": 1700000000,
                      "cutoffdate": 0,
                      "grade": 10.0
                    }
                  ]
                }
              ]
            }
        """

        const val SUBMISSION_JSON = """
            {
              "lastattempt": {
                "submission": {
                  "id": 9001,
                  "status": "submitted",
                  "timemodified": 1790000000
                },
                "graded": true,
                "cansubmit": false
              },
              "feedback": {
                "grade": {"grade": 7.5},
                "gradefordisplay": "7,5 / 10"
              }
            }
        """
    }
}