package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.repository.completion.CompletionRepository
import javax.inject.Inject

/**
 * Caso de uso: gestionar la finalización de una actividad.
 *
 * ## Qué operaciones ofrece
 *
 * - [markViewed]: avisa a Moodle de que el alumno ha abierto el
 *   módulo. Se llama justo antes de navegar a su pantalla.
 * - [setCompleted]: marca o desmarca a mano una actividad.
 *
 * ## Por qué ambos devuelven `Result`
 *
 * La finalización no debe bloquear la navegación ni mostrar errores
 * que impidan usar la app: si falla, el alumno sigue pudiendo abrir la
 * actividad. El ViewModel decide si conviene avisar o ignorar.
 *
 * ## Quién lo usa
 *
 * - `CourseDetailViewModel`.
 *
 * @param completionRepository Contrato de finalización.
 */
class ManageModuleCompletion @Inject constructor(
    private val completionRepository: CompletionRepository,
) {

    /**
     * Registra que el alumno ha visto un módulo.
     *
     * @param module Módulo que se va a abrir.
     * @return `Result` con el fallo si Moodle no pudo registrarlo.
     */
    suspend fun markViewed(module: CourseModule): Result<Unit> =
        completionRepository.markModuleViewed(module)

    /**
     * Marca o desmarca a mano una actividad.
     *
     * @param cmId Identificador del módulo en el curso.
     * @param completed `true` para completar, `false` para desmarcar.
     * @return `Result` con el fallo si Moodle rechazó el cambio.
     */
    suspend fun setCompleted(cmId: Int, completed: Boolean): Result<Unit> =
        completionRepository.setModuleCompleted(cmId, completed)
}
