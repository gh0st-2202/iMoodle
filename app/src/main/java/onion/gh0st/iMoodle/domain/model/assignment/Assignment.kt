package onion.gh0st.iMoodle.domain.model.assignment

/**
 * Datos básicos de una tarea de Moodle.
 *
 * ## Qué es una tarea
 *
 * En Moodle, una tarea es una actividad que el profesor propone al
 * alumno: "entrega este trabajo antes del viernes", "sube un PDF con
 * la práctica", "responde estas preguntas", etc. Cada tarea pertenece
 * a un curso y tiene una serie de datos: nombre, descripción, fecha
 * límite, nota máxima, etc.
 *
 * ## Por qué hay dos modelos para una tarea
 *
 * La app tiene dos modelos para representar tareas:
 *
 * 1. **`Assignment`** (este): versión **ligera**. Solo los datos
 *    básicos. Se usa en listados, donde no hace falta el estado de
 *    entrega ni la nota.
 * 2. **`AssignmentDetail`**: versión **completa**. Incluye, además,
 *    el estado de entrega del alumno, la nota obtenida y los
 *    archivos adjuntos. Se usa en la pantalla de detalle.
 *
 * Tener dos modelos en lugar de uno tiene dos ventajas:
 *
 * - **Eficiencia**: el listado no necesita cargar información que no
 *   va a mostrar. Cargar solo lo necesario es más rápido.
 * - **Claridad**: cada pantalla usa el modelo que le corresponde.
 *   No hay campos "vacíos" o "no usados" en ningún sitio.
 *
 * ## Qué NO contiene
 *
 * - **No contiene el estado de la entrega**: si el alumno ha
 *   entregado, si está en borrador, etc. Eso solo se pide para el
 *   detalle, porque es una llamada más costosa.
 * - **No contiene los archivos adjuntos**: solo se cargan en el
 *   detalle.
 * - **No contiene la nota obtenida**: solo la nota máxima. La nota
 *   obtenida requiere otra llamada.
 *
 * @property id ID interno de la tarea en Moodle. Es único dentro del
 *   plugin `mod_assign`, pero no dentro del curso (para eso está el
 *   `cmid`).
 * @property courseId ID del curso al que pertenece la tarea. Permite
 *   agrupar tareas por curso sin necesidad de una tabla adicional.
 * @property name Nombre visible de la tarea. Es lo que se muestra en
 *   el listado.
 * @property description Descripción de la tarea, con formato HTML
 *   tal cual la envía Moodle. La UI la limpia antes de mostrarla.
 * @property dueDate Fecha límite de entrega, en **segundos** (epoch),
 *   o `null` si la tarea no tiene fecha límite. Es `null` cuando el
 *   profesor ha creado una tarea sin plazo.
 * @property allowSubmissionsFromDate Fecha desde la que se permiten
 *   entregas, en **segundos**, o `null` si no hay restricción. Antes
 *   de esa fecha, el alumno no puede entregar.
 * @property grade Nota máxima posible de la tarea, o `null` si
 *   Moodle no la informa. Es la puntuación sobre la que se calcula
 *   la nota final del curso.
 * @property submissionStatus Estado de la entrega (`"submitted"`,
 *   `"draft"`, `"new"`), o `null` si aún no se ha consultado. En el
 *   listado suele ser `null` porque no se consulta.
 */
data class Assignment(
    val id: Int,
    val courseId: Int,
    val name: String,
    val description: String,
    val dueDate: Long?,
    val allowSubmissionsFromDate: Long?,
    val grade: Double?,
    val submissionStatus: String?,
)