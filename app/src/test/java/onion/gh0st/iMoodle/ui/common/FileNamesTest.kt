package onion.gh0st.iMoodle.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de [FileNames.sanitize]: el nombre viene del servidor y no es de fiar. */
class FileNamesTest {

    @Test
    fun `normal names are kept`() {
        assertEquals("informe final.pdf", FileNames.sanitize("informe final.pdf"))
    }

    @Test
    fun `path components are discarded`() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("evil.apk", FileNames.sanitize("..\\..\\evil.apk"))
        assertEquals("a.pdf", FileNames.sanitize("/absolute/a.pdf"))
    }

    @Test
    fun `reserved characters are replaced`() {
        assertEquals("a_b_c_.pdf", FileNames.sanitize("a:b?c*.pdf"))
    }

    @Test
    fun `control characters are replaced`() {
        assertEquals("a_b.txt", FileNames.sanitize("a\u0000b.txt"))
    }

    @Test
    fun `names that end up empty use the fallback`() {
        assertEquals("archivo", FileNames.sanitize(""))
        assertEquals("archivo", FileNames.sanitize(".."))
        assertEquals("archivo", FileNames.sanitize("   "))
        assertEquals("otro", FileNames.sanitize("///", fallback = "otro"))
    }

    @Test
    fun `leading dots are removed so hidden files are not created`() {
        assertEquals("oculto", FileNames.sanitize("...oculto"))
    }
}
