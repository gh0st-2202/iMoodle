package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.data.local.dao.CalendarDao
import onion.gh0st.iMoodle.data.local.entity.CalendarEventEntity
import onion.gh0st.iMoodle.fakes.FakeSessionApiProvider
import onion.gh0st.iMoodle.fakes.RecordingTransactionRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tests de [MoodleCalendarRepository.refreshEvents].
 *
 * Garantías:
 * - Borrar e insertar ocurre **dentro de una única transacción**.
 * - Si la red falla, Room no se toca y se conservan los datos anteriores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleCalendarRepositoryTest {

    private val runner = RecordingTransactionRunner()

    /** DAO que anota si cada operación ocurrió dentro de la transacción. */
    private class SpyCalendarDao(private val runner: RecordingTransactionRunner) : CalendarDao {
        val clearInTransaction = mutableListOf<Boolean>()
        val insertInTransaction = mutableListOf<Boolean>()
        var inserted: List<CalendarEventEntity> = emptyList()

        override fun getEvents(startTime: Long, endTime: Long): Flow<List<CalendarEventEntity>> =
            flowOf(emptyList())

        override suspend fun insertEvents(events: List<CalendarEventEntity>) {
            insertInTransaction += runner.active
            inserted = events
        }

        override suspend fun clearAll() {
            clearInTransaction += runner.active
        }
    }

    private fun repo(dao: CalendarDao, response: () -> String): MoodleCalendarRepository {
        val api = object : MoodleApiClient(
            client = OkHttpClient(),
            config = MoodleConfig(provinciaSlug = "cordoba"),
            tokenRefresher = TokenRefresher { null },
            initialToken = "t",
        ) {
            override suspend fun callREST(function: String, params: MoodleParams): String =
                response()
        }
        return MoodleCalendarRepository(FakeSessionApiProvider(api), dao, runner)
    }

    @Test
    fun `refresh clears and inserts inside a single transaction`() = runTest {
        val dao = SpyCalendarDao(runner)
        val repo = repo(dao) { EVENTS_JSON }

        repo.refreshEvents(startTime = 0, endTime = Long.MAX_VALUE)

        assertEquals(1, runner.runs)
        assertEquals(listOf(true), dao.clearInTransaction)
        assertEquals(listOf(true), dao.insertInTransaction)
        assertEquals(1, dao.inserted.size)
        assertEquals(5, dao.inserted.single().id)
    }

    @Test
    fun `network failure leaves room untouched`() = runTest {
        val dao = SpyCalendarDao(runner)
        val repo = repo(dao) { throw AuthException.Network(IOException("offline")) }

        val error = runCatching { repo.refreshEvents(0, Long.MAX_VALUE) }.exceptionOrNull()

        assertTrue(error is AuthException.Network)
        assertEquals(0, runner.runs)
        assertTrue(dao.clearInTransaction.isEmpty())
        assertTrue(dao.insertInTransaction.isEmpty())
    }

    private companion object {
        const val EVENTS_JSON = """
            {
              "events": [
                {
                  "id": 5, "name": "Entrega", "timesort": 1800000000,
                  "modulename": "assign", "instance": 3,
                  "course": { "id": 2, "fullname": "Curso" }
                }
              ]
            }
        """
    }
}