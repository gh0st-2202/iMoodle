package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.repository.completion.CompletionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [CompletionRepository] sobre la API REST de
 * Moodle.
 *
 * ## Funciones que usa
 *
 * - La función `view` de cada módulo (`mod_assign_view_assign`,
 *   `mod_forum_view_forum`…) para registrar el «visto» y actualizar la
 *   finalización automática.
 * - `core_completion_update_activity_completion_status_manually` para
 *   marcar o desmarcar una actividad manual.
 *
 * ## Por qué no hay una función genérica de «ver módulo»
 *
 * Porque Moodle no la tiene: cada plugin expone su propio `view_*`, que
 * es el que dispara el evento y la condición de finalización por
 * «ver». La app traduce el nombre del módulo a su función y su
 * parámetro.
 *
 * ## Por qué los fallos no se propagan
 *
 * La finalización es información secundaria: si Moodle rechaza el
 * cambio (por ejemplo, porque el profesor la configuró como
 * automática), la pantalla debe seguir funcionando. Por eso ambos
 * métodos devuelven `Result` en lugar de lanzar.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 */
@Singleton
class MoodleCompletionRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : CompletionRepository {

    /**
     * Registra que el alumno ha abierto un módulo.
     *
     * Si el módulo no tiene una función `view` conocida (por ejemplo,
     * una etiqueta), no se llama a Moodle y se devuelve éxito: no hay
     * nada que registrar.
     *
     * @param module Módulo que se ha abierto.
     * @return `Result.success(Unit)` si Moodle lo aceptó.
     */
    override suspend fun markModuleViewed(module: CourseModule): Result<Unit> =
        runCatchingCancellable {
            val viewRequest = viewRequestFor(module) ?: return@runCatchingCancellable Unit
            val requestParams = MoodleParams().apply {
                put(viewRequest.instanceParam, module.instance)
                if (module.modname == "glossary") put("mode", GLOSSARY_MODE)
            }
            sessionApi.requireApi().callREST(viewRequest.function, requestParams)
            Unit
        }

    /**
     * Marca o desmarca a mano una actividad.
     *
     * Moodle espera `completed` como booleano estricto; [MoodleParams]
     * lo convierte a `1`/`0`.
     *
     * @param cmId Identificador del módulo en el curso.
     * @param completed `true` para completar, `false` para desmarcar.
     * @return `Result.success(Unit)` si Moodle lo aceptó.
     */
    override suspend fun setModuleCompleted(cmId: Int, completed: Boolean): Result<Unit> =
        runCatchingCancellable {
            val requestParams = MoodleParams().apply {
                put("cmid", cmId)
                put("completed", completed)
            }
            sessionApi.requireApi().callREST(
                "core_completion_update_activity_completion_status_manually",
                requestParams,
            )
            Unit
        }

    /**
     * Traduce un módulo a la función `view` que le corresponde.
     *
     * @param module Módulo abierto.
     * @return La función y el nombre de su parámetro, o `null` si el
     *   módulo no tiene una vista registrable.
     */
    private fun viewRequestFor(module: CourseModule): ViewRequest? = when (module.modname) {
        "assign" -> ViewRequest("mod_assign_view_assign", "assignid")
        "forum" -> ViewRequest("mod_forum_view_forum", "forumid")
        "glossary" -> ViewRequest("mod_glossary_view_glossary", "id")
        "wiki" -> ViewRequest("mod_wiki_view_wiki", "wikiid")
        "resource" -> ViewRequest("mod_resource_view_resource", "resourceid")
        "folder" -> ViewRequest("mod_folder_view_folder", "folderid")
        "url" -> ViewRequest("mod_url_view_url", "urlid")
        "page" -> ViewRequest("mod_page_view_page", "pageid")
        "book" -> ViewRequest("mod_book_view_book", "bookid")
        else -> null
    }

    /**
     * Función `view` de Moodle y nombre del parámetro que recibe el ID
     * de la instancia del módulo.
     *
     * @property function Nombre del web service.
     * @property instanceParam Nombre del parámetro del ID.
     */
    private data class ViewRequest(
        val function: String,
        val instanceParam: String,
    )

    private companion object {
        /** Modo con el que se registra la visita a un glosario. */
        const val GLOSSARY_MODE = "letter"
    }
}
