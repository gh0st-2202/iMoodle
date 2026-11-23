package onion.gh0st.iMoodle.ui.forum.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.domain.usecase.forum.GetForumDiscussions
import onion.gh0st.iMoodle.domain.usecase.forum.ManageForumPosts
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
            manageForumPosts = ManageForumPosts(fakeRepo),
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

    @Test
    fun `permission to create discussions comes from Moodle`() = runTest {
        fakeRepo.canCreateDiscussion = false

        val vm = createViewModel()

        assertFalse(vm.state.value.canCreateDiscussion)
    }

    @Test
    fun `createDiscussion sends the post and reloads`() = runTest {
        fakeRepo.discussions = listOf(discussion(1, "Duda A"))
        val vm = createViewModel()

        vm.events.test {
            vm.createDiscussion("Duda B", "¿Cómo se hace?")
            assertEquals(ForumUiEvent.DiscussionCreated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Triple(1, "Duda B", "¿Cómo se hace?"), fakeRepo.lastDiscussion)
        assertFalse(vm.state.value.isPosting)
    }

    @Test
    fun `createDiscussion with blank fields does nothing`() = runTest {
        val vm = createViewModel()

        vm.createDiscussion(" ", "")

        assertEquals(null, fakeRepo.lastDiscussion)
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
