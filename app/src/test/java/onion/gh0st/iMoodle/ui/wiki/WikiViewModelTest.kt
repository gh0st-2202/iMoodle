package onion.gh0st.iMoodle.ui.wiki

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.usecase.wiki.GetWikiPages
import onion.gh0st.iMoodle.domain.usecase.wiki.ManageWikiPage
import onion.gh0st.iMoodle.fakes.FakeWikiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Tests de [WikiViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WikiViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeWikiRepository()

    private fun createViewModel(wikiId: Int = 4): WikiViewModel {
        val savedState = SavedStateHandle(mapOf("WIKI_ID" to wikiId))
        return WikiViewModel(
            savedStateHandle = savedState,
            getWikiPages = GetWikiPages(fakeRepo),
            manageWikiPage = ManageWikiPage(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes the pages`() = runTest {
        fakeRepo.pages = listOf(WikiPage(id = 1, title = "Portada", isFirstPage = true))

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertFalse(isCreating)
            assertEquals(listOf("Portada"), pages.map { it.title })
        }
    }

    @Test
    fun `load error emits a generic error event`() = runTest {
        fakeRepo.pagesError = RuntimeException("boom")

        val viewModel = createViewModel()

        viewModel.events.test {
            val event = awaitItem() as WikiUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createPage forwards the page and emits its id`() = runTest {
        fakeRepo.createdPageId = 21
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.createPage(title = "  Nueva  ", content = "Hola")

            assertEquals(WikiUiEvent.PageCreated(21), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(FakeWikiRepository.CreatedPage(4, "Nueva", "Hola"), fakeRepo.lastCreate)
        assertFalse(viewModel.state.value.isCreating)
    }

    @Test
    fun `moodle rejection while creating is shown with its reason`() = runTest {
        fakeRepo.writeError = IllegalStateException("No tienes permiso para crear páginas")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.createPage(title = "Nueva", content = "Hola")

            val event = awaitItem() as WikiUiEvent.ShowError
            assertEquals(R.string.error_wiki_operation, event.messageResId)
            assertEquals("No tienes permiso para crear páginas", event.arg)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.state.value.isCreating)
    }

    @Test
    fun `network error while creating is shown as network error`() = runTest {
        fakeRepo.writeError = AuthException.Network(IOException("sin red"))
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.createPage(title = "Nueva", content = "Hola")

            val event = awaitItem() as WikiUiEvent.ShowError
            assertEquals(R.string.error_network, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
