package onion.gh0st.iMoodle.ui.book

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.MainDispatcherRule
import onion.gh0st.iMoodle.domain.model.book.BookChapter
import onion.gh0st.iMoodle.domain.model.book.BookContent
import onion.gh0st.iMoodle.domain.usecase.course.GetBookContent
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests de [BookViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeCourseRepository()

    private fun createViewModel(courseId: Int = 1, instanceId: Int = 200): BookViewModel =
        BookViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    FragmentArgs.COURSE_ID to courseId,
                    FragmentArgs.MODULE_INSTANCE_ID to instanceId,
                ),
            ),
            getBookContent = GetBookContent(fakeRepo),
        )

    @Test
    fun `successful load publishes the book`() = runTest {
        fakeRepo.bookContent = BookContent(
            name = "Libro 1",
            intro = "<p>Intro</p>",
            chapters = listOf(BookChapter(title = "Cap 1", content = "<p>C1</p>")),
        )

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertNotNull(content)
            assertEquals(1, content?.chapters?.size)
            assertFalse(hasError)
        }
    }

    @Test
    fun `missing book sets hasError`() = runTest {
        fakeRepo.bookContent = null

        val viewModel = createViewModel()

        with(viewModel.state.value) {
            assertFalse(isLoading)
            assertTrue(hasError)
        }
    }

    @Test
    fun `refresh reloads the content`() = runTest {
        fakeRepo.bookContent = BookContent(name = "Libro 1", intro = null, chapters = emptyList())
        val viewModel = createViewModel()
        fakeRepo.bookContent = BookContent(
            name = "Libro 1",
            intro = "<p>Nuevo</p>",
            chapters = emptyList(),
        )

        viewModel.refresh()

        assertEquals("<p>Nuevo</p>", viewModel.state.value.content?.intro)
    }
}
