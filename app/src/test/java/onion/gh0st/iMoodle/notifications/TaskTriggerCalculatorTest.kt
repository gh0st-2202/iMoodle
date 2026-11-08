package onion.gh0st.iMoodle.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests unitarios de [TaskTriggerCalculator].
 *
 * Cubre:
 *
 * 1. Tarea vencida → `null`.
 * 2. Tarea de hoy, antes de las 12:00 → `null` (se notificó ayer).
 * 3. Tarea de hoy, a partir de las 12:00 → alarma a las 08:00 hoy.
 * 4. Tarea de mañana antes de las 12:00 → alarma a las 08:00 hoy.
 * 5. Tarea de mañana a las 12:00 en punto → alarma mañana a las 08:00.
 * 6. Tarea de mañana después de las 12:00 → alarma mañana a las 08:00.
 * 7. Tarea de varios días después → alarma ese día a las 08:00.
 * 8. Tarea a medianoche (Regla 1): "mañana 00:00" → "hoy 23:59" → alarma hoy.
 * 9. Tarea a medianoche ya pasada → `null`.
 * 10. **Sin catch-up**: si el nominal (08:00 del día que toque) ya pasó,
 *     la alarma no se programa (no hay "ahora + 1 minuto").
 */
class TaskTriggerCalculatorTest {

    // --- 1. Tarea vencida ---

    @Test
    fun `due date in the past returns null`() {
        val now = todayAt(hour = 7)
        val due = now - 60_000

        assertNull(calculate(due, now))
    }

    // --- 2. Tarea de hoy antes de las 12:00 → null ---

    @Test
    fun `due today before noon returns null because it was for yesterday`() {
        val now = todayAt(hour = 7)
        val due = todayAt(hour = 10)

        assertNull(calculate(due, now))
    }

    // --- 3. Tarea de hoy a partir de las 12:00 → hoy 08:00 ---

    @Test
    fun `due today after noon triggers today at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayAt(hour = 15)

        assertEquals(todayAt(hour = 8), calculate(due, now))
    }

    // --- 4. Tarea de mañana antes de las 12:00 → hoy 08:00 ---

    @Test
    fun `due tomorrow before noon triggers today at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayPlus(days = 1, hour = 11)

        assertEquals(todayAt(hour = 8), calculate(due, now))
    }

    // --- 5. Tarea de mañana a las 12:00 en punto → mañana 08:00 ---

    @Test
    fun `due tomorrow at noon triggers tomorrow at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayPlus(days = 1, hour = 12)

        assertEquals(todayPlus(days = 1, hour = 8), calculate(due, now))
    }

    // --- 6. Tarea de mañana después de las 12:00 → mañana 08:00 ---

    @Test
    fun `due tomorrow after noon triggers tomorrow at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayPlus(days = 1, hour = 15)

        assertEquals(todayPlus(days = 1, hour = 8), calculate(due, now))
    }

    // --- 7. Tarea de varios días después → ese día 08:00 ---

    @Test
    fun `due in several days triggers on that day at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayPlus(days = 3, hour = 15)

        assertEquals(todayPlus(days = 3, hour = 8), calculate(due, now))
    }

    // --- 8. Tarea a medianoche (Regla 1) → alarma hoy ---

    @Test
    fun `midnight due tomorrow triggers today at reminder hour`() {
        val now = todayAt(hour = 7)
        val due = todayPlus(days = 1, hour = 0)

        assertEquals(todayAt(hour = 8), calculate(due, now))
    }

    // --- 9. Tarea a medianoche ya pasada → null ---

    @Test
    fun `midnight due today is treated as yesterday end and returns null`() {
        val now = todayAt(hour = 7)
        val due = todayAt(hour = 0)

        assertNull(calculate(due, now))
    }

    // --- 10. Sin catch-up ---

    @Test
    fun `due today after noon with late now does not schedule (no catch-up)`() {
        val now = todayAt(hour = 10)
        val due = todayAt(hour = 15)

        assertNull(calculate(due, now))
    }

    @Test
    fun `due tomorrow before noon with late now does not schedule (no catch-up)`() {
        val now = todayAt(hour = 10)
        val due = todayPlus(days = 1, hour = 11)

        assertNull(calculate(due, now))
    }

    @Test
    fun `midnight due tomorrow with late now does not schedule (no catch-up)`() {
        // "Mañana 00:00" → "hoy 23:59" → nominal hoy 08:00.
        // Ahora son las 10:00 → el nominal ya pasó → null.
        val now = todayAt(hour = 10)
        val due = todayPlus(days = 1, hour = 0)

        assertNull(calculate(due, now))
    }

    // --- Integridad temporal ---

    @Test
    fun `trigger is always after now for future due dates`() {
        val now = todayAt(hour = 10)
        val due = todayPlus(days = 1, hour = 15)

        val trigger = calculate(due, now)

        assertNotNull(trigger)
        assertTrue(trigger!! > now)
    }

    // --- Helpers ---

    private fun calculate(due: Long, now: Long): Long? =
        TaskTriggerCalculator.calculate(due, now)

    private fun todayAt(hour: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayPlus(days: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}