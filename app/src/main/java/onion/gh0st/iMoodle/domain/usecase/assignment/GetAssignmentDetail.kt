package onion.gh0st.iMoodle.domain.usecase.assignment

import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener el detalle completo de una tarea.
 *
 * ## Qué es un "caso de uso"
 *
 * Un caso de uso es una operación **de negocio** que la app puede
 * hacer. Representa una acción que tiene sentido para el usuario, no
 * un detalle técnico. Por ejemplo: "obtener el detalle de una tarea",
 * "listar los cursos", "sincronizar los recordatorios".
 *
 * Los casos de uso viven en `domain/usecase/` y son el punto de
 * entrada del dominio. Los ViewModels los llaman, y ellos a su vez
 * llaman a los repositorios.
 *
 * ## Qué ventaja tienen
 *
 * A primera vista, un caso de uso que solo delega en el repositorio
 * puede parecer una capa innecesaria. Pero tiene tres ventajas:
 *
 * 1. **Encapsulación de lógica**: si mañana hay que añadir lógica
 *    (por ejemplo, "cachear el resultado", "combinar con otra
 *    fuente", "validar antes de llamar"), se añade aquí sin tocar
 *    el repositorio ni el ViewModel.
 * 2. **Claridad para la UI**: el ViewModel no necesita saber si
 *    detrás hay uno o varios repositorios. Solo llama a un caso de
 *    uso.
 * 3. **Testeabilidad**: se puede testear el caso de uso de forma
 *    aislada, con un repositorio fake.
 *
 * ## Qué hace este caso de uso en concreto
 *
 * Encapsula la llamada a `AssignmentRepository.getAssignmentDetail`,
 * y añade una capa de significado: si el repositorio devuelve `null`
 * (la tarea no existe), este caso de uso lanza un error tipado
 * (`AssignmentNotFoundException`) en lugar de devolver `null`.
 *
 * ¿Por qué? Porque "la tarea no existe" es un error de negocio, no
 * un estado válido. Si el ViewModel recibiera `null` y no supiera
 * distinguirlo de "no se ha cargado todavía" o "ha fallado la red",
 * sería muy confuso. Con el error tipado, el ViewModel puede tratarlo
 * como lo que es: un error a mostrar al usuario.
 *
 * @param repository Contrato de acceso a las tareas.
 */
class GetAssignmentDetail @Inject constructor(
    private val repository: AssignmentRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Qué hace
     *
     * Llama al repositorio. Si devuelve `null`, lanza
     * `AssignmentNotFoundException`. Si devuelve un detalle, lo
     * devuelve tal cual.
     *
     * ## Manejo de errores
     *
     * - **Tarea no encontrada**: lanza `AssignmentNotFoundException`.
     * - **Error de red o de Moodle**: se propaga tal cual (el
     *   ViewModel lo captura y muestra un mensaje genérico).
     *
     * @param courseId ID del curso.
     * @param assignmentId ID (o `cmid`) de la tarea.
     * @return El detalle de la tarea.
     * @throws AssignmentNotFoundException si la tarea no existe.
     */
    suspend operator fun invoke(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail =
        repository.getAssignmentDetail(courseId, assignmentId)
            ?: throw AssignmentNotFoundException(courseId, assignmentId)

    /**
     * Error de negocio: la tarea solicitada no existe.
     *
     * ## Cuándo se lanza
     *
     * Cuando el repositorio devuelve `null`, lo que significa que la
     * tarea no está en el curso (porque fue borrada, porque el
     * `assignmentId` no es válido, o porque el alumno ya no está
     * matriculado).
     *
     * ## Por qué es una excepción y no un `null`
     *
     * Porque "la tarea no existe" es un estado de error, no un
     * estado válido. Al lanzar una excepción, el ViewModel está
     * obligado a tratarlo (no puede olvidarse). Con `null`, podría
     * pasar desapercibido.
     *
     * @property courseId Curso consultado.
     * @property assignmentId ID de la tarea que no se encontró.
     */
    class AssignmentNotFoundException(
        courseId: Int,
        assignmentId: Int,
    ) : Exception("Tarea $assignmentId no encontrada en el curso $courseId")
}