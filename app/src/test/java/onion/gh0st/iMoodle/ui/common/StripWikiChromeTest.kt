package onion.gh0st.iMoodle.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests de [HtmlUtils.stripWikiChrome].
 *
 * Son cadenas fijas: la función solo manipula texto, así que no hace
 * falta Android ni un dispositivo.
 */
class StripWikiChromeTest {

    @Test
    fun `quita el indice que Moodle anade a las wikis`() {
        val html = """
            <div class="wiki-toc"><p class="wiki-toc-title">Tabla de contenidos</p>
            <p class="wiki-toc-section-1 wiki-toc-section">1. <a href="#toc-1">Inicio [Editar]</a></p></div>
            <h3>Inicio</h3><p>Contenido</p>
        """.trimIndent()

        val result = HtmlUtils.stripWikiChrome(html)

        assertFalse(result.contains("wiki-toc"))
        assertFalse(result.contains("Tabla de contenidos"))
        assertFalse(result.contains("[Editar]"))
        assertEquals("<h3>Inicio</h3><p>Contenido</p>", result)
    }

    @Test
    fun `quita el enlace de editar de cada encabezado`() {
        val html = """
            <h3><a name="toc-1"></a>Primera sección <a href="edit.php?pageid=9&amp;section=Primera" class="wiki_edit_section">[Editar]</a></h3>
            <p>Texto</p>
            <h3>Segunda sección <a href="edit.php?pageid=9&amp;section=Segunda" class="wiki_edit_section">[Editar]</a></h3>
        """.trimIndent()

        val result = HtmlUtils.stripWikiChrome(html)

        assertFalse(result.contains("wiki_edit_section"))
        assertFalse(result.contains("[Editar]"))
        assertFalse(result.contains("edit.php"))
    }

    @Test
    fun `conserva los enlaces propios de la pagina`() {
        val html = """<p>Mira <a href="https://example.com">este enlace</a>.</p>"""

        assertEquals(html, HtmlUtils.stripWikiChrome(html))
    }

    @Test
    fun `no toca un contenido que ya viene limpio`() {
        val html = "<h3>Título</h3><p>Sin chrome</p>"

        assertEquals(html, HtmlUtils.stripWikiChrome(html))
    }
}
