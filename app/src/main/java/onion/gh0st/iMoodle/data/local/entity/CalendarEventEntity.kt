package onion.gh0st.iMoodle.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room que representa un evento del calendario de Moodle.
 *
 * ## Qué es un evento de calendario
 *
 * Es cualquier cosa que aparece en la agenda: una fecha de entrega
 * de tarea, un examen anunciado, un evento del curso, un festivo,
 * etc. Moodle los devuelve con un `eventType` que indica qué tipo de
 * evento es (`"assign"` para tareas, `"event"` para eventos
 * genéricos, etc.).
 *
 * ## Diferencias con las tareas y los cursos
 *
 * A diferencia de `CourseEntity` y `AssignmentEntity`, esta tabla:
 *
 * 1. **No tiene `userId`**: los eventos se reemplazan enteros en
 *    cada sincronización. Si el usuario A ve su agenda y luego entra
 *    el usuario B, la tabla se vacía y se llena con los eventos de B.
 *    Por eso, al hacer logout, se borra la tabla entera con
 *    `LocalDataCleaner`.
 * 2. **No tiene clave foránea a curso**: los eventos pueden no
 *    pertenecer a ningún curso (por ejemplo, un festivo a nivel de
 *    centro). Guardar la relación complicaría el modelo sin mucho
 *    beneficio.
 *
 * ## Sobre el ajuste de medianoche
 *
 * `timestart` está en **segundos** y ya viene ajustado por
 * `DueDateRules.adjustMidnight` en el repositorio. Ese ajuste
 * convierte una fecha límite a las `00:00` en `23:59` del día
 * anterior, que es lo que el usuario espera. Aquí se guarda ya
 * ajustado para no tener que hacerlo en cada lectura.
 *
 * @property id ID interno del evento en Moodle (clave primaria).
 * @property name Nombre visible del evento.
 * @property description Descripción en HTML (puede estar vacía).
 * @property timestart Inicio en **segundos**, ya ajustado por
 *   `adjustMidnight`.
 * @property courseId ID del curso asociado, o `null` si es un evento
 *   global (por ejemplo, un festivo).
 * @property courseName Nombre del curso asociado, o `null` si es
 *   global.
 * @property eventType Tipo de evento según el `modulename` de Moodle
 *   (`"assign"`, `"forum"`, `"event"`, etc.). Solo los `"assign"`
 *   generan recordatorios.
 * @property instanceId ID de la instancia del módulo asociado, o
 *   `null` si el evento no corresponde a ningún módulo navegable.
 */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String,
    val timestart: Long,
    val courseId: Int?,
    val courseName: String?,
    val eventType: String,
    val instanceId: Int?,
)