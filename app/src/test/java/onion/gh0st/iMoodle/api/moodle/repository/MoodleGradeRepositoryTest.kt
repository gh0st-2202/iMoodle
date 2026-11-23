package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MoodleGradeRepository].
 *
 * Cubren el parseo de la fila total, de las actividades y de los
 * campos opcionales (nota ausente, nota oculta y comentario).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleGradeRepositoryTest {

    @Test
    fun `parses the course total and the activity rows`() = runTest {
        val api = RecordingApiClient(GRADES_JSON)
        val repository = MoodleGradeRepository(FakeSessionApiProvider(api))

        val grade = repository.getCourseGrades(courseId = 7, userId = 42)

        assertEquals(7, grade.courseId)
        assertEquals(3, grade.items.size)
        assertEquals("7,50 / 10,00", grade.total?.gradeFormatted)
        assertEquals(2, grade.activities.size)
        assertEquals(listOf("Práctica 1", "Cuestionario 1"), grade.activities.map { it.name })
        assertEquals(7.5, grade.activities[0].gradeRaw!!, 0.001)
        assertEquals(8.0, grade.activities[1].gradeMin!!, 0.001)
        assertEquals(10.0, grade.activities[1].gradeMax!!, 0.001)
        assertTrue(grade.activities[1].isLocked)
        assertNull(grade.activities[0].feedback)
        assertEquals("Buen trabajo", grade.activities[1].feedback)
    }

    @Test
    fun `sends the course and user to Moodle`() = runTest {
        val api = RecordingApiClient(GRADES_JSON)
        val repository = MoodleGradeRepository(FakeSessionApiProvider(api))

        repository.getCourseGrades(courseId = 7, userId = 42)

        assertEquals("gradereport_user_get_grade_items", api.lastFunction)
        assertEquals(mapOf("courseid" to "7", "userid" to "42"), api.lastParams)
    }

    @Test
    fun `omits the user when it is unknown`() = runTest {
        val api = RecordingApiClient(GRADES_JSON)
        val repository = MoodleGradeRepository(FakeSessionApiProvider(api))

        repository.getCourseGrades(courseId = 7)

        assertEquals(mapOf("courseid" to "7"), api.lastParams)
    }

    @Test
    fun `a row without grade has no numeric value`() = runTest {
        val api = RecordingApiClient(GRADES_WITHOUT_GRADE_JSON)
        val repository = MoodleGradeRepository(FakeSessionApiProvider(api))

        val item = repository.getCourseGrades(courseId = 7).activities.single()

        assertNull(item.gradeRaw)
        assertFalse(item.hasGrade)
        assertTrue(item.isHidden)
        assertNull(item.feedback)
    }

    @Test
    fun `fails when Moodle returns no grades`() = runTest {
        val api = RecordingApiClient("""{"usergrades":[]}""")
        val repository = MoodleGradeRepository(FakeSessionApiProvider(api))

        val result = runCatching { repository.getCourseGrades(courseId = 7) }

        assertTrue(result.isFailure)
    }

    private class FakeSessionApiProvider(
        private val api: MoodleApiClient,
    ) : SessionApiProvider {
        override fun requireApi(): MoodleApiClient = api
    }

    private class RecordingApiClient(
        private val response: String,
    ) : MoodleApiClient(
        client = OkHttpClient(),
        config = MoodleConfig(provinciaSlug = "cordoba"),
        tokenRefresher = TokenRefresher { null },
        initialToken = "token-de-prueba",
    ) {
        var lastFunction: String? = null
            private set

        var lastParams: Map<String, String> = emptyMap()
            private set

        override suspend fun callREST(function: String, params: MoodleParams): String {
            lastFunction = function
            lastParams = params.build()
            return response
        }
    }

    private companion object {
        const val GRADES_JSON = """
            {
              "usergrades": [
                {
                  "courseid": 7,
                  "coursename": "Acceso a datos",
                  "gradeitems": [
                    {
                      "id": 1, "itemname": "Total del curso", "itemtype": "course",
                      "gradeformatted": "7,50 / 10,00", "graderaw": 7.5,
                      "grademin": 0, "grademax": 10
                    },
                    {
                      "id": 2, "itemname": "Práctica 1", "itemtype": "mod",
                      "itemmodule": "assign", "gradeformatted": "7,50 / 10,00",
                      "graderaw": 7.5, "grademin": 0, "grademax": 10
                    },
                    {
                      "id": 3, "itemname": "Cuestionario 1", "itemtype": "mod",
                      "itemmodule": "quiz", "gradeformatted": "8,00 / 10,00",
                      "graderaw": 8.0, "grademin": 8, "grademax": 10,
                      "feedback": "Buen trabajo", "gradeislocked": 1
                    }
                  ]
                }
              ]
            }
        """

        const val GRADES_WITHOUT_GRADE_JSON = """
            {
              "usergrades": [
                {
                  "courseid": 7,
                  "gradeitems": [
                    {
                      "id": 2, "itemname": "Práctica 1", "itemtype": "mod",
                      "gradeformatted": "", "gradenull": true, "gradeishidden": 1
                    }
                  ]
                }
              ]
            }
        """
    }
}
