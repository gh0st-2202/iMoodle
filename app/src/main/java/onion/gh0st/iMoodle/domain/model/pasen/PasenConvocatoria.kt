package onion.gh0st.iMoodle.domain.model.pasen

/**
 * Una asignatura con sus notas dentro de una convocatoria.
 *
 * ## Por qué es flexible
 *
 * El endpoint `get-notas-convocatorias` de iPasen no está
 * documentado con precisión en el README del proyecto (`subjects` se
 * tipa como `List<Any>`). Para no bloquear la funcionalidad, el
 * parser conserva **todos** los campos que iPasen envíe para la
 * asignatura en [fields], además de intentar extraer los más habituales
 * ([grade], [average], [annotation]) por su nombre.
 *
 * Así, aunque los nombres reales difieran, la pantalla sigue pintando
 * la información disponible en lugar de fallar.
 *
 * @property name Nombre de la asignatura.
 * @property grade Nota principal, ya formateada por iPasen, o `null`.
 * @property average Media de la clase, o `null`.
 * @property annotation Anotación del profesorado, o `null`.
 * @property fields Todos los campos recibidos, en el orden en que
 *   vengan, para poder pintar los que no se hayan reconocido.
 */
data class PasenSubjectGrade(
    val name: String,
    val grade: String?,
    val average: String?,
    val annotation: String?,
    val fields: List<Pair<String, String>>,
)

/**
 * Una convocatoria de evaluación de iPasen.
 *
 * @property course Nombre de la convocatoria («1ª Evaluación», «Final»…).
 * @property isFinished `true` si la convocatoria ya ha finalizado.
 * @property dateStart Fecha de inicio `dd/MM/yyyy`.
 * @property dateFinish Fecha de fin `dd/MM/yyyy`.
 * @property isActive `true` si está activa ahora.
 * @property idCenter ID interno de la convocatoria.
 * @property subjects Asignaturas con sus notas.
 */
data class PasenConvocatoria(
    val course: String,
    val isFinished: Boolean,
    val dateStart: String,
    val dateFinish: String,
    val isActive: Boolean,
    val idCenter: Int,
    val subjects: List<PasenSubjectGrade>,
)
