package onion.gh0st.iMoodle.ui.forum.discussion

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.domain.usecase.forum.GetDiscussionPosts
import onion.gh0st.iMoodle.fakes.FakeForumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [DiscussionViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeForumRepository()

    private fun createViewModel(discussionId: Int = 10): DiscussionViewModel {
        val savedState = SavedStateHandle(mapOf("DISCUSSION_ID" to discussionId))
        return DiscussionViewModel(
            savedStateHandle = savedState,
            getDiscussionPosts = GetDiscussionPosts(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes posts`() = runTest {
        fakeRepo.posts = listOf(post(1, "Hola"))

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(1, posts.size)
        }
    }

    @Test
    fun `load error emits error event`() = runTest {
        fakeRepo.postsError = RuntimeException("boom")

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertTrue(posts.isEmpty())
        }
        vm.events.test {
            val event = awaitItem() as DiscussionUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun post(id: Int, subject: String) = ForumPost(
        id = id,
        subject = subject,
        message = "",
        author = "Autor",
        created = 0L,
        isReply = false,
    )
}