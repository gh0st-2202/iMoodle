package onion.gh0st.iMoodle.ui.assignments

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.usecase.assignment.GetAssignmentDetail
import onion.gh0st.iMoodle.fakes.FakeAssignmentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [AssignmentDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssignmentDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeAssignmentRepository()

    private fun createViewModel(
        courseId: Int = 1,
        assignmentId: Int = 42,
    ): AssignmentDetailViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "COURSE_ID" to courseId,
                "ASSIGNMENT_ID" to assignmentId,
            ),
        )
        return AssignmentDetailViewModel(
            savedStateHandle = savedState,
            getAssignmentDetail = GetAssignmentDetail(fakeRepo),
        )
    }

    @Test
    fun `successful load publishes assignment`() = runTest {
        fakeRepo.assignmentDetail = detail(id = 42)

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(42, assignment?.id)
        }
    }

    @Test
    fun `missing assignment exposes error event`() = runTest {
        fakeRepo.assignmentDetail = null

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertNull(assignment)
        }
        vm.events.test {
            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            assertNull(event.arg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network error exposes error event`() = runTest {
        fakeRepo.detailError = RuntimeException("network boom")

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertNull(assignment)
        }
        vm.events.test {
            val event = awaitItem() as AssignmentDetailUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun detail(id: Int) = AssignmentDetail(
        id = id,
        name = "Tarea $id",
        description = "",
        dueDate = null,
        allowSubmissionsFromDate = null,
        cutoffDate = null,
        maxGrade = 10.0,
        submissionStatus = null,
        submissionGrade = null,
        gradeDisplay = null,
        submissionGraded = false,
        canSubmit = true,
        lastAttemptDate = null,
        files = emptyList(),
    )
}