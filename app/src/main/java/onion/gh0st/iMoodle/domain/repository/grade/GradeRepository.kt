package onion.gh0st.iMoodle.domain.repository.grade

import onion.gh0st.iMoodle.domain.model.grade.CourseGrade

/**
 * Contrato de acceso al libro de calificaciones de Moodle.
 *
 * ## Qué ofrece
 *
 * Las notas del alumno en un curso: la nota total y la de cada
 * actividad calificable, con el comentario del profesor cuando lo hay.
 *
 * ## Por qué no hay caché
 *
 * Las notas cambian cuando el profesor corrige. Cachearlas mostraría
 * una nota antigua justo en el momento en que el alumno más quiere
 * verla actualizada. La consulta es ligera y se hace cada vez que se
 * abre la pantalla.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleGradeRepository` (API REST de Moodle).
 * - **Tests**: `FakeGradeRepository` (datos controlados).
 *
 * ## Quién la consume
 *
 * - `GetCourseGrades`.
 * - `GradesViewModel`.
 */
interface GradeRepository {

    /**
     * Devuelve las notas de un curso.
     *
     * ## Qué incluye
     *
     * La respuesta de Moodle (`gradereport_user_get_grade_items`)
     * aplanada en [CourseGrade]. La fila del total del curso se
     * conserva dentro de `items` y se distingue con
     * [onion.gh0st.iMoodle.domain.model.grade.GradeItem.isCourseTotal].
     *
     * ## Cuándo se llama
     *
     * Al abrir la pantalla de notas de un curso y al refrescarla.
     *
     * @param courseId Identificador del curso.
     * @param userId Identificador del alumno, o `null` para que Moodle
     *   use el usuario del token.
     * @return Las notas del curso.
     */
    suspend fun getCourseGrades(courseId: Int, userId: Int? = null): CourseGrade
}
