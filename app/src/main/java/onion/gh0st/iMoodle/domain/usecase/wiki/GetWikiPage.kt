package onion.gh0st.iMoodle.domain.usecase.wiki

import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.repository.wiki.WikiRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener el contenido de una página de wiki.
 *
 * ## Qué devuelve
 *
 * La página con su contenido **renderizado** y la marca `canEdit` que
 * envía Moodle. No devuelve el contenido original: para eso está
 * `ManageWikiPage.getDraft`.
 *
 * ## Quién lo usa
 *
 * - `WikiPageViewModel`.
 *
 * @param repository Contrato de acceso a wikis.
 */
class GetWikiPage @Inject constructor(
    private val repository: WikiRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param pageId ID de la página.
     * @return La página, o el fallo ocurrido.
     */
    suspend operator fun invoke(pageId: Int): Result<WikiPage> =
        repository.getPage(pageId)
}
