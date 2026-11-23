package onion.gh0st.iMoodle.domain.usecase.pasen

import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.repository.pasen.PasenGradeRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener las calificaciones de iPasen.
 *
 * ## Qué hace
 *
 * Delega en [PasenGradeRepository], sin reglas adicionales: la única
 * decisión de negocio (qué matrícula usar) vive en el repositorio.
 *
 * @param repository Contrato de acceso a las calificaciones.
 */
class GetPasenGrades @Inject constructor(
    private val repository: PasenGradeRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * @param forceRefresh Si `true`, ignora la caché.
     * @return Lista de convocatorias.
     */
    suspend operator fun invoke(forceRefresh: Boolean = false): List<PasenConvocatoria> =
        repository.getGrades(forceRefresh)
}
