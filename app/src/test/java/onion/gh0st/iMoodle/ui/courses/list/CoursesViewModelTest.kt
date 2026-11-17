package onion.gh0st.iMoodle.ui.courses.list

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
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
import onion.gh0st.iMoodle.domain.usecase.course.GetCourses
import onion.gh0st.iMoodle.domain.usecase.course.GetFavouriteCourseIds
import onion.gh0st.iMoodle.domain.usecase.course.RefreshCourses
import onion.gh0st.iMoodle.domain.usecase.course.ToggleFavouriteCourse
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.fakes.FakeFavouritesRepository
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [CoursesViewModel].
 *
 * ## Qué cubren
 *
 * - Carga, ordenación alfabética y sincronización con la red.
 * - Los eventos de error cuando no se conoce al usuario.
 * - **Favoritos**: se ordenan primero, se marcan y se pueden alternar. En
 *   particular, que **se cargan aunque no haya red** (antes solo se leían
 *   si la sincronización tenía éxito).
 *
 * Estos tests dejaron de compilar cuando el ViewModel ganó los casos de
 * uso de favoritos; ahora los construyen con un
 * [FakeFavouritesRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepo = FakeSessionRepository(TestSessions.active())
    private val fakeRepo = FakeCoursesRepository()
    private val favouritesRepo = FakeFavouritesRepository()

    /**
     * Crea el ViewModel con los fakes de la prueba, envueltos en sus
     * casos de uso.
     *
     * @param session Repositorio de sesión falso que ve el ViewModel.
     * @return El ViewModel listo para probar.
     */
    private fun createViewModel(session: FakeSessionRepository = sessionRepo) = CoursesViewModel(
        getCourses = GetCourses(fakeRepo),
        refreshCourses = RefreshCourses(fakeRepo),
        observeSession = ObserveSession(session),
        getFavouriteCourseIds = GetFavouriteCourseIds(favouritesRepo),
        toggleFavouriteCourse = ToggleFavouriteCourse(favouritesRepo),
    )

    @Test
    fun `empty flow emits loaded state with empty list`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())

        val vm = createViewModel()
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

        val vm = createViewModel()
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

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCalls)
    }

    @Test
    fun `refresh triggers another sync`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())
        val vm = createViewModel()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.refreshCalls)
    }

    @Test
    fun `missing user id emits error event instead of querying user 0`() = runTest {
        fakeRepo.coursesFlow = flowOf(emptyList())
        val noUser = FakeSessionRepository(TestSessions.active(user = null))

        val vm = createViewModel(noUser)
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
        val vm = createViewModel(noUser)
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

    @Test
    fun `favourite courses are marked and listed first`() = runTest {
        favouritesRepo.setCourseFavourite(2, true)
        fakeRepo.coursesFlow = flowOf(listOf(course(1, "Apple"), course(2, "Zebra")))

        val vm = createViewModel()
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(listOf(2, 1), courses.map { it.id })
            assertTrue(courses.first { it.id == 2 }.isFavorite)
            assertFalse(courses.first { it.id == 1 }.isFavorite)
        }
    }

    @Test
    fun `favourites are loaded even when the network refresh fails`() = runTest {
        favouritesRepo.setCourseFavourite(2, true)
        fakeRepo.coursesFlow = flowOf(listOf(course(1, "Apple"), course(2, "Zebra")))
        fakeRepo.refreshError = java.io.IOException("sin red")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(2, 1), vm.uiState.value.courses.map { it.id })
        assertTrue(vm.uiState.value.courses.first { it.id == 2 }.isFavorite)
    }

    @Test
    fun `toggling a favourite updates the list`() = runTest {
        fakeRepo.coursesFlow = flowOf(listOf(course(1, "Apple"), course(2, "Zebra")))
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleFavourite(vm.uiState.value.courses.first { it.id == 2 })
        advanceUntilIdle()

        assertEquals(listOf(2, 1), vm.uiState.value.courses.map { it.id })
        assertTrue(vm.uiState.value.courses.first { it.id == 2 }.isFavorite)
    }

    @Test
    fun `failing to save a favourite emits an error event`() = runTest {
        fakeRepo.coursesFlow = flowOf(listOf(course(1, "Apple")))
        favouritesRepo.writeError = java.io.IOException("disco lleno")
        val vm = createViewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.toggleFavourite(vm.uiState.value.courses.first())
            val event = awaitItem() as CoursesUiEvent.ShowError
            assertEquals(R.string.error_unexpected, event.messageResId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `double tap while the first toggle is in flight only writes once`() = runTest {
        fakeRepo.coursesFlow = flowOf(listOf(course(1, "Apple")))
        val vm = createViewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        favouritesRepo.writeGate = gate
        val course = vm.uiState.value.courses.first()

        vm.toggleFavourite(course)
        vm.toggleFavourite(course)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("La segunda pulsación debe ignorarse", 1, favouritesRepo.writeCalls)
        assertTrue(vm.uiState.value.courses.first().isFavorite)
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
        var refreshError: Throwable? = null
        var refreshCalls: Int = 0
            private set

        override fun getCoursesFlow(userId: Int): Flow<List<Course>> = coursesFlow

        override suspend fun refreshCourses(userId: Int) {
            refreshCalls++
            refreshError?.let { throw it }
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
