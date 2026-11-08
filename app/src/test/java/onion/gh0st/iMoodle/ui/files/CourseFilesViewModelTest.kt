package onion.gh0st.iMoodle.ui.files

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseContents
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseFiles
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [CourseFilesViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseFilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeCourseRepository()

    private fun createViewModel(
        courseId: Int = 1,
        moduleId: Int = 100,
    ): CourseFilesViewModel {
        val savedState = SavedStateHandle(
            mapOf(
                "COURSE_ID" to courseId,
                "MODULE_ID" to moduleId,
            ),
        )
        return CourseFilesViewModel(
            savedStateHandle = savedState,
            getCourseFiles = GetCourseFiles(GetCourseContents(fakeRepo)),
        )
    }

    @Test
    fun `init loads files of the module`() = runTest {
        fakeRepo.courseContents = listOf(
            section(
                id = 1,
                name = "Tema 1",
                module(id = 100, files = listOf(file("a.pdf"), file("b.pdf"))),
            ),
        )

        val vm = createViewModel(courseId = 1, moduleId = 100)

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(2, files.size)
        }
    }

    @Test
    fun `load failure emits error event`() = runTest {
        fakeRepo.contentsError = RuntimeException("boom")

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertTrue(files.isEmpty())
        }
        vm.events.test {
            val event = awaitItem() as CourseFilesUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown module leaves files empty without error`() = runTest {
        fakeRepo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 100, files = listOf(file("a.pdf")))),
        )

        val vm = createViewModel(courseId = 1, moduleId = 999)

        with(vm.state.value) {
            assertFalse(isLoading)
            assertTrue(files.isEmpty())
        }
    }

    private fun section(id: Int, name: String, vararg modules: CourseModule) =
        CourseContent(id = id, name = name, modules = modules.toList())

    private fun module(id: Int, files: List<CourseFile>) = CourseModule(
        id = id,
        instance = id,
        name = "Módulo $id",
        modname = "resource",
        url = null,
        description = null,
        files = files,
    )

    private fun file(name: String) = CourseFile(
        fileName = name,
        fileUrl = "https://example.com/$name?token=x",
        fileSize = 1024,
        mimeType = "application/pdf",
        timeModified = 0,
    )
}