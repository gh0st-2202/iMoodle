package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiException
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.domain.repository.wiki.WikiRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [WikiRepository] sobre la API REST de Moodle.
 *
 * ## Qué hace
 *
 * Habla con `mod_wiki` para leer y escribir las páginas de una wiki:
 *
 * - `mod_wiki_get_subwiki_pages` para listar las páginas visibles para
 *   el usuario (la subwiki actual, sin contenido para que la lista sea
 *   ligera).
 * - `mod_wiki_get_page_contents` para el contenido renderizado de una
 *   página.
 * - `mod_wiki_get_page_for_editing` para el contenido original.
 * - `mod_wiki_edit_page` y `mod_wiki_new_page` para guardar y crear.
 *
 * ## Sobre las escrituras
 *
 * Van por `POST` porque el contenido puede ser largo. Cuando Moodle
 * rechaza la operación, su mensaje se convierte en
 * `IllegalStateException` para que la UI pueda enseñarlo sin conocer la
 * capa de red (ver [callWrite]).
 *
 * ## Qué NO hace
 *
 * - **No cachea**: las wikis se editan y el usuario debe ver siempre lo
 *   último.
 * - **No gestiona adjuntos, comentarios ni historial de versiones**:
 *   solo lectura y edición del texto.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 */
@Singleton
class MoodleWikiRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : WikiRepository {

    /**
     * Lista las páginas de la wiki.
     *
     * ## Sobre los parámetros
     *
     * - `groupid = -1` y `userid = 0` significan "el grupo y el usuario
     *   actuales", que es lo que corresponde a la subwiki que ve quien
     *   consulta.
     * - `includecontent = 0` evita descargar el texto de todas las
     *   páginas: la lista solo necesita título y fecha.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @return Las páginas, o el fallo ocurrido.
     */
    override suspend fun getPages(wikiId: Int): Result<List<WikiPage>> =
        runCatchingCancellable {
            val params = MoodleParams().apply {
                put("wikiid", wikiId)
                put("groupid", CURRENT_GROUP)
                put("userid", CURRENT_USER)
                putNested("options", "sortby", "title")
                putNested("options", "sortdirection", "ASC")
                putNested("options", "includecontent", 0)
            }
            val response = JSONObject(callRest("mod_wiki_get_subwiki_pages", params))
            response.optJSONArray("pages").objects().map { pageJson -> parsePage(pageJson) }
        }

    /**
     * Devuelve el contenido renderizado de una página.
     *
     * @param pageId ID de la página.
     * @return La página, o el fallo ocurrido.
     */
    override suspend fun getPage(pageId: Int): Result<WikiPage> =
        runCatchingCancellable {
            val params = MoodleParams().apply { put("pageid", pageId) }
            val response = JSONObject(callRest("mod_wiki_get_page_contents", params))
            val pageJson = response.optJSONObject("page")
                ?: throw IllegalStateException(MISSING_PAGE_MESSAGE)
            parsePage(pageJson)
        }

    /**
     * Devuelve el contenido original de una página para editarlo.
     *
     * Usa `mod_wiki_get_page_for_editing` **sin** `section`: ese
     * parámetro indica la sección concreta que se quiere editar y, si se
     * envía vacío, Moodle lo interpreta como una sección llamada "" que
     * no existe. El resultado es un contenido vacío y un error
     * `invalidsection` al guardar. Omitirlo significa "página
     * completa". `lockonly = false` pide además el contenido, no solo el
     * bloqueo.
     *
     * Moodle bloquea la página mientras dura la edición; el bloqueo se
     * libera al guardar o al caducar. `lockonly` se envía como `0` (los
     * booleanos de Moodle son `1`/`0`; `false` provoca
     * `invalidparameter`).
     *
     * @param pageId ID de la página.
     * @return El borrador, o el fallo ocurrido.
     */
    override suspend fun getPageDraft(pageId: Int): Result<WikiPageDraft> =
        runCatchingCancellable {
            val params = MoodleParams().apply {
                put("pageid", pageId)
                put("lockonly", false)
            }
            val response = JSONObject(callWrite("mod_wiki_get_page_for_editing", params))
            val sectionJson = response.optJSONObject("pagesection")
                ?: throw IllegalStateException(MISSING_PAGE_MESSAGE)
            WikiPageDraft(
                pageId = pageId,
                content = sectionJson.optString("content"),
            )
        }

