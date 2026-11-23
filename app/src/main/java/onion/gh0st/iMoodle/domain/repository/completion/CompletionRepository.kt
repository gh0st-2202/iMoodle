package onion.gh0st.iMoodle.domain.repository.completion

import onion.gh0st.iMoodle.domain.model.course.CourseModule

/**
 * Contrato de acceso al seguimiento de finalización de Moodle.
 *
 * ## Qué es la finalización
 *
 * El profesor puede configurar una actividad para que cuente como
 * "completada" cuando el alumno la abre, cuando entrega algo o cuando
 * la marca a mano. Moodle guarda ese estado por alumno y lo usa para
 * calcular el progreso del curso.
 *
 * ## Qué permite este contrato
 *
 * - **Registrar que el alumno ha visto un módulo**
 *   ([markModuleViewed]). Es lo que hace que las actividades con
 *   finalización "al verla" cuenten de verdad.
 * - **Marcar o desmarcar una actividad manual**
 *   ([setModuleCompleted]). Solo funciona si el profesor configuró la
 *   finalización como manual; si es automática, Moodle rechaza la
 *   llamada.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleCompletionRepository` (API REST).
 * - **Tests**: `FakeCompletionRepository`.
 *
 * ## Quién la consume
 *
 * - `ManageModuleCompletion`.
 * - `CourseDetailViewModel`.
 */
interface CompletionRepository {

    /**
     * Avisa a Moodle de que el alumno ha abierto un módulo.
     *
     * ## Por qué importa
     *
     * Las actividades con finalización automática por "ver" solo se
     * completan si la app avisa. Abrir el módulo sin esta llamada deja
     * el progreso del curso parado aunque el alumno haya entrado.
     *
     * ## Manejo de errores
     *
     * No debe romper la navegación: si falla, el módulo se abre igual.
     *
     * @param module Módulo que se ha abierto. De él se usan el nombre
     *   (`modname`) para elegir la función `view` correcta y la instancia
     *   para identificar la actividad.
     * @return `Result` con el fallo si Moodle no pudo registrarlo.
     */
    suspend fun markModuleViewed(module: CourseModule): Result<Unit>

    /**
     * Marca o desmarca a mano una actividad.
     *
     * @param cmId Identificador del módulo en el curso (`cmid`).
     * @param completed `true` para marcarla como completada, `false`
     *   para volver a pendiente.
     * @return `Result` con el fallo si Moodle rechazó el cambio.
     */
    suspend fun setModuleCompleted(cmId: Int, completed: Boolean): Result<Unit>
}
