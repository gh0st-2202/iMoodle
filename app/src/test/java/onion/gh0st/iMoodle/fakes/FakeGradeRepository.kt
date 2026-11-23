package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.repository.grade.GradeRepository

/**
 * Implementación en memoria de [GradeRepository] para tests.
 */
class FakeGradeRepository : GradeRepository {

    /** Notas a devolver. */
    var courseGrade: CourseGrade = CourseGrade(0, emptyList())

    /** Si es no-null, se lanza al pedir las notas. */
    var gradeError: Throwable? = null

    /** Último curso pedido. */
    var lastCourseId: Int? = null
        private set

    /** Último usuario pedido. */
    var lastUserId: Int? = null
        private set

    /**
     * Devuelve [courseGrade] o lanza [gradeError].
     *
     * @param courseId ID del curso.
     * @param userId ID del alumno.
     * @return Las notas configuradas.
     * @throws Throwable si [gradeError] no es `null`.
     */
    override suspend fun getCourseGrades(courseId: Int, userId: Int?): CourseGrade {
        lastCourseId = courseId
        lastUserId = userId
        gradeError?.let { throw it }
        return courseGrade
    }
}
