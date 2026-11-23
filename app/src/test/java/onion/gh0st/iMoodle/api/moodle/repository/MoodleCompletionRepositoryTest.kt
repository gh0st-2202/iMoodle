package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MoodleCompletionRepository].
 *
 * Cubren la traducción de cada módulo a su función `view`, el caso de
 * un módulo sin vista registrable y el cambio manual de finalización.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleCompletionRepositoryTest {

    @Test
    fun `assign view uses mod_assign_view_assign`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        repository.markModuleViewed(module(modname = "assign", instance = 42))

        assertEquals("mod_assign_view_assign", api.lastFunction)
        assertEquals(mapOf("assignid" to "42"), api.lastParams)
    }

    @Test
    fun `glossary view sends the mode`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        repository.markModuleViewed(module(modname = "glossary", instance = 7))

        assertEquals("mod_glossary_view_glossary", api.lastFunction)
        assertEquals(mapOf("id" to "7", "mode" to "letter"), api.lastParams)
    }

    @Test
    fun `unknown module does not call Moodle`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        val result = repository.markModuleViewed(module(modname = "label", instance = 7))

        assertTrue(result.isSuccess)
        assertNull(api.lastFunction)
    }

    @Test
    fun `manual completion sends 1 when completed`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        repository.setModuleCompleted(cmId = 9, completed = true)

        assertEquals(
            "core_completion_update_activity_completion_status_manually",
            api.lastFunction,
        )
        assertEquals(mapOf("cmid" to "9", "completed" to "1"), api.lastParams)
    }

    @Test
    fun `manual completion sends 0 when pending`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        repository.setModuleCompleted(cmId = 9, completed = false)

        assertEquals(mapOf("cmid" to "9", "completed" to "0"), api.lastParams)
    }

    @Test
    fun `network failures are returned instead of thrown`() = runTest {
        val api = RecordingApiClient(error = RuntimeException("boom"))
        val repository = MoodleCompletionRepository(FakeSessionApiProvider(api))

        val result = repository.setModuleCompleted(cmId = 9, completed = true)

        assertTrue(result.isFailure)
    }

    private fun module(modname: String, instance: Int) = CourseModule(
        id = 1,
        instance = instance,
        name = "Módulo",
        modname = modname,
        url = null,
        description = null,
    )

    private class FakeSessionApiProvider(
        private val api: MoodleApiClient,
    ) : SessionApiProvider {
        override fun requireApi(): MoodleApiClient = api
    }

    private class RecordingApiClient(
        private val error: Throwable? = null,
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
            error?.let { failure -> throw failure }
            return "{\"status\":true,\"warnings\":[]}"
        }
    }
}
