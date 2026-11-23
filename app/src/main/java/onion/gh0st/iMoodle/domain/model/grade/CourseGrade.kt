package onion.gh0st.iMoodle.domain.model.grade

/**
 * Notas de un curso, tal como las devuelve el libro de calificaciones.
 *
 * ## Qué contiene
 *
 * - La **nota total del curso**, si el profesor la publica.
 * - Una entrada por **actividad calificable** (tareas, cuestionarios,
 *   foros evaluables, etc.).
 *
 * ## Por qué hay un modelo propio
 *
 * Moodle devuelve las notas en un JSON pensado para su libro de
 * calificaciones, con muchos campos que la app no necesita. Este
 * modelo deja solo lo que el alumno quiere ver: el nombre de la
 * actividad y su nota.
 *
 * ## Quién lo usa
 *
 * - `MoodleGradeRepository` lo construye.
 * - `GetCourseGrades` lo devuelve al ViewModel.
 * - `GradesViewModel` y `GradesAdapter` lo pintan.
 *
 * @property courseId Identificador del curso.
 * @property items Entradas del libro de calificaciones. Puede incluir
 *   la fila especial del total del curso.
 */
data class CourseGrade(
    val courseId: Int,
    val items: List<GradeItem>,
) {

    /** La fila del total del curso, o `null` si el profesor no la publica. */
    val total: GradeItem?
        get() = items.firstOrNull { item -> item.isCourseTotal }

    /** Las actividades calificables, sin la fila del total del curso. */
    val activities: List<GradeItem>
        get() = items.filterNot { item -> item.isCourseTotal }
}

/**
 * Una fila del libro de calificaciones.
 *
 * @property id Identificador de la fila en Moodle.
 * @property name Nombre visible de la actividad o del curso.
 * @property itemType Tipo de fila: `"course"` para el total del curso,
 *   `"mod"` para una actividad y `"manual"` para una nota manual.
 * @property itemModule Módulo de Moodle al que pertenece (`"assign"`,
 *   `"quiz"`…), o `null` si no aplica.
 * @property cmid Identificador del módulo en el curso (course module),
 *   o `null` si la fila no corresponde a una actividad.
 * @property itemInstance Identificador de la instancia del módulo
 *   (por ejemplo, el ID de la tarea), o `null`.
 * @property gradeFormatted Nota ya formateada por Moodle. Puede traer
 *   HTML (por ejemplo, un enlace al cuestionario).
 * @property gradeRaw Nota numérica, o `null` si no hay nota.
 * @property gradeMin Nota mínima de la escala, o `null`.
 * @property gradeMax Nota máxima de la escala, o `null`.
 * @property isHidden `true` si la nota está oculta para el alumno.
 * @property isLocked `true` si la nota está bloqueada y no se
 *   recalculará.
 * @property feedback Comentario de retroalimentación que dejó el
 *   profesor en el libro de calificaciones, en HTML, o `null` si no
 *   hay.
 */
data class GradeItem(
    val id: Int,
    val name: String,
    val itemType: String,
    val itemModule: String?,
    val cmid: Int? = null,
    val itemInstance: Int? = null,
    val gradeFormatted: String?,
    val gradeRaw: Double?,
    val gradeMin: Double?,
    val gradeMax: Double?,
    val isHidden: Boolean,
    val isLocked: Boolean,
    val feedback: String? = null,
) {

    /** `true` si esta fila es el total del curso. */
    val isCourseTotal: Boolean
        get() = itemType == TYPE_COURSE

    /** `true` si el alumno ya tiene una nota numérica publicada. */
    val hasGrade: Boolean
        get() = gradeRaw != null || !gradeFormatted.isNullOrBlank()

    private companion object {
        /** Tipo de fila del total del curso en Moodle. */
        const val TYPE_COURSE = "course"
    }
}
