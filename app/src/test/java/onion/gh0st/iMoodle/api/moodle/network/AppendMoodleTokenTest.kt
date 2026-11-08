package onion.gh0st.iMoodle.api.moodle.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de [appendMoodleToken]. Lo importante es que el token **nunca** se
 * añada a URLs de otro host (enlaces externos de profesores).
 */
class AppendMoodleTokenTest {

    private val host = "aulas.example.es"

    @Test
    fun `appends with question mark when there is no query`() {
        assertEquals(
            "https://aulas.example.es/f.pdf?token=T",
            "https://aulas.example.es/f.pdf".appendMoodleToken("T", host),
        )
    }

    @Test
    fun `appends with ampersand when there is already a query`() {
        assertEquals(
            "https://aulas.example.es/f.pdf?a=1&token=T",
            "https://aulas.example.es/f.pdf?a=1".appendMoodleToken("T", host),
        )
    }

    @Test
    fun `does not duplicate an existing token`() {
        val url = "https://aulas.example.es/f.pdf?token=OTHER"
        assertEquals(url, url.appendMoodleToken("T", host))
    }

    @Test
    fun `never adds the token to a foreign host`() {
        val url = "https://evil.example.com/malware.pdf"
        assertEquals(url, url.appendMoodleToken("T", host))
    }

    @Test
    fun `userinfo trick does not pass as the moodle host`() {
        val url = "https://aulas.example.es@evil.example.com/x"
        assertEquals(url, url.appendMoodleToken("T", host))
    }

    @Test
    fun `host with port and different case still matches`() {
        assertEquals(
            "http://Localhost:8080/f?token=T",
            "http://Localhost:8080/f".appendMoodleToken("T", "localhost:8080"),
        )
    }

    @Test
    fun `malformed url is left untouched when a host is required`() {
        val url = "https://aulas.example.es/con espacio"
        assertEquals(url, url.appendMoodleToken("T", host))
    }

    @Test
    fun `without allowed host the legacy behaviour is kept`() {
        assertEquals(
            "https://x.test/a?token=T",
            "https://x.test/a".appendMoodleToken("T"),
        )
    }
}
