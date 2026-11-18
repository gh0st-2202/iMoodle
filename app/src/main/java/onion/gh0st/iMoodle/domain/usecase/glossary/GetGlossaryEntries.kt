package onion.gh0st.iMoodle.domain.usecase.glossary

import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.domain.repository.glossary.GlossaryRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener las entradas de un glosario.
 *
 * ## Qué hace
 *
 * Delega en `GlossaryRepository.getEntries`. El repositorio ya devuelve
 * las entradas ordenadas por concepto, así que este caso de uso no las
 * reordena: solo es el punto de entrada del dominio para que el
 * ViewModel no dependa del repositorio.
 *
 * ## Quién lo usa
 *
 * - `GlossaryViewModel`.
 *
 * @param repository Contrato de acceso a glosarios.
 */
class GetGlossaryEntries @Inject constructor(
    private val repository: GlossaryRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param glossaryId ID de la instancia del glosario.
     * @return Las entradas, o el fallo ocurrido.
     */
    suspend operator fun invoke(glossaryId: Int): Result<List<GlossaryEntry>> =
        repository.getEntries(glossaryId)
}
