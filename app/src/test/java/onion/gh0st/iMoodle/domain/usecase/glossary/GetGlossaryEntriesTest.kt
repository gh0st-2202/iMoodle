package onion.gh0st.iMoodle.domain.usecase.glossary

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.fakes.FakeGlossaryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [GetGlossaryEntries].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetGlossaryEntriesTest {

    private val fakeRepo = FakeGlossaryRepository()

    @Test
    fun `returns the entries of the repository`() = runTest {
        fakeRepo.entries = listOf(entry(1, "Variable"), entry(2, "Función"))

        val result = GetGlossaryEntries(fakeRepo)(glossaryId = 7)

        assertEquals(listOf("Variable", "Función"), result.getOrThrow().map { it.concept })
    }

    @Test
    fun `propagates the repository failure`() = runTest {
        fakeRepo.entriesError = IllegalStateException("sin permiso")

        val result = GetGlossaryEntries(fakeRepo)(glossaryId = 7)

        assertTrue(result.isFailure)
        assertEquals("sin permiso", result.exceptionOrNull()?.message)
    }

    private fun entry(id: Int, concept: String) = GlossaryEntry(
        id = id,
        concept = concept,
        definition = "Definición de $concept",
        author = "Autor",
        createdAt = 0L,
        updatedAt = 0L,
        approved = true,
    )
}
