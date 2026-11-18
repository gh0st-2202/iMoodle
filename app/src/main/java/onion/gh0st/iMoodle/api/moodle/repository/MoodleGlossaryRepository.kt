package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiException
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.domain.repository.glossary.GlossaryRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [GlossaryRepository] sobre la API REST de
 * Moodle.
 *
 * ## Qué hace
 *
 * Habla con `mod_glossary` para leer y escribir las entradas de un
 * glosario:
 *
 * - `mod_glossary_get_entries_by_letter` con `letter = ALL` para traer
 *   todas las entradas (paginando de [PAGE_SIZE] en [PAGE_SIZE]).
 * - `mod_glossary_add_entry` y `mod_glossary_update_entry` para crear y
 *   modificar, siempre con `definitionformat = 1` (HTML).
 * - `mod_glossary_delete_entry` para borrar.
 *
 * ## Sobre las escrituras
 *
 * Se envían por `POST` (`callRESTWithFormBody`) porque la definición
 * puede ser larga y no conviene meterla en la URL. Además, cuando
 * Moodle rechaza la operación (por permisos, por contenido duplicado…)
 * su mensaje se convierte en `IllegalStateException` para que la UI
 * pueda enseñarlo sin conocer la capa de red (ver [callWrite]).
 *
 * ## Qué NO hace
 *
 * - **No cachea**: los glosarios cambian y una entrada editada debe
 *   verse al instante.
 * - **No gestiona adjuntos ni categorías**: la app solo edita concepto
 *   y definición.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 */
@Singleton
class MoodleGlossaryRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : GlossaryRepository {

    /**
     * Devuelve todas las entradas del glosario.
     *
     * ## Paginación
     *
     * Pide bloques de [PAGE_SIZE] hasta reunir el `count` que informa
     * Moodle (o hasta que un bloque venga vacío). [MAX_ENTRIES] es un
     * tope de seguridad para no encadenar peticiones sin fin si el
     * servidor devuelve un `count` incoherente.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @return Las entradas, o el fallo ocurrido.
     */
    override suspend fun getEntries(glossaryId: Int): Result<List<GlossaryEntry>> =
        runCatchingCancellable {
            val entries = mutableListOf<GlossaryEntry>()
            var offset = 0
            var total = 0

            do {
                val response = JSONObject(
                    callRest("mod_glossary_get_entries_by_letter", entriesParams(glossaryId, offset)),
                )
                total = response.optInt("count", 0)
                val pageEntries = response.optJSONArray("entries").objects()
                entries += pageEntries.map { entryJson -> parseEntry(entryJson) }
                offset += PAGE_SIZE
            } while (pageEntries.isNotEmpty() && entries.size < total && entries.size < MAX_ENTRIES)

            entries
        }

    /**
     * Crea una entrada nueva.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @param concept Término de la entrada.
     * @param definition Definición en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun addEntry(
        glossaryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> = runCatchingCancellable {
        val params = entryParams(concept, definition).apply { put("glossaryid", glossaryId) }
        callWrite("mod_glossary_add_entry", params)
        Unit
    }

    /**
     * Modifica una entrada existente.
     *
     * @param entryId ID de la entrada.
     * @param concept Término actualizado.
     * @param definition Definición actualizada, en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun updateEntry(
        entryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> = runCatchingCancellable {
        val params = entryParams(concept, definition).apply { put("id", entryId) }
        callWrite("mod_glossary_update_entry", params)
        Unit
    }

    /**
     * Borra una entrada.
     *
     * @param entryId ID de la entrada.
     * @return Éxito, o el fallo ocurrido.
     */
    override suspend fun deleteEntry(entryId: Int): Result<Unit> = runCatchingCancellable {
        val params = MoodleParams().apply { put("id", entryId) }
        callWrite("mod_glossary_delete_entry", params)
        Unit
    }

    /**
     * Construye los parámetros de una página de entradas.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @param offset Índice de la primera entrada que se quiere.
     * @return Parámetros listos para `mod_glossary_get_entries_by_letter`.
     */
    private fun entriesParams(glossaryId: Int, offset: Int): MoodleParams =
        MoodleParams().apply {
            put("id", glossaryId)
            put("letter", "ALL")
            put("from", offset)
            put("limit", PAGE_SIZE)
        }

    /**
     * Construye los parámetros comunes de creación y edición.
     *
     * @param concept Término de la entrada.
     * @param definition Definición en HTML.
     * @return Parámetros con el formato de definición ya fijado.
     */
    private fun entryParams(concept: String, definition: String): MoodleParams =
        MoodleParams().apply {
            put("concept", concept)
            put("definition", definition)
            put("definitionformat", HTML_FORMAT)
        }

    /**
     * Envía una escritura por `POST` traduciendo los rechazos de Moodle.
     *
     * ## Por qué traduce la excepción
     *
     * La UI no puede depender de `MoodleApiException` (vive en `api/`).
     * Cuando Moodle explica el motivo del rechazo (por ejemplo, "no
     * tienes permiso para editar esta entrada"), se convierte en
     * `IllegalStateException` con ese texto, que es lo que el ViewModel
     * ya sabe mostrar. Los fallos de red se propagan tal cual.
     *
     * @param function Función de escritura de Moodle.
     * @param params Parámetros ya montados.
     * @throws IllegalStateException si Moodle rechaza la operación.
     */
    private suspend fun callWrite(function: String, params: MoodleParams) {
        try {
            sessionApi.requireApi().callRESTWithFormBody(function, params.build())
        } catch (exception: MoodleApiException) {
            throw IllegalStateException(exception.message ?: exception.errorCode, exception)
        }
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
     * Convierte un `JSONObject` de entrada en [GlossaryEntry].
     *
     * @param entryJson Entrada devuelta por Moodle.
     * @return La entrada de dominio.
     */
    private fun parseEntry(entryJson: JSONObject): GlossaryEntry {
        val createdAt = entryJson.optLong("timecreated", 0L)
        return GlossaryEntry(
            id = entryJson.optInt("id"),
            concept = entryJson.optString("concept"),
            definition = entryJson.optString("definition"),
            author = entryJson.optString("userfullname").ifBlank { UNKNOWN_AUTHOR },
            createdAt = createdAt,
            updatedAt = entryJson.optLong("timemodified", createdAt),
            approved = entryJson.optBoolean("approved", true),
        )
    }

    private companion object {
        /** Entradas que se piden por página. */
        const val PAGE_SIZE = 100

        /** Tope de seguridad de entradas que se acumulan. */
        const val MAX_ENTRIES = 500

        /** Formato HTML de Moodle (`definitionformat`). */
        const val HTML_FORMAT = 1

        /** Autor mostrado cuando Moodle no informa del nombre. */
        const val UNKNOWN_AUTHOR = "Desconocido"
    }
}
