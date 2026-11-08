package onion.gh0st.iMoodle.domain.rules

import java.util.Calendar

/**
 * Reglas de negocio relacionadas con las fechas de entrega.
 *
 * ## Qué es una "regla de negocio"
 *
 * Es una decisión que toma la app sobre cómo interpretar o presentar
 * los datos. No es una regla técnica (como "usa HTTPS" o "valida el
 * JSON"): es una regla del **mundo real** que la app decide aplicar.
 *
 * En este caso, la regla es: "cuando Moodle nos da una fecha de
 * entrega a medianoche, interpretarla como el final del día
 * anterior".
 *
 * ## Por qué existe esta regla
 *
 * Moodle tiene una forma peculiar de representar las fechas límite.
 * Cuando un profesor pone una tarea "para el viernes", Moodle suele
 * guardarla como "sábado a las 00:00". La intención del profesor es
 * "el viernes", pero técnicamente es "justo después del viernes", que
 * es el sábado a medianoche.
 *
 * Si la app mostrara la fecha tal cual, el usuario vería:
 *
 * - En la agenda: "Sábado: entrega de la tarea" (cuando en realidad
 *   es el viernes).
 * - En la notificación: el sábado por la mañana (cuando ya es
 *   tarde).
 *
 * Para que la app muestre la fecha **intuitiva** (la que el profesor
 * quiso decir), se aplica esta regla: si la fecha cae exactamente a
 * medianoche, se reinterpreta como 23:59 del día anterior. Así:
 *
 * - "Sábado 00:00" se convierte en "Viernes 23:59".
 * - La agenda muestra "Viernes".
 * - La notificación cae el viernes por la mañana.
 *
 * ## Quién la usa
 *
 * - `MoodleCalendarRepository`: al parsear los eventos de Moodle, para
 *   que la agenda muestre la fecha correcta.
 * - `TaskTriggerCalculator`: al calcular cuándo disparar la
 *   notificación, para que caiga en el día correcto.
 *
 * ## Sobre las unidades de tiempo
 *
 * La regla trabaja en **milisegundos** (epoch), porque `Calendar` (la
 * clase de Java que se usa para manipular fechas) trabaja así. Los
 * repositorios convierten de segundos a milisegundos antes de
 * llamarla y de vuelta después.
 */
object DueDateRules {

    /** Un minuto en milisegundos. */
    private const val ONE_MINUTE_MS = 60_000L

    /**
     * Ajusta una fecha de entrega si cae exactamente a medianoche.
     *
     * ## Comportamiento
     *
     * - **Si la fecha cae exactamente a las 00:00:00.000** (hora
     *   local): se devuelve un minuto antes, es decir, 23:59:00.000
     *   del día anterior.
     * - **En cualquier otro caso**: se devuelve la fecha sin tocar.
     *
     * ## Qué significa "exactamente a medianoche"
     *
     * Significa que las horas, minutos, segundos y milisegundos son
     * todos cero en la zona horaria local del dispositivo. Moodle
     * envía este valor cuando quiere decir "al final del día
     * anterior".
     *
     * ## Qué pasa si el dispositivo está en otra zona horaria
     *
     * La regla se aplica según la hora local. Si el dispositivo está
     * en una zona distinta a la del centro, el ajuste podría no ser
     * exacto. Pero como la app está pensada para alumnos de la Junta
     * (que están en la misma zona horaria que sus centros), esto no
     * es un problema en la práctica.
     *
     * ## Qué pasa con valores negativos o cero
     *
     * Se devuelven tal cual. No tiene sentido ajustar una fecha que
     * no es válida (por ejemplo, `0` significa "sin fecha").
     *
     * @param dueDate Instante en **milisegundos** (epoch).
     * @return El instante ajustado, o el mismo si no era medianoche.
     */
    fun adjustMidnight(dueDate: Long): Long {
        if (dueDate <= 0) return dueDate
        val calendar = Calendar.getInstance().apply { timeInMillis = dueDate }
        val isMidnight = calendar.get(Calendar.HOUR_OF_DAY) == 0 &&
                calendar.get(Calendar.MINUTE) == 0 &&
                calendar.get(Calendar.SECOND) == 0 &&
                calendar.get(Calendar.MILLISECOND) == 0
        return if (isMidnight) dueDate - ONE_MINUTE_MS else dueDate
    }
}