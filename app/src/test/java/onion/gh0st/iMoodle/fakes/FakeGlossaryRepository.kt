package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.domain.repository.glossary.GlossaryRepository

/**
 * Implementación en memoria de [GlossaryRepository] para tests.
 *
 * Devuelve los datos configurados y guarda las escrituras recibidas,
 * sin red ni parsing de JSON.
 */
class FakeGlossaryRepository : GlossaryRepository {

    /** Entradas a devolver por [getEntries]. */
    var entries: List<GlossaryEntry> = emptyList()

    /** Si es no-`null`, [getEntries] lo devuelve como fallo. */
    var entriesError: Throwable? = null

    /** Si es no-`null`, las escrituras lo devuelven como fallo. */
    var writeError: Throwable? = null

    /** Última llamada a [addEntry], o `null` si no se ha llamado. */
    var lastAdd: AddedEntry? = null
        private set

    /** Última llamada a [updateEntry], o `null` si no se ha llamado. */
    var lastUpdate: UpdatedEntry? = null
        private set

    /** Último ID borrado con [deleteEntry], o `null` si no se ha llamado. */
    var lastDeletedId: Int? = null
        private set

    /**
     * Devuelve [entries] o [entriesError].
     *
     * @param glossaryId ID del glosario (ignorado por el fake).
     * @return El resultado configurado.
     */
    override suspend fun getEntries(glossaryId: Int): Result<List<GlossaryEntry>> =
        entriesError?.let { error -> Result.failure(error) } ?: Result.success(entries)

    /**
     * Registra la creación y devuelve el resultado configurado.
     *
     * @param glossaryId ID del glosario.
     * @param concept Término de la entrada.
     * @param definition Definición de la entrada.
     * @return Éxito, o [writeError].
     */
    override suspend fun addEntry(
        glossaryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> {
        lastAdd = AddedEntry(glossaryId, concept, definition)
        return writeError?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    }

    /**
     * Registra la modificación y devuelve el resultado configurado.
     *
     * @param entryId ID de la entrada.
     * @param concept Término actualizado.
     * @param definition Definición actualizada.
     * @return Éxito, o [writeError].
     */
    override suspend fun updateEntry(
        entryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> {
        lastUpdate = UpdatedEntry(entryId, concept, definition)
        return writeError?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    }

    /**
     * Registra el borrado y devuelve el resultado configurado.
     *
     * @param entryId ID de la entrada.
     * @return Éxito, o [writeError].
     */
    override suspend fun deleteEntry(entryId: Int): Result<Unit> {
        lastDeletedId = entryId
        return writeError?.let { error -> Result.failure(error) } ?: Result.success(Unit)
    }

    /**
     * Datos de una llamada a [addEntry].
     *
     * @property glossaryId ID del glosario.
     * @property concept Término enviado.
     * @property definition Definición enviada.
     */
    data class AddedEntry(
        val glossaryId: Int,
        val concept: String,
        val definition: String,
    )

    /**
     * Datos de una llamada a [updateEntry].
     *
     * @property entryId ID de la entrada.
     * @property concept Término enviado.
     * @property definition Definición enviada.
     */
    data class UpdatedEntry(
        val entryId: Int,
        val concept: String,
        val definition: String,
    )
}
