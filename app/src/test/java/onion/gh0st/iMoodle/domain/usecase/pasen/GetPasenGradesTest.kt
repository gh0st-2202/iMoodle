package onion.gh0st.iMoodle.domain.usecase.pasen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.fakes.FakePasenGradeRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de [GetPasenGrades].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetPasenGradesTest {

    private val repo = FakePasenGradeRepository()
    private val useCase = GetPasenGrades(repo)

    @Test
    fun `returns the convocatorias from the repository`() = runTest {
        repo.grades = listOf(PasenConvocatoria("1ª Evaluación", false, "", "", true, 1, emptyList()))

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals("1ª Evaluación", result.single().course)
    }

    @Test
    fun `propagates the forceRefresh flag`() = runTest {
        useCase(forceRefresh = true)

        assertEquals(true, repo.lastForceRefresh)
    }
}
