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
 * Tests de [MoodleWikiRepository].
 *
 * Cubren el parseo de páginas, la lectura del contenido original para
 * editar y los parámetros que Moodle valida en las escrituras.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleWikiRepositoryTest {

    @Test
    fun `getPages parses the titles and sends the current subwiki`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> PAGES_JSON })
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val pages = repository.getPages(wikiId = 4).getOrThrow()

        assertEquals(listOf("Guía", "Portada"), pages.map { it.title })
        assertEquals(9, pages[1].id)
        assertEquals("mod_wiki_get_subwiki_pages", api.lastFunction)
        assertEquals(
            mapOf(
                "wikiid" to "4",
                "groupid" to "-1",
                "userid" to "0",
                "options[sortby]" to "title",
                "options[sortdirection]" to "ASC",
                "options[includecontent]" to "0",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `getPage prefers the rendered content`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> PAGE_JSON })
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val page = repository.getPage(pageId = 9).getOrThrow()

        assertEquals("<p>Hola</p>", page.content)
        assertTrue(page.canEdit)
        assertTrue(page.isFirstPage)
        assertEquals(mapOf("pageid" to "9"), api.lastParams)
    }

    @Test
    fun `getPage falls back to the raw content when there is no rendered one`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> PAGE_WITHOUT_CACHED_JSON })
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val page = repository.getPage(pageId = 9).getOrThrow()

        assertEquals("Hola", page.content)
        assertFalse(page.canEdit)
    }

    @Test
    fun `getPage fails when Moodle does not return the page`() = runTest {
        val api = RecordingApiClient(responses = { _, _ -> EMPTY_JSON })
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.getPage(pageId = 9)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `getPageDraft returns the original content`() = runTest {
        val api = RecordingApiClient(writeResponse = DRAFT_JSON)
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val draft = repository.getPageDraft(pageId = 9).getOrThrow()

        assertEquals(9, draft.pageId)
        assertEquals("<p>Original</p>", draft.content)
        assertEquals("mod_wiki_get_page_for_editing", api.lastFunction)
        assertEquals(
            mapOf("pageid" to "9", "lockonly" to "0"),
            api.lastParams,
        )
    }

    @Test
    fun `getPageDraft translates a Moodle rejection with its message`() = runTest {
        val api = RecordingApiClient(writeError = MoodleApiException("pageislocked", "La página está bloqueada"))
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.getPageDraft(pageId = 9)

        assertTrue(result.isFailure)
        assertEquals("La página está bloqueada", result.exceptionOrNull()?.message)
    }

    @Test
    fun `updatePage sends the content without converting it`() = runTest {
        val api = RecordingApiClient()
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.updatePage(pageId = 9, content = "<p>Editado</p>")

        assertTrue(result.isSuccess)
        assertEquals("mod_wiki_edit_page", api.lastFunction)
        assertEquals(
            mapOf("pageid" to "9", "content" to "<p>Editado</p>"),
            api.lastParams,
        )
    }

    @Test
    fun `createPage returns the new page id`() = runTest {
        val api = RecordingApiClient(writeResponse = CREATED_PAGE_JSON)
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val pageId = repository.createPage(wikiId = 4, title = "Nueva", content = "Hola").getOrThrow()

        assertEquals(33, pageId)
        assertEquals("mod_wiki_new_page", api.lastFunction)
        assertEquals(
            mapOf(
                "wikiid" to "4",
                "title" to "Nueva",
                "content" to "Hola",
                "contentformat" to "html",
            ),
            api.lastParams,
        )
    }

    @Test
    fun `createPage fails when Moodle does not return a page id`() = runTest {
        val api = RecordingApiClient(writeResponse = EMPTY_JSON)
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.createPage(wikiId = 4, title = "Nueva", content = "Hola")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `moodle rejection becomes an IllegalStateException with its message`() = runTest {
        val api = RecordingApiClient(
            writeError = MoodleApiException("nopermission", "No puedes editar esta página"),
        )
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.updatePage(pageId = 9, content = "Editado")

        assertTrue(result.isFailure)
        assertEquals("No puedes editar esta página", result.exceptionOrNull()?.message)
    }

    @Test
    fun `network failures are not translated`() = runTest {
        val api = RecordingApiClient(writeError = java.io.IOException("sin red"))
        val repository = MoodleWikiRepository(FakeSessionApiProvider(api))

        val result = repository.updatePage(pageId = 9, content = "Editado")

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
     * @param writeResponse Respuesta de las escrituras.
     * @param writeError Fallo que lanzan las escrituras, si lo hay.
     */
    private class RecordingApiClient(
        private val responses: (String, MoodleParams) -> String = { _, _ -> EMPTY_JSON },
        private val writeResponse: String = EMPTY_JSON,
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

        override suspend fun callREST(function: String, params: MoodleParams): String {
            lastFunction = function
            lastParams = params.build()
            return responses(function, params)
        }

        override suspend fun callRESTWithFormBody(function: String, params: Map<String, String>): String {
            lastFunction = function
            lastParams = params
            writeError?.let { error -> throw error }
            return writeResponse
        }
    }

    private companion object {
        const val EMPTY_JSON = "{}"

        const val PAGES_JSON = """
            {
              "pages": [
                {"id": 8, "title": "Guía", "timemodified": 1700000200},
                {"id": 9, "title": "Portada", "timemodified": 1700000100, "firstpage": true}
              ]
            }
        """

        const val PAGE_JSON = """
            {
              "page": {
                "id": 9,
                "title": "Portada",
                "cachedcontent": "<p>Hola</p>",
                "content": "Hola",
                "timemodified": 1700000100,
                "caneditpage": true,
                "firstpage": true
              }
            }
        """

        const val PAGE_WITHOUT_CACHED_JSON = """
            {
              "page": {
                "id": 9,
                "title": "Portada",
                "content": "Hola",
                "caneditpage": false,
                "firstpage": false
              }
            }
        """

        const val DRAFT_JSON = """
            {
              "pagesection": {
                "pageid": 9,
                "content": "<p>Original</p>"
              }
            }
        """

        const val CREATED_PAGE_JSON = """{"pageid": 33}"""
    }
}
