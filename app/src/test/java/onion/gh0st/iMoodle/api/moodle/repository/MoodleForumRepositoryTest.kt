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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MoodleForumRepository].
 *
 * Cubren la lectura de discusiones y posts, el permiso para abrir
 * temas y las dos escrituras (tema nuevo y respuesta).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleForumRepositoryTest {

    @Test
    fun `getDiscussions parses the forum threads`() = runTest {
        val api = RecordingApiClient(readResponse = DISCUSSIONS_JSON)
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        val discussions = repository.getDiscussions(forumId = 4)

        assertEquals(1, discussions.size)
        with(discussions.single()) {
            assertEquals(11, id)
            assertEquals("Duda del tema 3", name)
            assertEquals("Ana", author)
            assertEquals(2, numReplies)
        }
        assertEquals("mod_forum_get_forum_discussions", api.lastFunction)
        assertEquals(mapOf("forumid" to "4"), api.lastParams)
    }

    @Test
    fun `getPosts resolves author and created date`() = runTest {
        val api = RecordingApiClient(readResponse = POSTS_JSON)
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        val posts = repository.getPosts(discussionId = 11)

        assertEquals(2, posts.size)
        assertEquals("Ana", posts[0].author)
        assertEquals(1700000000L, posts[0].created)
        assertFalse(posts[0].isReply)
        assertEquals("Luis", posts[1].author)
        assertTrue(posts[1].isReply)
        assertEquals(
            mapOf(
                "discussionid" to "11",
                "sortby" to "created",
                "sortdirection" to "ASC",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `canAddDiscussion reads the permission flag`() = runTest {
        val api = RecordingApiClient(readResponse = """{"status":true,"canadddiscussion":true}""")
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        assertTrue(repository.canAddDiscussion(forumId = 4))
        assertEquals("mod_forum_can_add_discussion", api.lastFunction)
        assertEquals(mapOf("forumid" to "4"), api.lastParams)
    }

    @Test
    fun `canAddDiscussion is false when Moodle denies it`() = runTest {
        val api = RecordingApiClient(readResponse = """{"status":false,"canadddiscussion":false}""")
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        assertFalse(repository.canAddDiscussion(forumId = 4))
    }

    @Test
    fun `canAddDiscussion is false when the call fails`() = runTest {
        val api = RecordingApiClient(readError = RuntimeException("boom"))
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        assertFalse(repository.canAddDiscussion(forumId = 4))
    }

    @Test
    fun `addDiscussion posts the subject and message`() = runTest {
        val api = RecordingApiClient(writeResponse = """{"discussionid":33,"warnings":[]}""")
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        val discussionId = repository.addDiscussion(
            forumId = 4,
            subject = "Duda nueva",
            message = "¿Cómo se hace?",
        )

        assertEquals(33, discussionId)
        assertEquals("mod_forum_add_discussion", api.lastFunction)
        assertEquals(
            mapOf(
                "forumid" to "4",
                "subject" to "Duda nueva",
                "message" to "¿Cómo se hace?",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `addReply posts against the parent message`() = runTest {
        val api = RecordingApiClient(writeResponse = """{"postid":44,"warnings":[]}""")
        val repository = MoodleForumRepository(FakeSessionApiProvider(api))

        val postId = repository.addReply(
            parentPostId = 12,
            subject = "Re: Duda",
            message = "Gracias",
        )

        assertEquals(44, postId)
        assertEquals("mod_forum_add_discussion_post", api.lastFunction)
        assertEquals(
            mapOf(
                "postid" to "12",
                "subject" to "Re: Duda",
                "message" to "Gracias",
            ),
            api.lastParams,
        )
    }

    private class FakeSessionApiProvider(
        private val api: MoodleApiClient,
    ) : SessionApiProvider {
        override fun requireApi(): MoodleApiClient = api
    }

    private class RecordingApiClient(
        private val readResponse: String = "{}",
        private val readError: Throwable? = null,
        private val writeResponse: String = "{}",
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
            readError?.let { error -> throw error }
            return readResponse
        }

        override suspend fun callRESTWithFormBody(
            function: String,
            params: Map<String, String>,
        ): String {
            lastFunction = function
            lastParams = params
            return writeResponse
        }
    }

    private companion object {
        const val DISCUSSIONS_JSON = """
            {
              "discussions": [
                {
                  "discussion": 11, "name": "Duda del tema 3",
                  "message": "<p>Hola</p>", "userfullname": "Ana",
                  "created": 1700000000, "numreplies": 2
                }
              ]
            }
        """

        const val POSTS_JSON = """
            {
              "posts": [
                {
                  "id": 12, "subject": "Duda del tema 3", "message": "<p>Hola</p>",
                  "author": {"fullname": "Ana"}, "timecreated": 1700000000, "parentid": 0
                },
                {
                  "id": 13, "subject": "Re: Duda del tema 3", "message": "<p>Mira la página 4</p>",
                  "author": {"fullname": "Luis"}, "timecreated": 1700000100, "parentid": 12
                }
              ]
            }
        """
    }
}
