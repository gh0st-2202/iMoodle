package onion.gh0st.iMoodle.core

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Tests de [runCatchingCancellable]. */
class ResultsTest {

    @Test
    fun `returns the value on success`() {
        assertEquals(5, runCatchingCancellable { 5 }.getOrThrow())
    }

    @Test
    fun `captures regular exceptions as failure`() {
        val result = runCatchingCancellable { error("boom") }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `rethrows cancellation instead of capturing it`() {
        try {
            runCatchingCancellable { throw CancellationException("cancelada") }
            fail("Debía relanzar la cancelación")
        } catch (e: CancellationException) {
            assertEquals("cancelada", e.message)
        }
    }
}
