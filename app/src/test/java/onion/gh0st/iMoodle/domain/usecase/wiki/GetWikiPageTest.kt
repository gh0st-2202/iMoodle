package onion.gh0st.iMoodle.domain.usecase.wiki

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.fakes.FakeWikiRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [GetWikiPage].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetWikiPageTest {

    private val fakeRepo = FakeWikiRepository()

    @Test
    fun `returns the page with its permissions`() = runTest {
        fakeRepo.page = WikiPage(id = 5, title = "Portada", content = "<p>Hola</p>", canEdit = true)

        val page = GetWikiPage(fakeRepo)(pageId = 5).getOrThrow()

        assertTrue(page.canEdit)
        assertTrue(page.isFirstPage.not())
    }

    @Test
    fun `propagates the repository failure`() = runTest {
        fakeRepo.pageError = IllegalStateException("Moodle no devolvió la página solicitada")

        val result = GetWikiPage(fakeRepo)(pageId = 5)

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }
}
