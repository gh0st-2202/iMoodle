package onion.gh0st.iMoodle.domain.usecase.course

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.fakes.FakeCourseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios de [GetCourseContents].
 *
 * Verifica que el caso de uso clasifica correctamente cada tipo de
 * módulo de Moodle en su [ModuleType] correspondiente, sin tocar
 * Android ni red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetCourseContentsTest {

    private val repo = FakeCourseRepository()
    private val useCase = GetCourseContents(repo)

    /**
     * Un módulo `assign` debe clasificarse como [ModuleType.Assignment].
     */
    @Test
    fun `assign module is classified as Assignment`() = runTest {
        repo.courseContents = listOf(section(module(id = 1, modname = "assign")))

        val sections = useCase(courseId = 1)

        assertEquals(1, sections.size)
        assertTrue(sections[0].modules[0] is ModuleType.Assignment)
    }

    /**
     * Un módulo `forum` debe clasificarse como [ModuleType.Forum].
     */
    @Test
    fun `forum module is classified as Forum`() = runTest {
        repo.courseContents = listOf(section(module(id = 2, modname = "forum")))

        val sections = useCase(courseId = 1)

        assertTrue(sections[0].modules[0] is ModuleType.Forum)
    }

    /**
     * Un módulo `label` con descripción debe clasificarse como
     * [ModuleType.Label] preservando el contenido.
     */
    @Test
    fun `label module keeps its content`() = runTest {
        repo.courseContents = listOf(
            section(module(id = 3, modname = "label", description = "<p>Hola</p>")),
        )

        val sections = useCase(courseId = 1)

        val label = sections[0].modules[0] as ModuleType.Label
        assertEquals("<p>Hola</p>", label.content)
    }

    /**
     * Un módulo desconocido debe clasificarse como [ModuleType.Other].
     */
    @Test
    fun `unknown module is classified as Other`() = runTest {
        repo.courseContents = listOf(section(module(id = 4, modname = "quiz")))

        val sections = useCase(courseId = 1)

        assertTrue(sections[0].modules[0] is ModuleType.Other)
    }

    /**
     * Un error del repositorio debe propagarse al llamador.
     */
    @Test
    fun `repository error propagates`() = runTest {
        repo.contentsError = RuntimeException("boom")

        val exception = runCatching { useCase(courseId = 1) }.exceptionOrNull()

        assertTrue(exception is RuntimeException)
        assertEquals("boom", exception?.message)
    }

    // --- Helpers ---

    private fun section(vararg modules: CourseModule) = CourseContent(
        id = 10,
        name = "Tema 1",
        modules = modules.toList(),
    )

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
        files = listOf(file()),
    )

    private fun file() = CourseFile(
        fileName = "file.pdf",
        fileUrl = "https://example.com/file.pdf?token=x",
        fileSize = 1024,
        mimeType = "application/pdf",
        timeModified = 0,
    )
}