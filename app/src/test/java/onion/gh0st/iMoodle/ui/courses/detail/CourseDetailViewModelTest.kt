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
import onion.gh0st.iMoodle.domain.usecase.course.ManageModuleCompletion
import onion.gh0st.iMoodle.domain.usecase.file.DownloadCourseFile
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.fakes.FakeCompletionRepository
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import onion.gh0st.iMoodle.fakes.FakeFileDownloader
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
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
    private val completionRepo = FakeCompletionRepository()
    private val observeSession = ObserveSession(FakeSessionRepository(TestSessions.active()))
    private val fileDownloader = FakeFileDownloader()

    private fun createViewModel(courseId: Int = 42): CourseDetailViewModel {
        val savedState = SavedStateHandle(mapOf("COURSE_ID" to courseId))
        return CourseDetailViewModel(
            savedStateHandle = savedState,
            getCourseContents = GetCourseContents(fakeRepo),
            getUrlExternalUrl = GetUrlExternalUrl(fakeRepo),
            downloadCourseFile = DownloadCourseFile(observeSession, fileDownloader),
            manageModuleCompletion = ManageModuleCompletion(completionRepo),
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
        assertEquals(1, headers.size)
        assertEquals("Tema 2", headers.single().title)

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

    @Test
    fun `markModuleViewed sends the module`() = runTest {
        val vm = createViewModel()

        vm.markModuleViewed(module(id = 5, modname = "assign"))

        assertEquals(5, completionRepo.lastViewedModule?.id)
    }

    @Test
    fun `toggleCompletion marks a pending module as complete`() = runTest {
        fakeRepo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 3, modname = "assign", completionMode = 1)),
        )
        val vm = createViewModel()
        val pendingModule = vm.currentModule(id = 3)

        vm.toggleCompletion(pendingModule)

        assertEquals(3 to true, completionRepo.lastCompletionChange)
        assertTrue(vm.currentModule(id = 3).isCompleted)
    }

    @Test
    fun `toggleCompletion failure keeps the module pending`() = runTest {
        completionRepo.completionResult = Result.failure(RuntimeException("boom"))
        fakeRepo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 3, modname = "assign", completionMode = 1)),
        )
        val vm = createViewModel()
        val pendingModule = vm.currentModule(id = 3)

        vm.toggleCompletion(pendingModule)

        assertFalse(vm.currentModule(id = 3).isCompleted)
    }

    private fun CourseDetailViewModel.currentModule(id: Int): CourseModule =
        state.value.items
            .filterIsInstance<CourseDetailItem.Module>()
            .first { item -> item.type.module.id == id }
            .type.module

    private fun section(
        id: Int,
        name: String,
        vararg modules: CourseModule,
    ) = CourseContent(id = id, name = name, modules = modules.toList())

    private fun module(
        id: Int,
        modname: String,
        description: String? = null,
        completionMode: Int = 0,
    ) = CourseModule(
        id = id,
        instance = id,
        name = "Módulo $id",
        modname = modname,
        url = null,
        description = description,
        files = emptyList(),
        completionMode = completionMode,
    )
}
