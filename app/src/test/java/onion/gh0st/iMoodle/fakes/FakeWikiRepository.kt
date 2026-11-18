package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.domain.repository.wiki.WikiRepository

/**
 * Implementación en memoria de [WikiRepository] para tests.
 *
 * Devuelve los datos configurados y guarda las escrituras recibidas,
 * sin red ni parsing de JSON.
 */
class FakeWikiRepository : WikiRepository {

    /** Páginas a devolver por [getPages]. */
    var pages: List<WikiPage> = emptyList()

    /** Si es no-`null`, [getPages] lo devuelve como fallo. */
    var pagesError: Throwable? = null

    /** Página a devolver por [getPage]. */
    var page: WikiPage? = null

    /** Si es no-`null`, [getPage] lo devuelve como fallo. */
    var pageError: Throwable? = null

    /** Borrador a devolver por [getPageDraft]. */
    var draft: WikiPageDraft? = null

    /** Si es no-`null`, [getPageDraft] lo devuelve como fallo. */
    var draftError: Throwable? = null

    /** Si es no-`null`, las escrituras lo devuelven como fallo. */
    var writeError: Throwable? = null

    /** ID que devuelve [createPage] cuando todo va bien. */
    var createdPageId: Int = 42

    /** Último contenido guardado con [updatePage], o `null`. */
    var lastUpdatedContent: String? = null
        private set

    /** ID de la última página actualizada con [updatePage], o `null`. */
    var lastUpdatedPageId: Int? = null
        private set

    /** Última llamada a [createPage], o `null`. */
    var lastCreate: CreatedPage? = null
        private set

    /**
     * Devuelve [pages] o [pagesError].
     *
     * @param wikiId ID de la wiki (ignorado por el fake).
     * @return El resultado configurado.
     */
    override suspend fun getPages(wikiId: Int): Result<List<WikiPage>> =
        pagesError?.let { error -> Result.failure(error) } ?: Result.success(pages)

    /**
     * Devuelve [page] o [pageError].
     *
     * @param pageId ID de la página (ignorado por el fake).
     * @return El resultado configurado.
     */
    override suspend fun getPage(pageId: Int): Result<WikiPage> =
        pageError?.let { error -> Result.failure(error) }
            ?: page?.let { loadedPage -> Result.success(loadedPage) }
            ?: Result.failure(IllegalStateException("Página no configurada"))

    /**
     * Devuelve [draft] o [draftError].
     *
     * @param pageId ID de la página (ignorado por el fake).
     * @return El resultado configurado.
     */
    override suspend fun getPageDraft(pageId: Int): Result<WikiPageDraft> =
        draftError?.let { error -> Result.failure(error) }
            ?: draft?.let { loadedDraft -> Result.success(loadedDraft) }
            ?: Result.failure(IllegalStateException("Borrador no configurado"))

    /**
     * Registra la edición y devuelve el resultado configurado.
     *
     * @param pageId ID de la página.
     * @param content Contenido enviado.
     * @return Éxito, o [writeError].
     */
    override suspend fun updatePage(pageId: Int, content: String): Result<Unit> {
        lastUpdatedPageId = pageId
        lastUpdatedContent = content
        return writeError?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    }

    /**
     * Registra la creación y devuelve el resultado configurado.
     *
     * @param wikiId ID de la wiki.
     * @param title Título de la página.
     * @param content Contenido inicial.
     * @return El ID configurado, o [writeError].
     */
    override suspend fun createPage(
        wikiId: Int,
        title: String,
        content: String,
    ): Result<Int> {
        lastCreate = CreatedPage(wikiId, title, content)
        return writeError?.let { error -> Result.failure(error) }
            ?: Result.success(createdPageId)
    }

    /**
     * Datos de una llamada a [createPage].
     *
     * @property wikiId ID de la wiki.
     * @property title Título enviado.
     * @property content Contenido enviado.
     */
    data class CreatedPage(
        val wikiId: Int,
        val title: String,
        val content: String,
    )
}
