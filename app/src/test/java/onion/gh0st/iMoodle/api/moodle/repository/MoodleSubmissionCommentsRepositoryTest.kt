package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MoodleSubmissionCommentsRepository].
 *
 * Se centran en la forma de los parámetros que viajan a Moodle, porque
 * la API es muy estricta con las estructuras anidadas y un fallo ahí se
 * manifiesta como un error genérico de "parámetro inválido".
 */
class MoodleSubmissionCommentsRepositoryTest {

    @Test
    fun `addComment nests the thread fields inside the comments array`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleSubmissionCommentsRepository(FakeSessionApiProvider(api))

        val result = repository.addComment(cmId = 77, submissionId = 99, content = "Hola & adiós")

        assertTrue(result.isSuccess)
        assertEquals("core_comment_add_comments", api.lastFunction)
        assertEquals(
            mapOf(
                "comments[0][contextlevel]" to "module",
                "comments[0][instanceid]" to "77",
                "comments[0][component]" to "assignsubmission_comments",
                "comments[0][area]" to "submission_comments",
                "comments[0][itemid]" to "99",
                "comments[0][content]" to "Hola & adiós",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `deleteComment sends the id inside the comments array`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleSubmissionCommentsRepository(FakeSessionApiProvider(api))

        val result = repository.deleteComment(commentId = 1234)

        assertTrue(result.isSuccess)
        assertEquals("core_comment_delete_comments", api.lastFunction)
        assertEquals(mapOf("comments[0]" to "1234"), api.lastParams)
    }

    /** Proveedor que devuelve siempre el mismo cliente falso. */
    private class FakeSessionApiProvider(
        private val api: MoodleApiClient,
    ) : SessionApiProvider {
        override fun requireApi(): MoodleApiClient = api
    }

    /**
     * Cliente que no hace red: guarda lo que se le pide y devuelve `{}`.
     */
    private class RecordingApiClient : MoodleApiClient(
        client = OkHttpClient(),
        config = MoodleConfig(provinciaSlug = "cordoba"),
        tokenRefresher = TokenRefresher { null },
        initialToken = "token-de-prueba",
    ) {
        var lastFunction: String? = null
            private set

        var lastParams: Map<String, String> = emptyMap()
            private set

        override suspend fun callREST(function: String, params: MoodleParams): String = EMPTY_JSON

        override suspend fun callRESTWithFormBody(function: String, params: Map<String, String>): String {
            lastFunction = function
            lastParams = params
            return EMPTY_JSON
        }

        private companion object {
            const val EMPTY_JSON = "{}"
        }
    }
}
