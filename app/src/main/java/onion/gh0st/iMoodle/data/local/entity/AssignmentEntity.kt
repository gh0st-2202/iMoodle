package onion.gh0st.iMoodle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Room que representa una tarea de Moodle.
 *
 * ## Qué es una tarea
 *
 * En Moodle, una tarea es una actividad que el profesor propone al
 * alumno: "entrega este trabajo antes del viernes", "sube un PDF con
 * la práctica", etc. Cada tarea pertenece a un curso y tiene una
 * serie de datos (nombre, descripción, fecha límite, nota máxima…).
 *
 * ## Sobre la clave foránea (ForeignKey)
 *
 * Una clave foránea es una regla que dice "el valor de esta columna
 * debe existir en otra tabla". En este caso, `courseId` debe
 * corresponder a un `id` de la tabla `courses`.
 *
 * Además, se configura con `onDelete = CASCADE`, que significa: "si
 * se borra el curso padre, borra también todas sus tareas". Esto es
 * útil porque:
 *
 * - Al hacer logout y limpiar los cursos, no hay que borrar las
 *   tareas a mano: se van con ellos.
 * - Al refrescar los cursos, si un curso desaparece, sus tareas
 *   también, sin dejar huérfanas.
 *
 * ## Sobre el índice
 *
 * El índice sobre `courseId` acelera dos operaciones:
 *
 * - La cascada de borrado (cuando se borra un curso, Room busca sus
 *   tareas por `courseId`).
 * - Cualquier consulta futura que filtre por curso.
 *
 * ## Sobre los timestamps
 *
 * `dueDate` y `allowSubmissionsFromDate` están en **segundos**
 * (epoch), igual que en el resto de la app.
 *
 * @property id ID interno de la tarea en Moodle (clave primaria).
 * @property courseId ID del curso al que pertenece (clave foránea con
 *   cascada).
 * @property name Nombre visible de la tarea.
 * @property description Descripción en HTML (tal cual la envía
 *   Moodle).
 * @property dueDate Fecha límite de entrega en **segundos**, o
 *   `null` si la tarea no tiene fecha límite.
 * @property allowSubmissionsFromDate Fecha desde la que se permiten
 *   entregas, en **segundos**, o `null` si no hay restricción.
 * @property grade Nota máxima posible, o `null` si Moodle no la
 *   informa.
 * @property submissionStatus Estado de la entrega (`"submitted"`,
 *   `"draft"`, `"new"`), o `null` si aún no se ha consultado.
 */
@Entity(
    tableName = "assignments",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class AssignmentEntity(
    @PrimaryKey val id: Int,
    val courseId: Int,
    val name: String,
    val description: String,
    val dueDate: Long?,
    val allowSubmissionsFromDate: Long?,
    val grade: Double?,
    val submissionStatus: String?,
)