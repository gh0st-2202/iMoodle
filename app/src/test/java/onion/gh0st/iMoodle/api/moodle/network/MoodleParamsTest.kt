package onion.gh0st.iMoodle.api.moodle.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests de [MoodleParams].
 *
 * Fijan el formato que espera Moodle, sobre todo el de los booleanos:
 * su validación estricta rechaza `"true"` y `"false"` con
 * `invalidparameter` (§9.70).
 */
class MoodleParamsTest {

    @Test
    fun `convierte los booleanos a 1 y 0`() {
        val params = MoodleParams().apply {
            put("lockonly", false)
            put("includecontent", true)
        }.build()

        assertEquals(mapOf("lockonly" to "0", "includecontent" to "1"), params)
    }

    @Test
    fun `ignora los valores nulos`() {
        val params = MoodleParams().apply {
            put("pageid", 9)
            put("section", null)
        }.build()

        assertEquals(mapOf("pageid" to "9"), params)
        assertFalse(params.containsKey("section"))
    }

    @Test
    fun `convierte los booleanos tambien en arrays y objetos anidados`() {
        val params = MoodleParams().apply {
            putArray("flags", listOf(true, false))
            putNested("options", "includecontent", false)
        }.build()

        assertEquals(
            mapOf(
                "flags[0]" to "1",
                "flags[1]" to "0",
                "options[includecontent]" to "0",
            ),
            params,
        )
    }

    @Test
    fun `conserva los enteros y el texto`() {
        val params = MoodleParams().apply {
            put("pageid", 9)
            put("content", "<p>Texto</p>")
        }.build()

        assertEquals(mapOf("pageid" to "9", "content" to "<p>Texto</p>"), params)
    }
}
