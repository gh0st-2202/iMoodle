package onion.gh0st.iMoodle.domain.model.assignment

import onion.gh0st.iMoodle.domain.model.course.CourseFile

/**
 * El "expediente" completo de una tarea de Moodle.
 *
 * ## ¿Qué es esto?
 * Imagina que la tarea es un sobre físico. Esta clase representa todo el contenido de ese sobre:
 * el nombre de la tarea, la fecha límite, si has entregado algo, la nota, los archivos que ha
 * puesto el profesor, etc.
 *
 * Es una clase "inmutable", lo que significa que una vez creada para mostrarla en pantalla,
 * no se puede modificar. Si algo cambia, se crea un expediente nuevo.
 *
 * @property id El número de identificación de la tarea dentro de Moodle.
 * @property courseId El número del curso al que pertenece la tarea.
 * @property cmId El número de identificación del módulo (el "hueco" que ocupa la tarea en el curso).
 * @property name El título de la tarea (ej: "Práctica 1").
 * @property description Las instrucciones de la tarea. Viene con formato HTML (etiquetas de texto).
 * @property dueDate La fecha límite de entrega. Está en "segundos epoch" (una forma de contar el tiempo
 *   que usan los ordenadores, contando los segundos desde 1970). Si es `null`, no hay fecha límite.
 * @property allowSubmissionsFromDate La fecha a partir de la cual se puede entregar. Si es `null`, se puede entregar desde siempre.
 * @property cutoffDate La fecha de "corte duro". Después de esta fecha, Moodle cierra la tarea por completo.
 * @property maxGrade La nota máxima que se puede sacar.
 * @property submissionStatus El estado de tu entrega. Puede ser `"submitted"` (entregado), `"draft"` (guardado pero no enviado) o `"new"` (sin empezar).
 * @property submissionGrade La nota numérica que te han puesto (si te la han puesto).
 * @property gradeDisplay La nota escrita tal cual la muestra Moodle (ej: "7,5" o "Apto").
 * @property submissionGraded Un booleano (verdadero/falso) que dice si ya te han corregido la tarea.
 * @property canSubmit Un booleano que dice si TIENES PERMISO para entregar. Se calcula mirando las fechas y lo que dice Moodle.
 * @property canEdit Un booleano que dice si TIENES PERMISO para modificar o borrar la entrega que ya hiciste.
 * @property lastAttemptDate La fecha de tu último intento de entrega.
 * @property submissionId El número interno de tu entrega. Es vital para poder leer o escribir comentarios en ella. Si no has entregado nada, es `null`.
 * @property files Lista de archivos que el profesor ha adjuntado a las instrucciones (PDFs, plantillas...).
 * @property submissionFiles Lista de archivos que TÚ has entregado.
 * @property enabledSubmissionPlugins Una lista de las cosas que el profesor permite entregar (ej: solo archivos, o archivos y texto).
 * @property statusUnknown Si hubo un error de red y no se pudo comprobar el estado real de la tarea, esto se pone en `true`. Así la pantalla no miente al usuario.
 */
data class AssignmentDetail(
    val id: Int,
    val courseId: Int,
    val cmId: Int,
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
    val canEdit: Boolean,
    val lastAttemptDate: Long?,
    val submissionId: Int? = null,
    val files: List<CourseFile> = emptyList(),
    val submissionFiles: List<CourseFile> = emptyList(),
    val enabledSubmissionPlugins: Set<String> = emptySet(),
    val statusUnknown: Boolean = false,
)