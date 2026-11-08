package onion.gh0st.iMoodle.api.pasen.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PasenStudentInfoParser].
 */
class PasenStudentInfoParserTest {

    @Test
    fun `parses sections and their items`() {
        val sections = PasenStudentInfoParser.parse(VALID_JSON)

        assertEquals(1, sections.size)
        val section = sections.single()
        assertEquals("Datos Personales", section.title)
        assertEquals(3, section.items.size)
        assertEquals("Nombre" to "Apellido1 Apellido2, Nombre", section.items[0])
        assertEquals("Edad" to "21 años", section.items[1])
        assertEquals("Sexo" to "Hombre", section.items[2])
    }

    @Test
    fun `throws when success is false`() {
        val exception = runCatching {
            PasenStudentInfoParser.parse("""{"success":false,"message":"No autorizado"}""")
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertEquals("No autorizado", exception?.message)
    }

    @Test
    fun `empty data returns empty list`() {
        assertTrue(PasenStudentInfoParser.parse("""{"success":true,"data":[]}""").isEmpty())
    }

    @Test
    fun `sections without items are skipped`() {
        val json = """
            {
              "success": true,
              "data": [
                {"section": "Vacía", "items": []},
                {"section": "Datos", "items": [["A", "1"]]}
              ]
            }
        """
        val sections = PasenStudentInfoParser.parse(json)

        assertEquals(1, sections.size)
        assertEquals("Datos", sections.single().title)
    }

    @Test
    fun `items with empty values are ignored`() {
        val json = """
            {
              "success": true,
              "data": [
                {"section": "Datos", "items": [["A", ""], ["B", "2"]]}
              ]
            }
        """
        val sections = PasenStudentInfoParser.parse(json)

        assertEquals(1, sections.single().items.size)
        assertEquals("B" to "2", sections.single().items[0])
    }

    private companion object {
        const val VALID_JSON = """
            {
              "success": true,
              "data": [
                {
                  "section": "Datos Personales",
                  "items": [
                    ["Nombre", "Apellido1 Apellido2, Nombre"],
                    ["Edad", "21 años"],
                    ["Sexo", "Hombre"]
                  ]
                }
              ]
            }
        """
    }
}