package onion.gh0st.iMoodle.domain.usecase.wiki

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.fakes.FakeWikiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [GetWikiPages].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetWikiPagesTest {

    private val fakeRepo = FakeWikiRepository()

    @Test
    fun `returns the pages of the repository`() = runTest {
        fakeRepo.pages = listOf(
            WikiPage(id = 1, title = "Portada", isFirstPage = true),
            WikiPage(id = 2, title = "Guía"),
        )

        val result = GetWikiPages(fakeRepo)(wikiId = 4)

        assertEquals(listOf("Portada", "Guía"), result.getOrThrow().map { it.title })
    }

    @Test
    fun `propagates the repository failure`() = runTest {
        fakeRepo.pagesError = IllegalStateException("wiki no encontrada")

        val result = GetWikiPages(fakeRepo)(wikiId = 4)

        assertTrue(result.isFailure)
        assertEquals("wiki no encontrada", result.exceptionOrNull()?.message)
    }
}