    /**
     * Guarda el contenido de una página existente.
     *
     * Como en [getPageDraft], `section` se omite para editar la página
     * completa. Enviarlo vacío haría que Moodle buscase una sección ""
     * y rechazase el guardado con `invalidsection`.
     *
     * @param pageId ID de la página.
     * @param content Contenido en el formato propio de la wiki.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun updatePage(pageId: Int, content: String): Result<Unit> =
        runCatchingCancellable {
            val params = MoodleParams().apply {
                put("pageid", pageId)
                put("content", content)
            }
            callWrite("mod_wiki_edit_page", params)
            Unit
        }

    /**
     * Crea una página nueva.
     *
     * El contenido inicial se envía como HTML (`contentformat = html`),
     * que es el formato con el que la app redacta el texto del editor.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @param title Título de la página.
     * @param content Contenido inicial, en HTML.
     * @return El ID de la página creada, o el fallo ocurrido.
     */
    override suspend fun createPage(
        wikiId: Int,
        title: String,
        content: String,
    ): Result<Int> = runCatchingCancellable {
        val params = MoodleParams().apply {
            put("wikiid", wikiId)
            put("title", title)
            put("content", content)
            put("contentformat", HTML_FORMAT)
        }
        val response = callWrite("mod_wiki_new_page", params)
        val createdPageId = JSONObject(response).optInt("pageid")
        if (createdPageId <= 0) {
            throw IllegalStateException(MISSING_PAGE_MESSAGE)
        }
        createdPageId
    }

    /**
     * Envía una escritura por `POST` traduciendo los rechazos de Moodle.
     *
     * @param function Función de escritura de Moodle.
     * @param params Parámetros ya montados.
     * @return Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si Moodle rechaza la operación.
     */
    private suspend fun callWrite(function: String, params: MoodleParams): String =
        try {
            sessionApi.requireApi().callRESTWithFormBody(function, params.build())
        } catch (exception: MoodleApiException) {
            throw IllegalStateException(exception.message ?: exception.errorCode, exception)
        }

    /**
     * Ejecuta una lectura contra el cliente autenticado.
     *
     * @param function Nombre de la función de Moodle.
     * @param params Parámetros ya montados.
     * @return Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si no hay sesión activa.
     */
    private suspend fun callRest(function: String, params: MoodleParams): String =
        sessionApi.requireApi().callREST(function, params)

    /**
     * Convierte un `JSONObject` de página en [WikiPage].
     *
     * ## Sobre el contenido
     *
     * Se prefiere `cachedcontent` (el HTML ya renderizado por Moodle) y
     * se cae a `content` si no está, para tolerar respuestas de
     * versiones antiguas. En la lista, `cachedcontent` puede no venir
     * porque se pide sin contenido; en ese caso queda vacío y la lista
     * no lo usa.
     *
     * @param pageJson Página devuelta por Moodle.
     * @return La página de dominio.
     */
    private fun parsePage(pageJson: JSONObject): WikiPage = WikiPage(
        id = pageJson.optInt("id"),
        title = pageJson.optString("title"),
        content = pageJson.optString("cachedcontent")
            .ifBlank { pageJson.optString("content") },
        modifiedAt = pageJson.optLong("timemodified", 0L),
        canEdit = pageJson.optBoolean("caneditpage", false),
        isFirstPage = pageJson.optBoolean("firstpage", false),
    )

    private companion object {
        /** `groupid` que significa "el grupo actual". */
        const val CURRENT_GROUP = -1

        /** `userid` que significa "el usuario actual". */
        const val CURRENT_USER = 0

        /** Formato con el que se crean las páginas nuevas. */
        const val HTML_FORMAT = "html"

        /** Mensaje cuando Moodle no devuelve la página esperada. */
        const val MISSING_PAGE_MESSAGE = "Moodle no devolvió la página solicitada"
    }
}
