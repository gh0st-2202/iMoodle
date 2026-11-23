package onion.gh0st.iMoodle.domain.usecase.course

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.fakes.FakeCompletionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [ManageModuleCompletion].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageModuleCompletionTest {

    private val repo = FakeCompletionRepository()
    private val useCase = ManageModuleCompletion(repo)

    @Test
    fun `markViewed delegates the module`() = runTest {
        val module = CourseModule(
            id = 12,
            instance = 3,
            name = "Tarea",
            modname = "assign",
            url = null,
            description = null,
        )

        useCase.markViewed(module)

        assertEquals(module, repo.lastViewedModule)
    }

    @Test
    fun `setCompleted delegates the new state`() = runTest {
        useCase.setCompleted(cmId = 12, completed = true)

        assertEquals(12 to true, repo.lastCompletionChange)
    }

    @Test
    fun `failures are returned instead of thrown`() = runTest {
        repo.completionResult = Result.failure(RuntimeException("boom"))

        val result = useCase.setCompleted(cmId = 12, completed = false)

        assertTrue(result.isFailure)
    }
}
