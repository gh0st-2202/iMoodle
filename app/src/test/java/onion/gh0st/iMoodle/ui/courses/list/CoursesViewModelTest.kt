package onion.gh0st.iMoodle.ui.courses.list

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [CoursesViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepo = FakeSessionRepository(TestSessions.active())
    private val fakeRepo = FakeCoursesRepository()

    @Test
    fun `empty flow emits loaded state with empty list`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())

        val vm = CoursesViewModel(fakeRepo, sessionRepo)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertFalse(isLoading)
            assertEquals(emptyList<CourseUi>(), courses)
        }
    }

    @Test
    fun `flow emission publishes courses`() = runTest {
        fakeRepo.coursesFlow = flowOf(
            listOf(course(2, "Zebra"), course(1, "Apple")),
        )

        val vm = CoursesViewModel(fakeRepo, sessionRepo)
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertFalse(isLoading)
            assertEquals(listOf(1, 2), courses.map { it.id })
            assertEquals(listOf("Apple", "Zebra"), courses.map { it.name })
        }
    }

    @Test
    fun `init triggers refresh`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())

        CoursesViewModel(fakeRepo, sessionRepo)
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCalls)
    }

    @Test
    fun `refresh triggers another sync`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())
        val vm = CoursesViewModel(fakeRepo, sessionRepo)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.refreshCalls)
    }

    @Test
    fun `missing user id emits error event instead of querying user 0`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())
        val noUser = FakeSessionRepository(TestSessions.active(user = null))

        val vm = CoursesViewModel(fakeRepo, noUser)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(0, fakeRepo.refreshCalls)
        vm.events.test {
            val event = awaitItem() as CoursesUiEvent.ShowError
            assertEquals(R.string.error_identify_user, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh without user emits error event`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())
        val noUser = FakeSessionRepository(TestSessions.active(user = null))
        val vm = CoursesViewModel(fakeRepo, noUser)
        advanceUntilIdle()

        vm.events.test {
            val initEvent = awaitItem() as CoursesUiEvent.ShowError
            assertEquals(R.string.error_identify_user, initEvent.messageResId)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            vm.refresh()
            val refreshEvent = awaitItem() as CoursesUiEvent.ShowError
            assertEquals(R.string.error_identify_user, refreshEvent.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun course(id: Int, name: String) = Course(
        id = id,
        fullname = name,
        shortname = name.lowercase(),
        summary = "",
        progress = null,
        startDate = null,
        endDate = null,
        imageUrl = null,
    )

    private class FakeCoursesRepository : CourseRepository {
        var coursesFlow: Flow<List<Course>> = flowOf(emptyList())
        var refreshCalls: Int = 0
            private set

        override fun getCoursesFlow(userId: Int): Flow<List<Course>> = coursesFlow

        override suspend fun refreshCourses(userId: Int) {
            refreshCalls++
        }

        override suspend fun getCourseContents(
            courseId: Int,
            forceRefresh: Boolean,
        ): List<CourseContent> = emptyList()

        override suspend fun getUrlExternalUrl(
            courseId: Int,
            instanceId: Int,
            forceRefresh: Boolean,
        ): String? = null
    }
}