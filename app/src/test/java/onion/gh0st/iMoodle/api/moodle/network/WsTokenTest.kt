package onion.gh0st.iMoodle.api.moodle.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * Tests de [decodeWstokenOrNull], la función que extrae el `wstoken` de
 * la redirección de `launch.php`.
 *
 * Esta lógica estaba duplicada en dos clases; ahora vive en un único
 * sitio y estos tests la protegen.
 */
class WsTokenTest {

    /**
     * Construye una cabecera `Location` como la de Moodle: esquema móvil
     * y `token=` con el contenido en Base64 URL-safe sin relleno.
     *
     * @param decodedContent Texto que irá codificado.
     * @return La cabecera `Location` simulada.
     */
    private fun redirectWith(decodedContent: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(decodedContent.toByteArray(Charsets.UTF_8))
        return "moodlemobile://token=$encoded"
    }

    @Test
    fun `extracts the token from the three part format`() {
        val location = redirectWith("siteid:::abc123token:::privatetoken")

        assertEquals("abc123token", location.decodeWstokenOrNull())
    }

    @Test
    fun `extracts the token when the private part is missing`() {
        val location = redirectWith("siteid:::abc123token")

        assertEquals("abc123token", location.decodeWstokenOrNull())
    }

    @Test
    fun `ignores extra query parameters after the token`() {
        val location = redirectWith("siteid:::abc123token:::p") + "&other=1"

        assertEquals("abc123token", location.decodeWstokenOrNull())
    }

    @Test
    fun `returns null when there is no token parameter`() {
        assertNull("moodlemobile://nothing".decodeWstokenOrNull())
    }

    @Test
    fun `returns null when the value is not valid base64`() {
        assertNull("moodlemobile://token=%%%no-es-base64".decodeWstokenOrNull())
    }

    @Test
    fun `returns null when the decoded content has no token part`() {
        assertNull(redirectWith("soloUnTexto").decodeWstokenOrNull())
    }
}
