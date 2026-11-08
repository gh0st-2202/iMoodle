package onion.gh0st.iMoodle.domain.usecase.forum

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.fakes.FakeForumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios de [GetForumDiscussions].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetForumDiscussionsTest {

    private val repo = FakeForumRepository()
    private val useCase = GetForumDiscussions(repo)

    /**
     * Con discusiones en el repo, devuelve la lista tal cual.
     */
    @Test
    fun `returns discussions from repository`() = runTest {
        repo.discussions = listOf(discussion(1, "Duda A"), discussion(2, "Duda B"))

        val result = useCase(forumId = 1)

        assertEquals(2, result.size)
        assertEquals("Duda A", result[0].name)
    }

    /**
     * Sin discusiones, devuelve lista vacía.
     */
    @Test
    fun `returns empty list when no discussions`() = runTest {
        repo.discussions = emptyList()

        assertTrue(useCase(forumId = 1).isEmpty())
    }

    /**
     * Un error del repositorio se propaga.
     */
    @Test
    fun `repository error propagates`() = runTest {
        repo.discussionsError = RuntimeException("boom")

        val exception = runCatching { useCase(forumId = 1) }.exceptionOrNull()

        assertTrue(exception is RuntimeException)
        assertEquals("boom", exception?.message)
    }

    // --- Helpers ---

    private fun discussion(id: Int, name: String) = ForumDiscussion(
        id = id,
        name = name,
        message = "",
        author = "Autor",
        created = 0L,
        numReplies = 0,
    )
}