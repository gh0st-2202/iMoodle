package onion.gh0st.iMoodle.api.moodle.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiException
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [MoodleGlossaryRepository].
 *
 * Cubren el parseo de las entradas, la paginación de
 * `mod_glossary_get_entries_by_letter` y la forma exacta de los
 * parámetros de escritura, que es lo que Moodle valida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleGlossaryRepositoryTest {

    @Test
    fun `getEntries parses the entries and sends the ALL filter`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> ENTRIES_JSON })
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val entries = repository.getEntries(glossaryId = 3).getOrThrow()

        assertEquals(2, entries.size)
        with(entries[0]) {
            assertEquals(11, id)
            assertEquals("Variable", concept)
            assertEquals("<p>Un valor con nombre</p>", definition)
            assertEquals("Ana Pérez", author)
            assertEquals(1_700_000_000L, createdAt)
            assertEquals(1_700_000_500L, updatedAt)
            assertTrue(approved)
        }
        assertEquals(2, entries[1].id)
        assertEquals("mod_glossary_get_entries_by_letter", api.lastFunction)
        assertEquals(
            mapOf("id" to "3", "letter" to "ALL", "from" to "0", "limit" to "100"),
            api.lastParams,
        )
    }

    @Test
    fun `getEntries keeps asking for pages until the count is reached`() = runTest {
        val api = RecordingApiClient(
            responses = { _, params ->
                when (params.build()["from"]) {
                    "0" -> FIRST_PAGE_OF_THREE_JSON
                    else -> THIRD_ENTRY_JSON
                }
            },
        )
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val entries = repository.getEntries(glossaryId = 3).getOrThrow()

        assertEquals(3, entries.size)
        assertEquals(listOf("0", "100"), api.requestedOffsets)
    }

    @Test
    fun `getEntries stops when a page comes back empty`() = runTest {
        val api = RecordingApiClient(
            responses = { _, params ->
                if (params.build()["from"] == "0") FIRST_PAGE_OF_THREE_JSON else EMPTY_PAGE_JSON
            },
        )
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val entries = repository.getEntries(glossaryId = 3).getOrThrow()

        assertEquals(2, entries.size)
        assertEquals(listOf("0", "100"), api.requestedOffsets)
    }

    @Test
    fun `getEntries uses a fallback author when Moodle does not send it`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> ENTRY_WITHOUT_AUTHOR_JSON })
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val entry = repository.getEntries(glossaryId = 3).getOrThrow().single()

        assertEquals("Desconocido", entry.author)
        assertEquals(entry.createdAt, entry.updatedAt)
    }

    @Test
    fun `addEntry sends the concept and the definition as HTML`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val result = repository.addEntry(
            glossaryId = 3,
            concept = "Variable",
            definition = "<p>Valor</p>",
        )

        assertTrue(result.isSuccess)
        assertEquals("mod_glossary_add_entry", api.lastFunction)
        assertEquals(
            mapOf(
                "glossaryid" to "3",
                "concept" to "Variable",
                "definition" to "<p>Valor</p>",
                "definitionformat" to "1",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `updateEntry sends the id of the entry`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val result = repository.updateEntry(entryId = 9, concept = "Bucle", definition = "Repetición")

        assertTrue(result.isSuccess)
        assertEquals("mod_glossary_update_entry", api.lastFunction)
        assertEquals(
            mapOf(
                "id" to "9",
                "concept" to "Bucle",
                "definition" to "Repetición",
                "definitionformat" to "1",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `deleteEntry sends the id of the entry`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val result = repository.deleteEntry(entryId = 12)

        assertTrue(result.isSuccess)
        assertEquals("mod_glossary_delete_entry", api.lastFunction)
        assertEquals(mapOf("id" to "12"), api.lastParams)
    }

    @Test
    fun `moodle rejection becomes an IllegalStateException with its message`() = runTest {
        val api = RecordingApiClient(
            writeError = MoodleApiException("nopermission", "No puedes editar esta entrada"),
        )
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val result = repository.addEntry(glossaryId = 3, concept = "Variable", definition = "Valor")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("No puedes editar esta entrada", error?.message)
    }

    @Test
    fun `network failures are not translated`() = runTest {
        val api = RecordingApiClient(writeError = java.io.IOException("sin red"))
        val repository = MoodleGlossaryRepository(FakeSessionApiProvider(api))

        val result = repository.deleteEntry(entryId = 12)

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is IllegalStateException)
    }

    /** Proveedor que devuelve siempre el mismo cliente falso. */
    private class FakeSessionApiProvider(
        private val api: MoodleApiClient,
    ) : SessionApiProvider {
        override fun requireApi(): MoodleApiClient = api
    }

    /**
     * Cliente sin red: devuelve las respuestas configuradas y guarda lo
     * que se le pide.
     *
     * @param responses Respuesta de lectura según la función y los
     *   parámetros recibidos.
     * @param writeError Fallo que lanzan las escrituras, si lo hay.
     */
    private class RecordingApiClient(
        private val responses: (String, MoodleParams) -> String = { _, _ -> EMPTY_JSON },
        private val writeError: Throwable? = null,
    ) : MoodleApiClient(
        client = OkHttpClient(),
        config = MoodleConfig(provinciaSlug = "cordoba"),
        tokenRefresher = TokenRefresher { null },
        initialToken = "token-de-prueba",
    ) {
        var lastFunction: String? = null
            private set

        var lastParams: Map<String, String> = emptyMap()
            private set

        private val offsets = mutableListOf<String>()

        /** Valores de `from` de las lecturas, en orden. */
        val requestedOffsets: List<String> get() = offsets

        override suspend fun callREST(function: String, params: MoodleParams): String {
            lastFunction = function
            lastParams = params.build()
            params.build()["from"]?.let { from -> offsets += from }
            return responses(function, params)
        }

        override suspend fun callRESTWithFormBody(function: String, params: Map<String, String>): String {
            lastFunction = function
            lastParams = params
            writeError?.let { error -> throw error }
            return EMPTY_JSON
        }

        private companion object {
            const val EMPTY_JSON = "{}"
        }
    }

    private companion object {
        const val EMPTY_JSON = "{}"

        const val ENTRIES_JSON = """
            {
              "count": 2,
              "entries": [
                {
                  "id": 11,
                  "concept": "Variable",
                  "definition": "<p>Un valor con nombre</p>",
                  "userfullname": "Ana Pérez",
                  "timecreated": 1700000000,
                  "timemodified": 1700000500,
                  "approved": true
                },
                {
                  "id": 2,
                  "concept": "Bucle",
                  "definition": "Repetición",
                  "userfullname": "Luis Gil",
                  "timecreated": 1700000001,
                  "timemodified": 1700000001,
                  "approved": false
                }
              ]
            }
        """

        const val THIRD_ENTRY_JSON = """
            {
              "count": 3,
              "entries": [
                {
                  "id": 3,
                  "concept": "Función",
                  "definition": "Bloque reutilizable",
                  "userfullname": "Marta Ruiz",
                  "timecreated": 1700000002,
                  "timemodified": 1700000002,
                  "approved": true
                }
              ]
            }
        """

        const val EMPTY_PAGE_JSON = """{"count": 3, "entries": []}"""

        const val FIRST_PAGE_OF_THREE_JSON = """
            {
              "count": 3,
              "entries": [
                {
                  "id": 11,
                  "concept": "Variable",
                  "definition": "<p>Un valor con nombre</p>",
                  "userfullname": "Ana Pérez",
                  "timecreated": 1700000000,
                  "timemodified": 1700000500,
                  "approved": true
                },
                {
                  "id": 2,
                  "concept": "Bucle",
                  "definition": "Repetición",
                  "userfullname": "Luis Gil",
                  "timecreated": 1700000001,
                  "timemodified": 1700000001,
                  "approved": false
                }
              ]
            }
        """

        const val ENTRY_WITHOUT_AUTHOR_JSON = """
            {
              "count": 1,
              "entries": [
                {
                  "id": 4,
                  "concept": "Constante",
                  "definition": "Valor fijo",
                  "timecreated": 1700000003
                }
              ]
            }
        """
    }
}
