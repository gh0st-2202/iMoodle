package onion.gh0st.iMoodle.domain.repository.wiki

import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft

/**
 * Contrato de acceso a las wikis de Moodle (`mod_wiki`).
 *
 * ## Operaciones
 *
 * - **Leer**: [getPages] lista las páginas y [getPage] devuelve el
 *   contenido de una.
 * - **Editar**: [getPageDraft] obtiene el contenido original y
 *   [updatePage] lo guarda.
 * - **Crear**: [createPage] añade una página nueva.
 *
 * ## Sobre los permisos
 *
 * Moodle informa en cada página de si el usuario puede editarla
 * (`canEdit`), así que la app enseña u oculta la acción según ese dato.
 * Si aun así el servidor rechaza un guardado, se devuelve su motivo.
 *
 * ## Por qué hay dos lecturas
 *
 * [getPage] devuelve el contenido renderizado (para mostrar). Para
 * editar hace falta el contenido original en el formato de la wiki, y
 * eso solo lo da [getPageDraft]; ver `WikiPageDraft`.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleWikiRepository`.
 * - **Tests**: `FakeWikiRepository`.
 *
 * ## Quién la consume
 *
 * - `GetWikiPages`.
 * - `GetWikiPage`.
 * - `ManageWikiPage`.
 */
interface WikiRepository {

    /**
     * Lista las páginas de la wiki.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @return Las páginas, ordenadas por título, o el fallo ocurrido.
     */
    suspend fun getPages(wikiId: Int): Result<List<WikiPage>>

    /**
     * Devuelve el contenido de una página, ya renderizado.
     *
     * @param pageId ID de la página.
     * @return La página, o el fallo ocurrido.
     */
    suspend fun getPage(pageId: Int): Result<WikiPage>

    /**
     * Devuelve el contenido original de una página para editarlo.
     *
     * @param pageId ID de la página.
     * @return El borrador con el contenido original, o el fallo ocurrido.
     */
    suspend fun getPageDraft(pageId: Int): Result<WikiPageDraft>

    /**
     * Guarda el contenido de una página existente.
     *
     * @param pageId ID de la página.
     * @param content Contenido en el formato propio de la wiki.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun updatePage(pageId: Int, content: String): Result<Unit>

    /**
     * Crea una página nueva en la wiki.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @param title Título de la página.
     * @param content Contenido inicial, en HTML.
     * @return El ID de la página creada, o el fallo ocurrido.
     */
    suspend fun createPage(
        wikiId: Int,
        title: String,
        content: String,
    ): Result<Int>
}
