package onion.gh0st.iMoodle.ui.courses.detail

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseContents
import onion.gh0st.iMoodle.domain.usecase.course.GetUrlExternalUrl
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [CourseDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeCourseRepository()

    private fun createViewModel(courseId: Int = 42): CourseDetailViewModel {
        val savedState = SavedStateHandle(mapOf("COURSE_ID" to courseId))
        return CourseDetailViewModel(
            savedStateHandle = savedState,
            getCourseContents = GetCourseContents(fakeRepo),
            getUrlExternalUrl = GetUrlExternalUrl(fakeRepo),
        )
    }

    @Test
    fun `init loads sections and items`() = runTest {
        fakeRepo.courseContents = listOf(
            section(
                id = 1,
                name = "Tema 1",
                module(id = 1, modname = "assign"),
                module(id = 2, modname = "forum"),
            ),
        )

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(listOf("Tema 1"), sections)
            assertTrue(items.any { it is CourseDetailItem.SectionHeader })
            assertEquals(2, items.count { it is CourseDetailItem.Module })
        }
    }

    @Test
    fun `label modules become Label items`() = runTest {
        fakeRepo.courseContents = listOf(
            section(
                id = 1,
                name = "Tema 1",
                module(id = 1, modname = "label", description = "<p>Hola</p>"),
            ),
        )

        val vm = createViewModel()

        val labels = vm.state.value.items.filterIsInstance<CourseDetailItem.Label>()
        assertEquals(1, labels.size)
        assertEquals("<p>Hola</p>", labels[0].content)
    }

    @Test
    fun `module items expose their ModuleType`() = runTest {
        fakeRepo.courseContents = listOf(
            section(
                id = 1,
                name = "Tema 1",
                module(id = 1, modname = "assign"),
                module(id = 2, modname = "forum"),
            ),
        )

        val vm = createViewModel()

        val modules = vm.state.value.items.filterIsInstance<CourseDetailItem.Module>()
        assertTrue(modules[0].type is ModuleType.Assignment)
        assertTrue(modules[1].type is ModuleType.Forum)
    }

    @Test
    fun `setSection filters items`() = runTest {
        fakeRepo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 1, modname = "assign")),
            section(id = 2, name = "Tema 2", module(id = 2, modname = "forum")),
        )
        val vm = createViewModel()

        vm.setSection("Tema 2")

        val headers = vm.state.value.items.filterIsInstance<CourseDetailItem.SectionHeader>()
        assertTrue(headers.isEmpty())

        val modules = vm.state.value.items.filterIsInstance<CourseDetailItem.Module>()
        assertEquals(1, modules.size)
        assertEquals("forum", modules[0].type.module.modname)
    }

    @Test
    fun `setSection null restores all items`() = runTest {
        fakeRepo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 1, modname = "assign")),
            section(id = 2, name = "Tema 2", module(id = 2, modname = "forum")),
        )
        val vm = createViewModel()

        vm.setSection("Tema 2")
        vm.setSection(null)

        val headers = vm.state.value.items.filterIsInstance<CourseDetailItem.SectionHeader>()
        assertEquals(2, headers.size)
    }

    @Test
    fun `load failure stops loading`() = runTest {
        fakeRepo.contentsError = RuntimeException("boom")

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
    }

    private fun section(
        id: Int,
        name: String,
        vararg modules: CourseModule,
    ) = CourseContent(id = id, name = name, modules = modules.toList())

    private fun module(
        id: Int,
        modname: String,
        description: String? = null,
    ) = CourseModule(
        id = id,
        instance = id,
        name = "Módulo $id",
        modname = modname,
        url = null,
        description = description,
        files = emptyList(),
    )
}