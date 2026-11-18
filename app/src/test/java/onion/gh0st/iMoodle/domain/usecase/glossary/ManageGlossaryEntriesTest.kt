package onion.gh0st.iMoodle.domain.usecase.glossary

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.fakes.FakeGlossaryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ManageGlossaryEntries].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageGlossaryEntriesTest {

    private val fakeRepo = FakeGlossaryRepository()

    private val manage = ManageGlossaryEntries(fakeRepo)

    @Test
    fun `add forwards the entry to the repository`() = runTest {
        val result = manage.add(glossaryId = 3, concept = "Variable", definition = "<p>Valor</p>")

        assertTrue(result.isSuccess)
        assertEquals(
            FakeGlossaryRepository.AddedEntry(3, "Variable", "<p>Valor</p>"),
            fakeRepo.lastAdd,
        )
    }

    @Test
    fun `update forwards the entry to the repository`() = runTest {
        val result = manage.update(entryId = 9, concept = "Bucle", definition = "Repetición")

        assertTrue(result.isSuccess)
        assertEquals(
            FakeGlossaryRepository.UpdatedEntry(9, "Bucle", "Repetición"),
            fakeRepo.lastUpdate,
        )
    }

    @Test
    fun `delete forwards the id to the repository`() = runTest {
        val result = manage.delete(entryId = 11)

        assertTrue(result.isSuccess)
        assertEquals(11, fakeRepo.lastDeletedId)
    }

    @Test
    fun `write failures are returned without throwing`() = runTest {
        fakeRepo.writeError = IllegalStateException("No puedes editar esta entrada")

        val addResult = manage.add(glossaryId = 3, concept = "Variable", definition = "Valor")
        val updateResult = manage.update(entryId = 9, concept = "Bucle", definition = "Repetición")
        val deleteResult = manage.delete(entryId = 11)

        assertEquals("No puedes editar esta entrada", addResult.exceptionOrNull()?.message)
        assertEquals("No puedes editar esta entrada", updateResult.exceptionOrNull()?.message)
        assertEquals("No puedes editar esta entrada", deleteResult.exceptionOrNull()?.message)
    }
}
