package onion.gh0st.iMoodle.domain.usecase.wiki

import onion.gh0st.iMoodle.domain.model.wiki.WikiPageDraft
import onion.gh0st.iMoodle.domain.repository.wiki.WikiRepository
import javax.inject.Inject

/**
 * Caso de uso: editar y crear páginas de una wiki.
 *
 * ## Por qué agrupa las escrituras
 *
 * Porque comparten el mismo recurso y la misma responsabilidad: enviar
 * contenido a Moodle, que es quien valida permisos y formato. Es el
 * mismo patrón que `ManageSubmissionComments` y `ManageGlossaryEntries`.
 *
 * ## Lectura del borrador
 *
 * [getDraft] no es una escritura, pero vive aquí porque solo tiene
 * sentido como paso previo a [update]: el editor necesita el contenido
 * original y no el renderizado.
 *
 * ## Quién lo usa
 *
 * - `WikiPageViewModel` (borrador y guardado).
 * - `WikiViewModel` (páginas nuevas).
 *
 * @param repository Contrato de acceso a wikis.
 */
class ManageWikiPage @Inject constructor(
    private val repository: WikiRepository,
) {

    /**
     * Obtiene el contenido original de una página para editarla.
     *
     * @param pageId ID de la página.
     * @return El borrador, o el fallo ocurrido.
     */
    suspend fun getDraft(pageId: Int): Result<WikiPageDraft> =
        repository.getPageDraft(pageId)

    /**
     * Guarda el contenido de una página existente.
     *
     * @param pageId ID de la página.
     * @param content Contenido en el formato propio de la wiki.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun update(pageId: Int, content: String): Result<Unit> =
        repository.updatePage(pageId, content)

    /**
     * Crea una página nueva.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @param title Título de la página.
     * @param content Contenido inicial, en HTML.
     * @return El ID de la página creada, o el fallo ocurrido.
     */
    suspend fun create(wikiId: Int, title: String, content: String): Result<Int> =
        repository.createPage(wikiId, title, content)
}
