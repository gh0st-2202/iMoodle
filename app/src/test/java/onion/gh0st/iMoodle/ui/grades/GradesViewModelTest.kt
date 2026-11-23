package onion.gh0st.iMoodle.ui.grades

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.model.grade.GradeItem
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseContents
import onion.gh0st.iMoodle.domain.usecase.grade.GetCourseGrades
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import onion.gh0st.iMoodle.fakes.FakeGradeRepository
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [GradesViewModel].
 *
 * Cubren la carga, el agrupado por sección en el orden del curso, el
 * descarte de los totales de categoría y la conservación de las notas
 * anteriores cuando falla un refresco.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GradesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeGradeRepository()
    private val observeSession = ObserveSession(FakeSessionRepository(TestSessions.active()))

    private val courseRepo = FakeCourseRepository()

    private fun createViewModel(courseId: Int = 7): GradesViewModel {
        val savedState = SavedStateHandle(mapOf("COURSE_ID" to courseId))
        return GradesViewModel(
            savedStateHandle = savedState,
            getCourseGrades = GetCourseGrades(repo, observeSession),
            getCourseContents = GetCourseContents(courseRepo),
        )
    }

    @Test
    fun `successful load publishes the grades`() = runTest {
        repo.courseGrade = CourseGrade(courseId = 7, items = listOf(gradeItem(id = 1)))

        val vm = createViewModel()

        with(vm.state.value) {
            assertFalse(isLoading)
            assertEquals(1, items.size)
        }
    }

    @Test
    fun `load error keeps the previous grades and emits an error`() = runTest {
        repo.gradeError = RuntimeException("boom")

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(vm.state.value.hasError)
        vm.events.test {
            val event = awaitItem() as GradesUiEvent.ShowError
            assertEquals(R.string.error_grades_load, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh asks Moodle again`() = runTest {
        val vm = createViewModel()

        vm.refresh()

        assertEquals(7, repo.lastCourseId)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `sections and activities follow the course order`() = runTest {
        courseRepo.courseContents = listOf(
            courseContent(
                id = 1,
                name = "Unidad 2",
                courseModule(id = 20),
                courseModule(id = 10),
            ),
            courseContent(id = 2, name = "Unidad 1", courseModule(id = 30)),
        )
        repo.courseGrade = CourseGrade(
            courseId = 7,
            items = listOf(
                gradeItem(id = 1, name = "Total", itemType = "course", itemModule = null),
                gradeItem(id = 2, name = "A", cmid = 30),
                gradeItem(id = 3, name = "B", cmid = 20),
                gradeItem(id = 4, name = "C", cmid = 10),
            ),
        )

        val vm = createViewModel()

        assertEquals(
            listOf("Total", "Unidad 2", "B", "C", "Unidad 1", "A"),
            vm.state.value.items.map(::labelOf),
        )
    }

    @Test
    fun `category totals are dropped`() = runTest {
        courseRepo.courseContents = listOf(courseContent(id = 1, name = "Unidad 1", courseModule(id = 10)))
        repo.courseGrade = CourseGrade(
            courseId = 7,
            items = listOf(
                gradeItem(id = 1, name = "Total", itemType = "course", itemModule = null),
                gradeItem(id = 2, name = "Cálculo total de Unidad 1", itemType = "category", itemModule = null),
                gradeItem(id = 3, name = "Práctica", cmid = 10),
            ),
        )

        val vm = createViewModel()

        assertEquals(
            listOf("Total", "Unidad 1", "Práctica"),
            vm.state.value.items.map(::labelOf),
        )
    }

    @Test
    fun `activities without section go at the end`() = runTest {
        repo.courseGrade = CourseGrade(
            courseId = 7,
            items = listOf(
                gradeItem(id = 1, name = "Total", itemType = "course", itemModule = null),
                gradeItem(id = 2, name = "Nota manual", itemType = "manual", itemModule = null),
            ),
        )

        val vm = createViewModel()

        assertEquals(
            listOf("Total", "Nota manual"),
            vm.state.value.items.map(::labelOf),
        )
    }

    @Test
    fun `contents failure still shows the grades without headers`() = runTest {
        courseRepo.contentsError = RuntimeException("boom")
        repo.courseGrade = CourseGrade(
            courseId = 7,
            items = listOf(gradeItem(id = 2, name = "Práctica", cmid = 10)),
        )

        val vm = createViewModel()

        assertEquals(listOf("Práctica"), vm.state.value.items.map(::labelOf))
        assertTrue(vm.state.value.items.single() is GradesListItem.Grade)
    }

    @Test
    fun `a failed refresh keeps the previous grades`() = runTest {
        repo.courseGrade = CourseGrade(courseId = 7, items = listOf(gradeItem(id = 1)))
        val vm = createViewModel()
        val previousItems = vm.state.value.items

        repo.gradeError = RuntimeException("boom")
        vm.refresh()

        assertEquals(previousItems, vm.state.value.items)
        assertTrue(vm.state.value.hasError)
    }

    private fun labelOf(item: GradesListItem): String = when (item) {
        is GradesListItem.SectionHeader -> item.title
        is GradesListItem.Grade -> item.item.name
    }

    private fun courseContent(
        id: Int,
        name: String,
        vararg modules: CourseModule,
    ) = CourseContent(id = id, name = name, modules = modules.toList())

    private fun courseModule(id: Int) = CourseModule(
        id = id,
        instance = id,
        name = "Módulo $id",
        modname = "assign",
        url = null,
        description = null,
        files = emptyList(),
    )

    private fun gradeItem(
        id: Int,
        name: String = "Actividad $id",
        itemType: String = "mod",
        itemModule: String? = "assign",
        cmid: Int? = null,
    ) = GradeItem(
        id = id,
        name = name,
        itemType = itemType,
        itemModule = itemModule,
        cmid = cmid,
        gradeFormatted = "7,50 / 10,00",
        gradeRaw = 7.5,
        gradeMin = 0.0,
        gradeMax = 10.0,
        isHidden = false,
        isLocked = false,
    )
}
