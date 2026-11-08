package onion.gh0st.iMoodle.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de [WebUrls.isSafeWebUrl]. */
class WebUrlsTest {

    @Test
    fun `http and https are allowed in any case`() {
        assertTrue(WebUrls.isSafeWebUrl("http://example.com"))
        assertTrue(WebUrls.isSafeWebUrl("https://example.com/a?b=1"))
        assertTrue(WebUrls.isSafeWebUrl("  HTTPS://Example.com  "))
    }

    @Test
    fun `links with spaces or non ascii characters are still allowed`() {
        assertTrue(WebUrls.isSafeWebUrl("https://example.com/mi documento ñ.pdf"))
    }

    @Test
    fun `dangerous or unknown schemes are rejected`() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "intent://scan/#Intent;scheme=zxing;end",
            "content://com.android.contacts/contacts",
            "tel:123456789",
            "ftp://example.com/x",
        ).forEach { assertFalse("Debía rechazar $it", WebUrls.isSafeWebUrl(it)) }
    }

    @Test
    fun `null blank and scheme less values are rejected`() {
        assertFalse(WebUrls.isSafeWebUrl(null))
        assertFalse(WebUrls.isSafeWebUrl(""))
        assertFalse(WebUrls.isSafeWebUrl("   "))
        assertFalse(WebUrls.isSafeWebUrl("example.com/path"))
    }
}
