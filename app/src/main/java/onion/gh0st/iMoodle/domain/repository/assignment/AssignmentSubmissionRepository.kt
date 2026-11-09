package onion.gh0st.iMoodle.domain.repository.assignment

import onion.gh0st.iMoodle.domain.model.assignment.AssignmentSubmission

/**
 * El contrato para entregar y borrar tareas.
 *
 * ## ¿Qué es un contrato?
 * Imagina que es un menú de restaurante. Solo dice QUÉ se puede pedir (entregar, borrar),
 * pero no dice CÓMO se cocina. El "cocinero" real (la capa de datos) decidirá si usar Moodle,
 * una base de datos o palomas mensajeras.
 */
interface AssignmentSubmissionRepository {

    /**
     * Envía los deberes a Moodle.
     *
     * @param submission El formulario con los archivos y el texto.
     * @return Un resultado. Si va bien, contiene `Unit` (que significa "éxito sin datos extra"). Si va mal, contiene el error.
     */
    suspend fun submit(submission: AssignmentSubmission): Result<Unit>

    /**
     * Borra la entrega que ya has hecho. Es como si arrancaras la hoja del cuaderno y la tiraras a la basura.
     *
     * @param assignmentId El número de la tarea.
     * @param userId El número de tu usuario (para que Moodle sepa de quién es la entrega).
     * @return Un resultado indicando si se borró correctamente.
     */
    suspend fun deleteSubmission(assignmentId: Int, userId: Int): Result<Unit>
}