package onion.gh0st.iMoodle.api.pasen.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PasenScheduleParser].
 *
 * Se usa un JSON de fixture inline (no hay fichero externo) para
 * mantener el test autocontenido. Si en el futuro se externaliza a
 * `app/src/test/resources/fixtures/pasen_horario.json`, basta con
 * leerlo con `getResourceAsStream`.
 */
class PasenScheduleParserTest {

    @Test
    fun `parses a valid response with two slots`() {
        val slots = PasenScheduleParser.parse(VALID_JSON)

        assertEquals(2, slots.size)
        val first = slots[0]
        assertEquals("Lunes", first.day)
        assertEquals("8:30 - 9:30", first.hoursection)
        assertEquals(1, first.section)
        assertEquals("Acceso a datos", first.subject)
        assertEquals("García, Juan", first.professor)
        assertEquals(1_000_001, first.codeSection)
    }

    @Test
    fun `throws when success is false`() {
        val exception = runCatching {
            PasenScheduleParser.parse("""{"success":false,"message":"Token expirado"}""")
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        assertEquals("Token expirado", exception?.message)
    }

    @Test
    fun `throws when data is missing`() {
        val exception = runCatching {
            PasenScheduleParser.parse("""{"success":true}""")
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `empty data returns empty list`() {
        val slots = PasenScheduleParser.parse("""{"success":true,"data":[]}""")
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `slot with missing fields uses safe defaults`() {
        val slots = PasenScheduleParser.parse("""{"success":true,"data":[{}]}""")

        assertEquals(1, slots.size)
        val slot = slots[0]
        assertEquals("", slot.day)
        assertEquals("", slot.hoursection)
        assertEquals(0, slot.section)
        assertEquals("", slot.subject)
        assertEquals("", slot.professor)
        assertEquals(0, slot.codeSection)
    }

    private companion object {
        const val VALID_JSON = """
            {
              "success": true,
              "data": [
                {
                  "day": "Lunes",
                  "hoursection": "8:30 - 9:30",
                  "section": 1,
                  "subject": "Acceso a datos",
                  "prof": "García, Juan",
                  "codesection": 1000001
                },
                {
                  "day": "Martes",
                  "hoursection": "10:00 - 11:00",
                  "section": 3,
                  "subject": "Desarrollo de interfaces",
                  "prof": "Pérez, Ana",
                  "codesection": 1000002
                }
              ]
            }
        """
    }
}