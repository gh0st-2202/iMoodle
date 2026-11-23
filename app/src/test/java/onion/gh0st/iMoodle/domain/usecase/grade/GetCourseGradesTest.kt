package onion.gh0st.iMoodle.domain.usecase.grade

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.fakes.FakeGradeRepository
import onion.gh0st.iMoodle.fakes.FakeSessionRepository
import onion.gh0st.iMoodle.fakes.TestSessions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de [GetCourseGrades].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCourseGradesTest {

    private val repo = FakeGradeRepository()
    private val observeSession = ObserveSession(FakeSessionRepository(TestSessions.active()))
    private val useCase = GetCourseGrades(repo, observeSession)

    @Test
    fun `returns the grades of the requested course`() = runTest {
        repo.courseGrade = CourseGrade(courseId = 7, items = emptyList())

        val grade = useCase(courseId = 7)

        assertEquals(7, grade.courseId)
        assertEquals(7, repo.lastCourseId)
    }

    @Test
    fun `asks for the grades of the signed-in user`() = runTest {
        useCase(courseId = 7)

        assertEquals(1, repo.lastUserId)
    }
}
