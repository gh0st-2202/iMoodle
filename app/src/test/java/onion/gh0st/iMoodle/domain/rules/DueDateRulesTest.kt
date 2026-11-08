package onion.gh0st.iMoodle.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Tests de [DueDateRules.adjustMidnight].
 *
 * Nota: los tests construyen fechas con `Calendar` local. La regla
 * solo se aplica a la hora local del dispositivo, así que el test es
 * independiente de la zona horaria siempre que se use el mismo huso
 * para construir la entrada y calcular el resultado esperado.
 */
class DueDateRulesTest {

    @Test
    fun `midnight is shifted one minute back`() {
        val midnight = at(hour = 0, minute = 0)

        assertEquals(midnight - 60_000L, DueDateRules.adjustMidnight(midnight))
    }

    @Test
    fun `non-midnight is left untouched`() {
        val atNoon = at(hour = 12, minute = 0)

        assertEquals(atNoon, DueDateRules.adjustMidnight(atNoon))
    }

    @Test
    fun `midnight plus one second is not shifted`() {
        val midnightPlusSecond = at(hour = 0, minute = 0) + 1_000L

        assertEquals(midnightPlusSecond, DueDateRules.adjustMidnight(midnightPlusSecond))
    }

    @Test
    fun `zero is returned as-is`() {
        assertEquals(0L, DueDateRules.adjustMidnight(0L))
    }

    @Test
    fun `negative values are returned as-is`() {
        assertEquals(-1L, DueDateRules.adjustMidnight(-1L))
    }

    @Test
    fun `end of day is not shifted`() {
        val endOfDay = at(hour = 23, minute = 59)

        assertEquals(endOfDay, DueDateRules.adjustMidnight(endOfDay))
    }

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}