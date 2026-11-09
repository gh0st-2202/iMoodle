package onion.gh0st.iMoodle.domain.usecase.assignment

import onion.gh0st.iMoodle.domain.model.assignment.AssignmentSubmission
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository
import javax.inject.Inject

/**
 * El "gerente" que gestiona el envío de una tarea.
 *
 * ## ¿Qué hace?
 * Es un paso intermedio entre el cerebro de la pantalla (ViewModel) y el trabajador de Moodle (Repository).
 * Simplemente recoge los datos (texto, archivos nuevos, archivos viejos) y los empaqueta
 * en un `AssignmentSubmission` para que el repositorio lo entienda.
 */
class SubmitAssignment @Inject constructor(
    private val repository: AssignmentSubmissionRepository,
) {
    suspend operator fun invoke(
        assignmentId: Int,
        courseId: Int,
        cmId: Int,
        onlineText: String,
        fileUris: List<String>,
        existingFileUrls: List<String> = emptyList(),
    ): Result<Unit> = repository.submit(
        AssignmentSubmission(
            assignmentId = assignmentId,
            courseId = courseId,
            cmId = cmId,
            onlineText = onlineText,
            newFileUris = fileUris,
            existingFileUrls = existingFileUrls,
        ),
    )
}