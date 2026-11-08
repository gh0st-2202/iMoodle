package onion.gh0st.iMoodle.domain.model.schedule

/**
 * Una franja horaria del horario del alumno.
 *
 * ## Qué es una franja horaria
 *
 * Es una asignatura concreta en un día y una hora determinados. Por
 * ejemplo:
 *
 * - "Lunes de 8:30 a 9:30: Acceso a datos, con el profesor García".
 * - "Martes de 10:00 a 11:00: Desarrollo de interfaces, con la
 *   profesora Pérez".
 *
 * Un horario completo es una lista de estas franjas.
 *
 * ## De dónde vienen los datos
 *
 * Del endpoint `get-horario` de Pasen, que devuelve la lista de
 * franjas del alumno. El parser `PasenScheduleParser` se encarga de
 * convertir el JSON en instancias de esta clase.
 *
 * ## Qué NO contiene
 *
 * - **No contiene el nombre del centro**: es el mismo para todas las
 *   franjas del alumno.
 * - **No contiene el curso académico**: es el mismo para todas las
 *   franjas.
 * - **No contiene fechas**: el horario es recurrente (se repite cada
 *   semana durante el curso). Las fechas de inicio y fin del curso
 *   están en otros sitios.
 *
 * ## Sobre `codeSection`
 *
 * Es el ID único de la asignatura. La UI lo usa como semilla para
 * asignar un color determinista a cada asignatura: dos franjas de la
 * misma asignatura tendrán el mismo color, aunque estén en días
 * distintos.
 *
 * ## Quién lo usa
 *
 * - `PasenScheduleParser` lo construye a partir del JSON de Pasen.
 * - `ScheduleCache` lo persiste en disco.
 * - `ScheduleViewModel` lo usa para construir los items de la UI.
 * - `ScheduleAdapter` lo usa para renderizar cada franja.
 *
 * @property day Día de la semana en español (`"Lunes"`, `"Martes"`,
 *   `"Miércoles"`, `"Jueves"`, `"Viernes"`, `"Sábado"` o
 *   `"Domingo"`).
 * @property hoursection Rango horario (por ejemplo, `"8:30 - 9:30"`).
 *   Es una cadena ya formateada por Pasen, no un par de horas.
 * @property section Slot del día (1..7). Determina el orden dentro
 *   del día. Es un número que asigna Pasen según el orden de las
 *   horas en el centro.
 * @property subject Nombre de la asignatura.
 * @property professor Nombre del profesor.
 * @property codeSection ID único de la asignatura. Se usa como
 *   semilla para asignar el color de la franja.
 */
data class ScheduleSlot(
    val day: String,
    val hoursection: String,
    val section: Int,
    val subject: String,
    val professor: String,
    val codeSection: Int,
)