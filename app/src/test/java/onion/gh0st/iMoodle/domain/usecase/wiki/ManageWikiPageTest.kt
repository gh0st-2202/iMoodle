package onion.gh0st.iMoodle.domain.usecase.wiki

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.fakes.FakeWikiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ManageWikiPage].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageWikiPageTest {

    private val fakeRepo = FakeWikiRepository()

    private val manage = ManageWikiPage(fakeRepo)

    @Test
    fun `getDraft returns the original content`() = runTest {
        fakeRepo.draft = WikiPageDraft(pageId = 8, content = "<p>Original</p>")

        val draft = manage.getDraft(pageId = 8).getOrThrow()

        assertEquals("<p>Original</p>", draft.content)
    }

    @Test
    fun `update forwards the content untouched`() = runTest {
        val result = manage.update(pageId = 8, content = "<p>Editado</p>")

        assertTrue(result.isSuccess)
        assertEquals(8, fakeRepo.lastUpdatedPageId)
        assertEquals("<p>Editado</p>", fakeRepo.lastUpdatedContent)
    }

    @Test
    fun `create returns the id of the new page`() = runTest {
        fakeRepo.createdPageId = 21

        val result = manage.create(wikiId = 2, title = "Nueva", content = "Hola")

        assertEquals(21, result.getOrThrow())
        assertEquals(
            FakeWikiRepository.CreatedPage(2, "Nueva", "Hola"),
            fakeRepo.lastCreate,
        )
    }

    @Test
    fun `write failures are returned without throwing`() = runTest {
        fakeRepo.writeError = IllegalStateException("No tienes permiso para editar esta página")

        val updateResult = manage.update(pageId = 8, content = "Editado")
        val createResult = manage.create(wikiId = 2, title = "Nueva", content = "Hola")

        assertEquals(
            "No tienes permiso para editar esta página",
            updateResult.exceptionOrNull()?.message,
        )
        assertEquals(
            "No tienes permiso para editar esta página",
            createResult.exceptionOrNull()?.message,
        )
    }
}
