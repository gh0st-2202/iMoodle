package onion.gh0st.iMoodle.domain.repository.glossary

import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry

/**
 * Contrato de acceso a los glosarios de Moodle (`mod_glossary`).
 *
 * ## Operaciones
 *
 * - **Leer**: [getEntries] devuelve todas las entradas del glosario.
 * - **Escribir**: [addEntry], [updateEntry] y [deleteEntry] cubren el
 *   ciclo completo de una entrada.
 *
 * ## Sobre los permisos
 *
 * Moodle decide quién puede escribir: el alumnado suele poder crear y
 * editar sus propias entradas, y el profesorado, todas. La app no
 * duplica esas reglas; si el servidor rechaza una operación, devuelve
 * el fallo con el motivo y la UI lo muestra.
 *
 * ## Por qué devuelve `Result`
 *
 * Porque son operaciones que el usuario provoca y cuyo fallo hay que
 * enseñar sin romper la pantalla. Igual que en
 * `SubmissionCommentsRepository`, el resultado viaja como `Result` y el
 * ViewModel decide qué mensaje mostrar.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleGlossaryRepository`.
 * - **Tests**: `FakeGlossaryRepository`.
 *
 * ## Quién la consume
 *
 * - `GetGlossaryEntries`.
 * - `ManageGlossaryEntries`.
 */
interface GlossaryRepository {

    /**
     * Devuelve todas las entradas del glosario, ordenadas por concepto.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @return Las entradas, o el fallo ocurrido.
     */
    suspend fun getEntries(glossaryId: Int): Result<List<GlossaryEntry>>

    /**
     * Crea una entrada nueva.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @param concept Término de la entrada.
     * @param definition Definición en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun addEntry(
        glossaryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit>

    /**
     * Modifica una entrada existente.
     *
     * @param entryId ID de la entrada.
     * @param concept Término actualizado.
     * @param definition Definición actualizada, en HTML.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun updateEntry(
        entryId: Int,
        concept: String,
        definition: String,
    ): Result<Unit>

    /**
     * Borra una entrada.
     *
     * @param entryId ID de la entrada.
     * @return Éxito, o el fallo ocurrido.
     */
    suspend fun deleteEntry(entryId: Int): Result<Unit>
}
