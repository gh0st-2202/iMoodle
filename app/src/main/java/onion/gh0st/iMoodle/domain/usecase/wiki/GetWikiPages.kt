package onion.gh0st.iMoodle.domain.usecase.wiki

import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.domain.repository.wiki.WikiRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener las páginas de una wiki.
 *
 * ## Qué hace
 *
 * Delega en `WikiRepository.getPages`, que devuelve las páginas
 * ordenadas por título. La portada no se separa de las demás: la UI la
 * marca con una insignia usando `WikiPage.isFirstPage`.
 *
 * ## Quién lo usa
 *
 * - `WikiViewModel`.
 *
 * @param repository Contrato de acceso a wikis.
 */
class GetWikiPages @Inject constructor(
    private val repository: WikiRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param wikiId ID de la instancia de la wiki.
     * @return Las páginas, o el fallo ocurrido.
     */
    suspend operator fun invoke(wikiId: Int): Result<List<WikiPage>> =
        repository.getPages(wikiId)
}
