package onion.gh0st.iMoodle.notifications

import onion.gh0st.iMoodle.domain.rules.DueDateRules
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Calculadora del instante en el que debe dispararse el recordatorio
 * de una tarea.
 *
 * ## Qué hace
 *
 * Recibe la fecha de entrega de una tarea y el instante actual, y
 * devuelve el instante exacto en el que debe sonar la alarma (o
 * `null` si no debe sonar).
 *
 * ## La "regla de oro"
 *
 * La app sigue una regla sencilla para decidir cuándo avisar:
 *
 * 1. A la fecha de entrega se le **restan 12 horas**.
 * 2. Del día resultante, se toma la hora de **08:00 locales**.
 *
 * Ese es el instante "nominal" de disparo. Por ejemplo:
 *
 * - Tarea para el viernes a las 15:00 → menos 12h = viernes a las
 *   03:00 → del viernes, 08:00 → **viernes a las 08:00**.
 * - Tarea para el viernes a las 10:00 → menos 12h = jueves a las
 *   22:00 → del jueves, 08:00 → **jueves a las 08:00**.
 * - Tarea para el sábado a las 00:00 (que en realidad es "viernes")
 *   → se aplica también la "regla de medianoche" (ver más abajo),
 *   queda como viernes a las 23:59 → menos 12h = viernes a las
 *   11:59 → del viernes, 08:00 → **viernes a las 08:00**.
 *
 * El resultado es que el usuario recibe el aviso por la mañana, con
 * tiempo para actuar durante el día.
 *
 * ## Reglas adicionales
 *
 * Además de la regla de oro, hay dos reglas más:
 *
 * 1. **Regla de medianoche**: si la fecha de entrega cae exactamente
 *    a las 00:00:00.000, se interpreta como 23:59:00.000 del día
 *    anterior. Se delega en `DueDateRules.adjustMidnight`. El motivo
 *    está explicado en esa clase.
 * 2. **Descarte de vencidas**: si la fecha efectiva (después del
 *    ajuste de medianoche) ya ha pasado, no se programa nada. No
 *    tiene sentido avisar de una tarea que ya está vencida.
 * 3. **Descarte de nominales pasados**: si el instante nominal
 *    (08:00 del día resultante) ya ha pasado, tampoco se programa.
 *    No hay "catch-up": si la alarma se perdió, se perdió. No
 *    queremos avisar de una tarea a las 3 de la tarde solo porque
 *    la app se abrió después de las 8.
 *
 * ## Sobre el DST (cambio de hora)
 *
 * El cálculo se hace con `ZonedDateTime` y `Duration.ofHours(12)`,
 * que manejan correctamente los cambios de hora (DST). En los días
 * de cambio de hora, el resultado se ajusta automáticamente para
 * respetar la hora local del dispositivo. Por ejemplo, si en primavera
 * se adelanta el reloj, el "viernes a las 08:00" sigue siendo el
 * viernes a las 08:00 local (aunque en UTC sean las 06:00 o las
 * 07:00, según el día).
 *
 * ## Función pura
 *
 * `calculate` **no usa `System.currentTimeMillis()` internamente**.
 * Recibe `now` como parámetro. Esto la hace **pura** (mismo input,
 * mismo output) y **testeable** sin manipular el reloj del sistema.
 * Los tests pasan un `now` fijo y comparan contra un resultado
 * esperado.
 */
internal object TaskTriggerCalculator {

    /** Doce horas como `Duration`. */
    private val TWELVE_HOURS = java.time.Duration.ofHours(12)

    /** Hora local a la que se intenta disparar el recordatorio. */
    private val REMINDER_TIME = LocalTime.of(8, 0)

    /**
     * Calcula el instante de disparo para una fecha de entrega.
     *
     * ## Qué hace paso a paso
     *
     * 1. **Ajuste de medianoche**: si la fecha cae exactamente a las
     *    00:00, se reinterpreta como 23:59 del día anterior.
     * 2. **Descarte de vencidas**: si la fecha efectiva ya ha pasado,
     *    devuelve `null`.
     * 3. **Regla de oro**: resta 12 horas a la fecha efectiva y del
     *    día resultante toma las 08:00 locales.
     * 4. **Descarte de nominales pasados**: si el instante nominal ya
     *    ha pasado, devuelve `null`.
     *
     * @param dueDate Fecha de entrega en **milisegundos**.
     * @param now Instante actual en **milisegundos**. Se pasa como
     *   parámetro para que la función sea testeable.
     * @return Instante de disparo en milisegundos, o `null` si no
     *   debe programarse la alarma.
     */
    fun calculate(dueDate: Long, now: Long): Long? {
        val effectiveDueDateMs = DueDateRules.adjustMidnight(dueDate)
        if (effectiveDueDateMs <= now) return null

        val zone = ZoneId.systemDefault()
        val effectiveDueDateTime = Instant.ofEpochMilli(effectiveDueDateMs).atZone(zone)

        val shiftedDateTime = effectiveDueDateTime.minus(TWELVE_HOURS)
        val nominalDateTime = shiftedDateTime.with(REMINDER_TIME)

        val nominalMs = nominalDateTime.toInstant().toEpochMilli()
        return nominalMs.takeIf { it > now }
    }
}