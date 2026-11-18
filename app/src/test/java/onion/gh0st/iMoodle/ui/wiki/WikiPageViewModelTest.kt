package onion.gh0st.iMoodle.ui.wiki

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.domain.usecase.wiki.GetWikiPage
import onion.gh0st.iMoodle.domain.usecase.wiki.ManageWikiPage
import onion.gh0st.iMoodle.fakes.FakeWikiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [WikiPageViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WikiPageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeWikiRepository()

    private fun createViewModel(pageId: Int = 5): WikiPageViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "WIKI_PAGE_ID" to pageId,
                "WIKI_PAGE_TITLE" to "Portada",
            ),
        )
        return WikiPageViewModel(
            savedStateHandle = savedState,
            getWikiPage = GetWikiPage(fakeRepo),
            manageWikiPage = ManageWikiPage(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes the page`() = runTest {
        fakeRepo.page = WikiPage(
            id = 5,
            title = "Portada",
            content = "<p>Hola</p>",
            modifiedAt = 1_700_000_000L,
            canEdit = true,
        )

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertNotNull(page)
            assertEquals("Portada", page?.title)
            assertTrue(page?.canEdit == true)
        }
    }

    @Test
    fun `load error emits a generic error event`() = runTest {
        fakeRepo.pageError = RuntimeException("boom")

        val viewModel = createViewModel()

        viewModel.events.test {
            val event = awaitItem() as WikiPageUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestDraft publishes the original content`() = runTest {
        fakeRepo.page = WikiPage(id = 5, title = "Portada")
        fakeRepo.draft = WikiPageDraft(pageId = 5, content = "<p>Original</p>")
        val viewModel = createViewModel()

        viewModel.requestDraft()

        with(viewModel.state.value) {
            assertFalse(isLoadingDraft)
            assertEquals("<p>Original</p>", draft?.content)
        }
    }

    @Test
    fun `draft error is shown with the reason given by moodle`() = runTest {
        fakeRepo.page = WikiPage(id = 5, title = "Portada")
        fakeRepo.draftError = IllegalStateException("La página está bloqueada")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.requestDraft()

            val event = awaitItem() as WikiPageUiEvent.ShowError
            assertEquals(R.string.error_wiki_operation, event.messageResId)
            assertEquals("La página está bloqueada", event.arg)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun `savePage sends the content and emits the success event`() = runTest {
        fakeRepo.page = WikiPage(id = 5, title = "Portada")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.savePage("<p>Editado</p>")

            assertEquals(WikiPageUiEvent.PageSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5, fakeRepo.lastUpdatedPageId)
        assertEquals("<p>Editado</p>", fakeRepo.lastUpdatedContent)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `discardDraft clears the draft so the next edit reloads it`() = runTest {
        fakeRepo.page = WikiPage(id = 5, title = "Portada")
        fakeRepo.draft = WikiPageDraft(pageId = 5, content = "Original")
        val viewModel = createViewModel()

        viewModel.requestDraft()
        viewModel.discardDraft()

        assertNull(viewModel.state.value.draft)
    }
}
