package onion.gh0st.iMoodle.domain.usecase.forum

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.fakes.FakeForumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ManageForumPosts].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageForumPostsTest {

    private val repo = FakeForumRepository()
    private val useCase = ManageForumPosts(repo)

    @Test
    fun `canAddDiscussion asks the repository`() = runTest {
        repo.canCreateDiscussion = false

        val allowed = useCase.canAddDiscussion(forumId = 4)

        assertTrue(!allowed)
    }

    @Test
    fun `addDiscussion trims the subject and the message`() = runTest {
        useCase.addDiscussion(forumId = 4, subject = "  Duda  ", message = "  Texto  ")

        assertEquals(Triple(4, "Duda", "Texto"), repo.lastDiscussion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addDiscussion rejects a blank subject`() = runTest {
        useCase.addDiscussion(forumId = 4, subject = " ", message = "Texto")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addDiscussion rejects a blank message`() = runTest {
        useCase.addDiscussion(forumId = 4, subject = "Duda", message = "")
    }

    @Test
    fun `addReply answers the given post`() = runTest {
        useCase.addReply(parentPostId = 9, subject = "Re: Duda", message = "Gracias")

        assertEquals(Triple(9, "Re: Duda", "Gracias"), repo.lastReply)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addReply rejects a blank message`() = runTest {
        useCase.addReply(parentPostId = 9, subject = "Re: Duda", message = " ")
    }
}
