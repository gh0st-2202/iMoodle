package onion.gh0st.iMoodle.domain.usecase.forum

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.fakes.FakeForumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios de [GetDiscussionPosts].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetDiscussionPostsTest {

    private val repo = FakeForumRepository()
    private val useCase = GetDiscussionPosts(repo)

    /**
     * Con posts en el repo, devuelve la lista tal cual.
     */
    @Test
    fun `returns posts from repository`() = runTest {
        repo.posts = listOf(post(1, "Hola"), post(2, "Adiós"))

        val result = useCase(discussionId = 10)

        assertEquals(2, result.size)
        assertEquals("Hola", result[0].subject)
    }

    /**
     * Sin posts, devuelve lista vacía.
     */
    @Test
    fun `returns empty list when no posts`() = runTest {
        repo.posts = emptyList()

        assertTrue(useCase(discussionId = 10).isEmpty())
    }

    /**
     * Un error del repositorio se propaga.
     */
    @Test
    fun `repository error propagates`() = runTest {
        repo.postsError = RuntimeException("boom")

        val exception = runCatching { useCase(discussionId = 10) }.exceptionOrNull()

        assertTrue(exception is RuntimeException)
        assertEquals("boom", exception?.message)
    }

    // --- Helpers ---

    private fun post(id: Int, subject: String) = ForumPost(
        id = id,
        subject = subject,
        message = "",
        author = "Autor",
        created = 0L,
        isReply = false,
    )
}