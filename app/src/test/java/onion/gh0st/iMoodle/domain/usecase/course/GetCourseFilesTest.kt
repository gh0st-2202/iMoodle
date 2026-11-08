package onion.gh0st.iMoodle.domain.usecase.course

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios de [GetCourseFiles].
 *
 * Verifica que el caso de uso filtra por `moduleId` y devuelve los
 * archivos del módulo correcto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCourseFilesTest {

    private val repo = FakeCourseRepository()
    private val useCase = GetCourseFiles(GetCourseContents(repo))

    /**
     * Debe devolver los archivos del módulo indicado.
     */
    @Test
    fun `returns files of matching module`() = runTest {
        repo.courseContents = listOf(
            section(
                id = 1,
                name = "Tema 1",
                module(id = 100, files = listOf(file("a.pdf"))),
                module(id = 200, files = listOf(file("b.pdf"))),
            ),
        )

        val result = useCase(courseId = 1, moduleId = 200)

        assertEquals(1, result.size)
        assertEquals("b.pdf", result[0].fileName)
    }

    /**
     * Si el módulo no existe, devuelve lista vacía.
     */
    @Test
    fun `returns empty list for unknown module`() = runTest {
        repo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 100, files = listOf(file("a.pdf")))),
        )

        val result = useCase(courseId = 1, moduleId = 999)

        assertTrue(result.isEmpty())
    }

    /**
     * Si el módulo existe pero no tiene archivos, devuelve lista vacía.
     */
    @Test
    fun `returns empty list when module has no files`() = runTest {
        repo.courseContents = listOf(
            section(id = 1, name = "Tema 1", module(id = 100, files = emptyList())),
        )

        val result = useCase(courseId = 1, moduleId = 100)

        assertTrue(result.isEmpty())
    }

    // --- Helpers ---

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