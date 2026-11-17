package onion.gh0st.iMoodle.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [UpdateSource.isTrustedDownloadUrl].
 *
 * Garantizan que solo se aceptan descargas de las *releases* del
 * repositorio oficial y que se rechazan intentos de colar otro origen.
 */
class UpdateSourceTest {

    private val officialUrl =
        "https://github.com/gh0st-2202/iMoodle/releases/download/v2.5/iMoodle-v2.5.apk"

    @Test
    fun `official release asset is trusted`() {
        assertTrue(UpdateSource.isTrustedDownloadUrl(officialUrl))
    }

    @Test
    fun `other repository is rejected`() {
        assertFalse(
            UpdateSource.isTrustedDownloadUrl(
                "https://github.com/otro-usuario/iMoodle/releases/download/v2.5/iMoodle.apk",
            ),
        )
    }

    @Test
    fun `other host is rejected`() {
        assertFalse(UpdateSource.isTrustedDownloadUrl("https://example.com/iMoodle.apk"))
    }

    @Test
    fun `plain http is rejected`() {
        assertFalse(UpdateSource.isTrustedDownloadUrl(officialUrl.replace("https://", "http://")))
    }

    @Test
    fun `lookalike host with the official name as a prefix is rejected`() {
        assertFalse(
            UpdateSource.isTrustedDownloadUrl(
                "https://github.com.evil.example/gh0st-2202/iMoodle/releases/download/v2.5/x.apk",
            ),
        )
    }

    @Test
    fun `blank url is rejected`() {
        assertFalse(UpdateSource.isTrustedDownloadUrl(""))
    }
}
