package onion.gh0st.iMoodle.domain.repository.assignment

import onion.gh0st.iMoodle.domain.model.assignment.Assignment
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail

/**
 * Contrato de acceso a las tareas de Moodle.
 *
 * ## Qué es un "contrato" en este contexto
 *
 * En arquitectura limpia, un contrato (o interfaz) describe **qué**
 * operaciones se pueden hacer, sin decir **cómo**. La implementación
 * real vive en otra capa (`api/moodle/repository/MoodleAssignmentRepository`)
 * y se encarga de los detalles: hacer peticiones HTTP, parsear JSON,
 * etc.
 *
 * Esta separación tiene tres ventajas:
 *
 * 1. **El dominio no depende de la infraestructura**: los
 *    ViewModels y casos de uso dependen de esta interfaz, no de la
 *    implementación. Si mañana se cambia Moodle por otra plataforma,
 *    solo hay que cambiar la implementación, no los consumidores.
 * 2. **Testeabilidad**: en tests se puede sustituir por un fake que
 *    devuelva datos controlados, sin hacer peticiones reales.
 * 3. **Claridad**: al leer la interfaz, queda claro qué puede hacer
 *    la app con las tareas, sin perderse en detalles de red.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleAssignmentRepository` (hace peticiones
 *   reales a Moodle).
 * - **Tests**: `FakeAssignmentRepository` (devuelve datos
 *   configurados).
 *
 * ## Quién la consume
 *
 * - El caso de uso `GetAssignmentDetail`.
 * - Cualquier ViewModel que necesite tareas.
 */
interface AssignmentRepository {

    /**
     * Devuelve la lista de tareas de uno o varios cursos.
     *
     * ## Qué devuelve
     *
     * Una lista plana de tareas, sin el estado de entrega de cada
     * una. Se usa cuando la UI solo necesita saber qué tareas hay
     * (por ejemplo, para contarlas o mostrarlas en un listado).
     *
     * ## Cuándo se llama
     *
     * - Al cargar el listado de tareas de un curso.
     * - Al calcular cuántas tareas pendientes tiene cada curso.
     *
     * @param courseIds IDs de los cursos a consultar.
     * @return Lista de tareas con sus datos básicos.
     */
    suspend fun getAssignments(courseIds: List<Int>): List<Assignment>

    /**
     * Devuelve el detalle completo de una tarea.
     *
     * ## Qué incluye
     *
     * A diferencia de [getAssignments], este método devuelve toda la
     * información de la tarea:
     *
     * - Nombre, descripción, fechas.
     * - Estado de entrega del alumno.
     * - Nota obtenida (si está calificada).
     * - Archivos adjuntos del enunciado.
     *
     * ## Cuándo se llama
     *
     * Al abrir la pantalla de detalle de una tarea.
     *
     * ## Cuándo devuelve `null`
     *
     * Si la tarea no existe en el curso indicado. No es un error: es
     * una situación válida (por ejemplo, la tarea fue borrada, o el
     * alumno ya no está matriculado).
     *
     * @param courseId ID del curso.
     * @param assignmentId ID (o `cmid`) de la tarea.
     * @return El detalle completo, o `null` si la tarea no existe.
     */
    suspend fun getAssignmentDetail(
        courseId: Int,
        assignmentId: Int,
    ): AssignmentDetail?
}