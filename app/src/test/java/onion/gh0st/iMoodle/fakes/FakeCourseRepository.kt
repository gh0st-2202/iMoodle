package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository

/**
 * Implementación en memoria de [CourseRepository] para tests.
 *
 * Cubre el contrato completo:
 * - **Flow de cursos**: configurable vía [coursesFlow].
 * - **Refresco**: incrementa [refreshCalls] cada vez que se invoca.
 * - **Contenidos**: devuelve [courseContents] o lanza [contentsError].
 * - **URL externa**: devuelve [externalUrl].
 *
 * Los `forceRefresh` recibidos se registran en sus propiedades
 * correspondientes para verificar que la UI pasa el flag correcto.
 */
class FakeCourseRepository : CourseRepository {

    /** Flow de cursos que observará el ViewModel. */
    var coursesFlow: Flow<List<Course>> = flowOf(emptyList())

    /** Contenidos a devolver por [getCourseContents]. */
    var courseContents: List<CourseContent> = emptyList()

    /** URL externa devuelta por [getUrlExternalUrl]. */
    var externalUrl: String? = null

    /** Si es no-null, se lanza al invocar [getCourseContents]. */
    var contentsError: Throwable? = null

    /** Número de llamadas a [refreshCourses]. */
    var refreshCalls: Int = 0
        private set

    /** Último `forceRefresh` recibido por [getCourseContents]. */
    var lastForceRefreshContents: Boolean? = null
        private set

    /**
     * Devuelve [coursesFlow].
     *
     * @param userId ID del usuario (ignorado por el fake).
     * @return El flujo configurado.
     */
    override fun getCoursesFlow(userId: Int): Flow<List<Course>> = coursesFlow

    /**
     * Incrementa [refreshCalls]. No hace red.
     *
     * @param userId ID del usuario (ignorado por el fake).
     */
    override suspend fun refreshCourses(userId: Int) {
        refreshCalls++
    }

    /**
     * Devuelve [courseContents] o lanza [contentsError].
     *
     * @param courseId ID del curso (ignorado por el fake).
     * @param forceRefresh Flag que se registra en [lastForceRefreshContents].
     * @return Lista configurada.
     * @throws Throwable si [contentsError] no es `null`.
     */
    override suspend fun getCourseContents(
        courseId: Int,
        forceRefresh: Boolean,
    ): List<CourseContent> {
        lastForceRefreshContents = forceRefresh
        contentsError?.let { throw it }
        return courseContents
    }

    /**
     * Devuelve [externalUrl].
     *
     * @param courseId ID del curso (ignorado por el fake).
     * @param instanceId ID de la instancia (ignorado por el fake).
     * @param forceRefresh Flag (ignorado por el fake).
     * @return La URL externa configurada, o `null`.
     */
    override suspend fun getUrlExternalUrl(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean,
    ): String? = externalUrl
}