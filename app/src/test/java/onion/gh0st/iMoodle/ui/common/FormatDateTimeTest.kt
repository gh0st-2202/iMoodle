package onion.gh0st.iMoodle.ui.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Tests de [FormatUtils.formatDateTime].
 *
 * Fijan la zona horaria y el idioma durante la prueba para que el
 * resultado sea el mismo en cualquier máquina.
 */
class FormatDateTimeTest {

    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `formats epoch seconds as day month year and time`() {
        assertEquals("14/11/2023 22:13", FormatUtils.formatDateTime(1_700_000_000L))
    }

    @Test
    fun `epoch zero is the start of 1970`() {
        assertEquals("01/01/1970 00:00", FormatUtils.formatDateTime(0L))
    }

    @Test
    fun `respects the device time zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))

        assertEquals("14/11/2023 23:13", FormatUtils.formatDateTime(1_700_000_000L))
    }
}
