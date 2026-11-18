package onion.gh0st.iMoodle.ui.glossary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.domain.usecase.glossary.GetGlossaryEntries
import onion.gh0st.iMoodle.domain.usecase.glossary.ManageGlossaryEntries
import onion.gh0st.iMoodle.fakes.FakeGlossaryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Tests de [GlossaryViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlossaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeGlossaryRepository()

    private fun createViewModel(glossaryId: Int = 3): GlossaryViewModel {
        val savedState = SavedStateHandle(mapOf("GLOSSARY_ID" to glossaryId))
        return GlossaryViewModel(
            savedStateHandle = savedState,
            getGlossaryEntries = GetGlossaryEntries(fakeRepo),
            manageGlossaryEntries = ManageGlossaryEntries(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes the entries`() = runTest {
        fakeRepo.entries = listOf(entry(1, "Variable"))

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertFalse(isSaving)
            assertEquals(listOf("Variable"), entries.map { it.concept })
        }
    }

    @Test
    fun `load error emits a generic error event`() = runTest {
        fakeRepo.entriesError = RuntimeException("boom")

        val viewModel = createViewModel()

        assertTrue(viewModel.state.value.entries.isEmpty())
        viewModel.events.test {
            val event = awaitItem() as GlossaryUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network error on load emits the network message`() = runTest {
        fakeRepo.entriesError = AuthException.Network(IOException("sin red"))

        val viewModel = createViewModel()

        viewModel.events.test {
            val event = awaitItem() as GlossaryUiEvent.ShowError
            assertEquals(R.string.error_network, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveEntry with id 0 creates the entry and reloads`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.saveEntry(entryId = 0, concept = "  Bucle  ", definition = "<p>Repetición</p>")

            assertEquals(GlossaryUiEvent.EntrySaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            FakeGlossaryRepository.AddedEntry(3, "Bucle", "<p>Repetición</p>"),
            fakeRepo.lastAdd,
        )
        assertNull(fakeRepo.lastUpdate)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `saveEntry with id updates the entry and reloads`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.saveEntry(entryId = 9, concept = "Bucle", definition = "Repetición")

            assertEquals(GlossaryUiEvent.EntrySaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            FakeGlossaryRepository.UpdatedEntry(9, "Bucle", "Repetición"),
            fakeRepo.lastUpdate,
        )
        assertNull(fakeRepo.lastAdd)
    }

    @Test
    fun `deleteEntry removes the entry and emits the event`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.deleteEntry(entryId = 12)

            assertEquals(GlossaryUiEvent.EntryDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(12, fakeRepo.lastDeletedId)
    }

    @Test
    fun `moodle rejection is shown with its reason`() = runTest {
        fakeRepo.writeError = IllegalStateException("No puedes editar esta entrada")
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.saveEntry(entryId = 9, concept = "Bucle", definition = "Repetición")

            val event = awaitItem() as GlossaryUiEvent.ShowError
            assertEquals(R.string.error_glossary_operation, event.messageResId)
            assertEquals("No puedes editar esta entrada", event.arg)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.state.value.isSaving)
    }

    private fun entry(id: Int, concept: String) = GlossaryEntry(
        id = id,
        concept = concept,
        definition = "Definición",
        author = "Autor",
        createdAt = 0L,
        updatedAt = 0L,
        approved = true,
    )
}
