package onion.gh0st.iMoodle.ui.pasengrades

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade
import onion.gh0st.iMoodle.domain.usecase.pasen.GetPasenGrades
import onion.gh0st.iMoodle.fakes.FakePasenGradeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [PasenGradesViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasenGradesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePasenGradeRepository()

    private fun createViewModel(): PasenGradesViewModel =
        PasenGradesViewModel(GetPasenGrades(repo))

    @Test
    fun `successful load publishes convocatorias and selects the active one`() = runTest {
        repo.grades = listOf(
            PasenConvocatoria("1ª Evaluación", true, "", "", false, 1, emptyList()),
            PasenConvocatoria("2ª Evaluación", false, "", "", true, 2, emptyList()),
        )

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals(2, vm.state.value.convocatorias.size)
        assertEquals("2ª Evaluación", vm.state.value.selectedConvocatoria)
    }

    @Test
    fun `setConvocatoria changes the selection`() = runTest {
        repo.grades = listOf(
            PasenConvocatoria("1ª Evaluación", true, "", "", false, 1, emptyList()),
            PasenConvocatoria("2ª Evaluación", false, "", "", true, 2, emptyList()),
        )
        val vm = createViewModel()

        vm.setConvocatoria("1ª Evaluación")

        assertEquals("1ª Evaluación", vm.state.value.selectedConvocatoria)
    }

    @Test
    fun `load error emits an error event`() = runTest {
        repo.gradeError = RuntimeException("boom")

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.hasError)
        vm.events.test {
            val event = awaitItem() as PasenGradesUiEvent.ShowError
            assertEquals(R.string.error_pasen_grades_load, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh keeps the selected convocatoria when it still exists`() = runTest {
        repo.grades = listOf(
            PasenConvocatoria("1ª Evaluación", true, "", "", true, 1, emptyList()),
            PasenConvocatoria("2ª Evaluación", false, "", "", false, 2, emptyList()),
        )
        val vm = createViewModel()
        vm.setConvocatoria("2ª Evaluación")

        vm.refresh()

        assertEquals("2ª Evaluación", vm.state.value.selectedConvocatoria)
    }

    @Test
    fun `refresh falls back to the active convocatoria when the selection disappears`() = runTest {
        repo.grades = listOf(
            PasenConvocatoria("1ª Evaluación", true, "", "", true, 1, emptyList()),
        )
        val vm = createViewModel()
        vm.setConvocatoria("1ª Evaluación")
        repo.grades = listOf(
            PasenConvocatoria("2ª Evaluación", false, "", "", true, 2, emptyList()),
        )

        vm.refresh()

        assertEquals("2ª Evaluación", vm.state.value.selectedConvocatoria)
    }

    @Test
    fun `subjects of the selected convocatoria are exposed`() = runTest {
        val subject = PasenSubjectGrade("Acceso a datos", "8,5", null, null, emptyList())
        repo.grades = listOf(
            PasenConvocatoria("1ª Evaluación", true, "", "", true, 1, listOf(subject)),
        )

        val vm = createViewModel()

        assertEquals(listOf(subject), vm.state.value.subjects)
    }
}
