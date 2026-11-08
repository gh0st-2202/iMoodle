package onion.gh0st.iMoodle.domain.model.assignment

import onion.gh0st.iMoodle.domain.model.course.CourseFile

/**
 * Detalle completo de una tarea de Moodle.
 *
 * ## Qué es esto
 *
 * Es la versión "completa" de una tarea. Incluye todo lo que tiene
 * `Assignment` (nombre, descripción, fechas, etc.) más:
 *
 * - El **estado de la entrega** del alumno.
 * - La **nota obtenida** (si está calificada).
 * - Los **archivos adjuntos** del enunciado (plantillas, PDFs, etc.).
 *
 * Se usa en la pantalla de detalle de tarea, que es donde el alumno
 * ve toda la información de una tarea concreta.
 *
 * ## Qué es el "estado desconocido"
 *
 * El campo [statusUnknown] es especialmente importante. Indica si la
 * app **pudo consultar** el estado de la entrega o no:
 *
 * - **`false`** (lo normal): el estado de la entrega es fiable. Si
 *   dice "sin entregar", es que no se ha entregado; si dice
 *   "entregado", es que se ha entregado.
 * - **`true`**: no se pudo consultar. Puede ser porque la red falló,
 *   porque Moodle devolvió un error, o porque el servidor no
 *   respondió. En este caso, los campos [submissionStatus],
 *   [submissionGrade], [gradeDisplay] y [submissionGraded] **no son
 *   fiables**, y la UI debe mostrarlos como "desconocidos" en lugar
 *   de asumir "sin entregar".
 *
 * ## Por qué es importante distinguir "sin entregar" de "desconocido"
 *
 * Imagina que el alumno ha entregado la tarea pero, al abrir el
 * detalle, la red falla y la app no puede consultar el estado. Si la
 * app asumiera "sin entregar", el alumno vería un mensaje falso y
 * podría pensar que no ha entregado. Con [statusUnknown], la app le
 * dice "no se pudo consultar", que es la verdad.
 *
 * @property id ID interno de la tarea en Moodle.
 * @property name Nombre visible de la tarea.
 * @property description Descripción en HTML, tal cual la envía
 *   Moodle.
 * @property dueDate Fecha límite de entrega en **segundos**, o
 *   `null` si no hay.
 * @property allowSubmissionsFromDate Fecha desde la que se permiten
 *   entregas, en **segundos**, o `null` si no hay restricción.
 * @property cutoffDate Fecha de corte a partir de la cual no se
 *   aceptan más entregas, en **segundos**, o `null` si no hay. Es
 *   una fecha "límite duro" que, si existe, anula la posibilidad de
 *   entregar aunque `dueDate` sea posterior.
 * @property maxGrade Nota máxima posible de la tarea.
 * @property submissionStatus Estado de la entrega (`"submitted"`,
 *   `"draft"`, `"new"`), o `null` si no está disponible.
 * @property submissionGrade Nota numérica obtenida, o `null` si no
 *   está calificada o la escala es cualitativa.
 * @property gradeDisplay Nota ya formateada por Moodle (por ejemplo,
 *   `"7,5"` o `"Apto"`). Útil para tareas evaluadas con escala
 *   cualitativa (Apto/No apto).
 * @property submissionGraded `true` si la tarea ya está calificada.
 * @property canSubmit `true` si el alumno puede entregar ahora mismo
 *   (dentro del plazo, sin entrega previa bloqueante, etc.).
 * @property lastAttemptDate Fecha del último intento, en
 *   **segundos**, o `null` si nunca se ha intentado.
 * @property files Archivos adjuntos del enunciado de la tarea
 *   (plantillas, PDFs, etc.).
 * @property statusUnknown `true` si **no se pudo consultar** el
 *   estado de la entrega. En ese caso, los campos de entrega y nota
 *   no son fiables.
 */
data class AssignmentDetail(
    val id: Int,
    val name: String,
    val description: String,
    val dueDate: Long?,
    val allowSubmissionsFromDate: Long?,
    val cutoffDate: Long?,
    val maxGrade: Double,
    val submissionStatus: String?,
    val submissionGrade: Double?,
    val gradeDisplay: String?,
    val submissionGraded: Boolean,
    val canSubmit: Boolean,
    val lastAttemptDate: Long?,
    val files: List<CourseFile> = emptyList(),
    val statusUnknown: Boolean = false,
)