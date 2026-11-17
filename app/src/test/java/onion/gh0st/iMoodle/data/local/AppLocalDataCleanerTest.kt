package onion.gh0st.iMoodle.data.local

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import onion.gh0st.iMoodle.data.schedule.ScheduleCache
import onion.gh0st.iMoodle.data.session.UserProfileCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [AppLocalDataCleaner].
 *
 * Protegen dos garantías del cierre de sesión:
 *
 * 1. Se borran **todos** los almacenes: base de datos, horario y perfil.
 *    (Antes solo se vaciaba la base de datos y los datos personales de
 *    Pasen seguían en el teléfono.)
 * 2. Un fallo en un almacén no impide borrar los demás.
 */
class AppLocalDataCleanerTest {

    private val scheduleCache = mockk<ScheduleCache>(relaxed = true)
    private val userProfileCache = mockk<UserProfileCache>(relaxed = true)

    @Test
    fun `clear empties the database and both caches`() = runTest {
        var databaseCleared = false
        val cleaner = AppLocalDataCleaner(
            databaseCleaner = { databaseCleared = true },
            scheduleCache = scheduleCache,
            userProfileCache = userProfileCache,
        )

        cleaner.clear()

        assertTrue(databaseCleared)
        coVerify(exactly = 1) { scheduleCache.clearAll() }
        coVerify(exactly = 1) { userProfileCache.clearAll() }
    }

    @Test
    fun `a failing database does not prevent clearing the caches and the error is rethrown`() = runTest {
        val cleaner = AppLocalDataCleaner(
            databaseCleaner = { throw IllegalStateException("disco lleno") },
            scheduleCache = scheduleCache,
            userProfileCache = userProfileCache,
        )

        val error = runCatching { cleaner.clear() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("disco lleno", error?.message)
        coVerify(exactly = 1) { scheduleCache.clearAll() }
        coVerify(exactly = 1) { userProfileCache.clearAll() }
    }

    @Test
    fun `a failing cache does not prevent clearing the next one`() = runTest {
        coEvery { scheduleCache.clearAll() } throws IllegalStateException("fallo")
        val cleaner = AppLocalDataCleaner(
            databaseCleaner = { },
            scheduleCache = scheduleCache,
            userProfileCache = userProfileCache,
        )

        runCatching { cleaner.clear() }

        coVerify(exactly = 1) { userProfileCache.clearAll() }
    }
}
