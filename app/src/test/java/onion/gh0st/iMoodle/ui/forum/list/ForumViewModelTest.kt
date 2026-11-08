package onion.gh0st.iMoodle.ui.forum.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.usecase.forum.GetForumDiscussions
import onion.gh0st.iMoodle.fakes.FakeForumRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [ForumViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeForumRepository()

    private fun createViewModel(forumId: Int = 1): ForumViewModel {
        val savedState = SavedStateHandle(mapOf("FORUM_ID" to forumId))
        return ForumViewModel(
            savedStateHandle = savedState,
            getForumDiscussions = GetForumDiscussions(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes discussions`() = runTest {
        fakeRepo.discussions = listOf(discussion(1, "Duda A"))

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(1, discussions.size)
            assertEquals("Duda A", discussions[0].name)
        }
    }

    @Test
    fun `load error emits error event`() = runTest {
        fakeRepo.discussionsError = RuntimeException("boom")

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertTrue(discussions.isEmpty())
        }
        vm.events.test {
            val event = awaitItem() as ForumUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun discussion(id: Int, name: String) = ForumDiscussion(
        id = id,
        name = name,
        message = "",
        author = "Autor",
        created = 0L,
        numReplies = 0,
    )
}