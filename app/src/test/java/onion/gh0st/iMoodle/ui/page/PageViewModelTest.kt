package onion.gh0st.iMoodle.ui.page

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.domain.model.page.PageContent
import onion.gh0st.iMoodle.domain.usecase.course.GetPageContent
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [PageViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeCourseRepository()

    private fun createViewModel(courseId: Int = 1, instanceId: Int = 200): PageViewModel =
        PageViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    FragmentArgs.COURSE_ID to courseId,
                    FragmentArgs.MODULE_INSTANCE_ID to instanceId,
                ),
            ),
            getPageContent = GetPageContent(fakeRepo),
        )

    @Test
    fun `successful load publishes the page`() = runTest {
        fakeRepo.pageContent = PageContent(name = "Página 1", content = "<p>Hola</p>")

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertNotNull(content)
            assertEquals("<p>Hola</p>", content?.content)
            assertFalse(hasError)
        }
    }

    @Test
    fun `missing page sets hasError`() = runTest {
        fakeRepo.pageContent = null

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertTrue(hasError)
        }
    }

    @Test
    fun `refresh reloads the content`() = runTest {
        fakeRepo.pageContent = PageContent(name = "Página 1", content = "<p>Hola</p>")
        val viewModel = createViewModel()
        fakeRepo.pageContent = PageContent(name = "Página 1", content = "<p>Adiós</p>")

        viewModel.refresh()

        assertEquals("<p>Adiós</p>", viewModel.state.value.content?.content)
    }
}
