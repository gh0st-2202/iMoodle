package onion.gh0st.iMoodle.domain.usecase.glossary

import onion.gh0st.iMoodle.domain.repository.glossary.GlossaryRepository
import javax.inject.Inject

/**
 * Caso de uso: crear, modificar y borrar entradas de un glosario.
 *
 * ## Por qué agrupa las tres operaciones
 *
 * Porque las tres modifican el mismo recurso y comparten la misma regla
 * de negocio (delegar en Moodle, que es quien decide los permisos). Es
 * el mismo patrón que `ManageSubmissionComments`: una sola puerta para
 * las escrituras, en lugar de tres casos de uso casi idénticos.
 *
 * ## Qué NO hace
 *
 * No refresca la lista después de escribir. Eso es decisión del
 * ViewModel: tras un guardado correcto vuelve a pedir las entradas para
 * que la pantalla muestre el resultado real del servidor.
 *
 * ## Quién lo usa
 *
 * - `GlossaryViewModel`.
 *
 * @param repository Contrato de acceso a glosarios.
 */
class ManageGlossaryEntries @Inject constructor(
    private val repository: GlossaryRepository,
) {

    /**
     * Añade una entrada al glosario.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @param concept Término de la entrada.
     * @param definition Definición en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun add(
        glossaryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> = repository.addEntry(glossaryId, concept, definition)

    /**
     * Modifica una entrada existente.
     *
     * @param entryId ID de la entrada.
     * @param concept Término actualizado.
     * @param definition Definición actualizada, en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun update(
        entryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit> = repository.updateEntry(entryId, concept, definition)

    /**
     * Borra una entrada.
     *
     * @param entryId ID de la entrada.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun delete(entryId: Int): Result<Unit> = repository.deleteEntry(entryId)
}
