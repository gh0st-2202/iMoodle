package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiException
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.fakes.FakeAssignmentDao
import onion.gh0st.iMoodle.fakes.FakeCourseDao
import onion.gh0st.iMoodle.fakes.FakeSessionApiProvider
import onion.gh0st.iMoodle.fakes.RecordingTransactionRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios de [MoodleCourseRepository] con DAOs fake.
 *
 * Verifica el flujo Offline-First sin Room real ni Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleCourseRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var courseDao: FakeCourseDao
    private lateinit var assignmentDao: FakeAssignmentDao
    private lateinit var repo: MoodleCourseRepository
    private val runner = RecordingTransactionRunner()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = realApi(server)
        val sessionApi = FakeSessionApiProvider(api)
        courseDao = FakeCourseDao()
        assignmentDao = FakeAssignmentDao()
        repo = MoodleCourseRepository(
            sessionApi = sessionApi,
            courseDao = courseDao,
            assignmentDao = assignmentDao,
            transactionRunner = runner,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- refreshCourses + getCoursesFlow ---

    @Test
    fun `refreshCourses persists courses and getCoursesFlow emits them`() = runTest {
        enqueueRefreshResponses()
        repo.refreshCourses(userId = 1)
        val courses = repo.getCoursesFlow(userId = 1).first()
        assertEquals(2, courses.size)
        assertEquals("Course One", courses[0].fullname)
        assertEquals("Course Two", courses[1].fullname)
        assertEquals(2, courseDao.courses.size)
        assertEquals(1, assignmentDao.assignments.size)
    }

    @Test
    fun `refreshCourses sends correct requests`() = runTest {
        enqueueRefreshResponses()
        repo.refreshCourses(userId = 42)
        val first = server.takeRequest()
        assertTrue(first.path!!.contains("wsfunction=core_enrol_get_users_courses"))
        assertTrue(first.path!!.contains("userid=42"))
        val second = server.takeRequest()
        assertTrue(second.path!!.contains("wsfunction=mod_assign_get_assignments"))
    }

    @Test
    fun `second refresh replaces previous data`() = runTest {
        enqueueRefreshResponses()
        enqueueRefreshResponses()
        repo.refreshCourses(userId = 1)
        repo.refreshCourses(userId = 1)
        val courses = repo.getCoursesFlow(userId = 1).first()
        assertEquals(2, courses.size)
        assertEquals(2, courseDao.courses.size)
    }

    // --- getCourseContents ---

    @Test
    fun `getCourseContents parses sections modules and files`() = runTest {
        val fileUrl = "http://${server.hostName}:${server.port}/webservice/pluginfile.php/1/apuntes.pdf"
        server.enqueue(
            jsonResponse(COURSE_CONTENTS_JSON.replace("http://example.com/apuntes.pdf", fileUrl)),
        )
        val sections = repo.getCourseContents(courseId = 1)
        assertEquals(1, sections.size)
        val section = sections.single()
        assertEquals(10, section.id)
        assertEquals("Tema 1", section.name)
        assertEquals(1, section.modules.size)
        val module = section.modules.single()
        assertEquals(100, module.id)
        assertEquals(200, module.instance)
        assertEquals("resource", module.modname)
        assertEquals(1, module.files.size)
        val file = module.files.single()
        assertEquals("apuntes.pdf", file.fileName)
        assertEquals("$fileUrl?token=test_token", file.fileUrl)
        assertEquals(1024L, file.fileSize)
        assertEquals("application/pdf", file.mimeType)
    }

    // --- getUrlExternalUrl ---

    @Test
    fun `getUrlExternalUrl returns external url for instance`() = runTest {
        server.enqueue(jsonResponse(URLS_JSON))
        val url = repo.getUrlExternalUrl(courseId = 1, instanceId = 200)
        assertEquals("https://example.com/target", url)
    }

    @Test
    fun `getUrlExternalUrl returns null for unknown instance`() = runTest {
        server.enqueue(jsonResponse(URLS_JSON))
        val url = repo.getUrlExternalUrl(courseId = 1, instanceId = 999)
        assertNull(url)
    }

    // --- Seguridad del token ---

    @Test
    fun `getCourseContents never sends the token to foreign hosts`() = runTest {
        server.enqueue(jsonResponse(COURSE_CONTENTS_JSON))
        val file = repo.getCourseContents(courseId = 1).single().modules.single().files.single()
        assertEquals("http://example.com/apuntes.pdf", file.fileUrl)
    }

    @Test
    fun `course image is persisted without token and token is added when reading`() = runTest {
        val image = "http://${server.hostName}:${server.port}/webservice/pluginfile.php/5/img.jpg"
        server.enqueue(
            jsonResponse(
                """[{"id":1,"fullname":"C","shortname":"c","summary":"",
                    "overviewfiles":[{"fileurl":"$image"}]}]""",
            ),
        )
        server.enqueue(jsonResponse("""{"courses":[]}"""))
        repo.refreshCourses(userId = 1)
        assertEquals("Room no debe guardar el token", image, courseDao.courses.single().imageUrl)
        assertEquals(
            "$image?token=test_token",
            repo.getCoursesFlow(userId = 1).first().single().imageUrl,
        )
    }

    // --- Atomicidad ---

    @Test
    fun `refresh replaces data in a single transaction`() = runTest {
        enqueueRefreshResponses()
        repo.refreshCourses(userId = 1)
        assertEquals(1, runner.runs)
    }

    @Test
    fun `network failure while refreshing keeps existing courses`() = runTest {
        enqueueRefreshResponses()
        repo.refreshCourses(userId = 1)
        server.enqueue(MockResponse().setResponseCode(500))
        val error = runCatching { repo.refreshCourses(userId = 1) }.exceptionOrNull()
        assertTrue(error is MoodleApiException)
        assertEquals(2, courseDao.courses.size)
        assertEquals("Solo la primera pasada debe haber escrito", 1, runner.runs)
    }

    // --- Helpers ---

    private fun enqueueRefreshResponses() {
        server.enqueue(jsonResponse(ENROLLED_COURSES_JSON))
        server.enqueue(jsonResponse(ASSIGNMENTS_JSON))
    }

    private fun realApi(server: MockWebServer): MoodleApiClient = MoodleApiClient(
        client = OkHttpClient(),
        config = MoodleConfig(
            provinciaSlug = "cordoba",
            moodleHost = "${server.hostName}:${server.port}",
            protocol = "http",
        ),
        tokenRefresher = TokenRefresher { null },
        initialToken = "test_token",
    )

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    private companion object {
        const val ENROLLED_COURSES_JSON = """
            [
              {
                "id": 1, "fullname": "Course One", "shortname": "C1",
                "summary": "", "progress": 50, "startdate": 1700000000, "enddate": 1800000000,
                "overviewfiles": [{ "fileurl": "http://example.com/img1.jpg" }]
              },
              {
                "id": 2, "fullname": "Course Two", "shortname": "C2",
                "summary": "", "progress": -1, "startdate": 0, "enddate": 0
              }
            ]
        """

        const val ASSIGNMENTS_JSON = """
            {
              "courses": [
                {
                  "id": 1,
                  "assignments": [
                    {
                      "id": 500, "course": 1, "name": "Tarea 1", "intro": "Descripción",
                      "duedate": 1800000000, "allowsubmissionsfromdate": 1700000000, "grade": 10.0
                    }
                  ]
                }
              ]
            }
        """

        const val COURSE_CONTENTS_JSON = """
            [
              {
                "id": 10, "name": "Tema 1",
                "modules": [
                  {
                    "id": 100, "instance": 200, "name": "Apuntes", "modname": "resource",
                    "url": "http://example.com", "description": "Descripción",
                    "contents": [
                      {
                        "filename": "apuntes.pdf", "fileurl": "http://example.com/apuntes.pdf",
                        "filesize": 1024, "mimetype": "application/pdf", "timemodified": 1700000000
                      }
                    ]
                  }
                ]
              }
            ]
        """

        const val URLS_JSON = """
            {
              "urls": [
                { "id": 100, "externalurl": "https://example.com/other" },
                { "id": 200, "externalurl": "https://example.com/target" }
              ]
            }
        """
    }
}