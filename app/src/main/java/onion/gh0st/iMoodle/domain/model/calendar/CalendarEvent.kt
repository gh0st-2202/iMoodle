package onion.gh0st.iMoodle.domain.model.calendar

/**
 * Evento del calendario de Moodle.
 *
 * ## Qué es un evento de calendario
 *
 * Es cualquier cosa que aparece en la agenda del alumno: una fecha de
 * entrega de tarea, un examen anunciado, un evento del curso, un
 * festivo a nivel de centro, etc. Moodle los devuelve todos mezclados
 * y la app los muestra en la pantalla de Agenda.
 *
 * ## Diferencia con `Assignment`
 *
 * Un evento de calendario y una tarea son cosas distintas aunque
 * puedan referirse a lo mismo. Una tarea es una **actividad** que el
 * alumno tiene que realizar. Un evento es una **entrada en el
 * calendario** que puede referirse a esa tarea (por su fecha de
 * entrega) o a cualquier otra cosa.
 *
 * Por ejemplo:
 *
 * - Una tarea "Entrega de la práctica 3" con fecha límite el viernes
 *   genera un evento en el calendario para el viernes.
 * - Un examen de "Acceso a datos" anunciado para el lunes genera un
 *   evento en el calendario para el lunes.
 * - Un festivo a nivel de centro genera un evento en el calendario.
 *
 * ## Cómo se usa
 *
 * - Se **persiste en Room** (`CalendarEventEntity`).
 * - Se **expone como `Flow`** a la UI de la agenda.
 * - Se usa para **programar recordatorios** de tareas próximas a
 *   entregar (solo los eventos con `eventType = "assign"`).
 *
 * ## Sobre el ajuste de medianoche
 *
 * El campo `timestart` ya viene ajustado por `DueDateRules.adjustMidnight`
 * en el repositorio. Ese ajuste convierte una fecha límite a las
 * `00:00` en `23:59` del día anterior. El motivo es que Moodle, para
 * decir "final del viernes", a veces envía "sábado 00:00". Sin el
 * ajuste, el usuario vería "sábado" en la agenda y la notificación
 * caería el sábado, cuando en realidad quiere decir "viernes".
 *
 * @property id ID interno del evento en Moodle. Es único en la tabla
 *   `calendar_events`.
 * @property name Nombre visible del evento. Por ejemplo, "Entrega de
 *   la práctica 3" o "Examen de Acceso a datos".
 * @property description Descripción en HTML (puede estar vacía).
 * @property timestart Inicio en **segundos** (epoch), ya ajustado por
 *   `adjustMidnight`.
 * @property timeduration Duración en segundos, o `null` si no aplica.
 *   Para eventos puntuales (como una entrega) es `null`. Para
 *   eventos con duración (como un examen de 2 horas) sería el número
 *   de segundos.
 * @property courseId ID del curso asociado, o `null` si es un evento
 *   global (por ejemplo, un festivo).
 * @property courseName Nombre del curso, o `null` si es global. Se
 *   guarda además del ID para no tener que hacer un `JOIN` con la
 *   tabla de cursos al mostrar la agenda.
 * @property eventType Tipo de evento según el `modulename` de Moodle
 *   (`"assign"`, `"forum"`, `"quiz"`, `"event"`, etc.). Solo los
 *   eventos con `"assign"` generan recordatorios.
 * @property instanceId ID de la instancia del módulo asociado, o
 *   `null` si el evento no corresponde a ningún módulo navegable.
 *   Permite abrir la pantalla correspondiente al pulsar el evento.
 */
data class CalendarEvent(
    val id: Int,
    val name: String,
    val description: String,
    val timestart: Long,
    val timeduration: Long?,
    val courseId: Int?,
    val courseName: String?,
    val eventType: String,
    val instanceId: Int?,
)