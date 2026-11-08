package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import onion.gh0st.iMoodle.data.local.dao.CourseDao
import onion.gh0st.iMoodle.data.local.entity.CourseEntity
import onion.gh0st.iMoodle.data.local.relation.CourseWithAssignments

/**
 * Implementación en memoria de [CourseDao] para tests.
 *
 * Sustituye a Room real en los tests unitarios: evita Robolectric y
 * el problema de compatibilidad con Java 25, y hace los tests más
 * rápidos y deterministas.
 */
class FakeCourseDao : CourseDao {

    /** Flujo interno con el estado actual de los cursos. */
    private val coursesFlow = MutableStateFlow<List<CourseEntity>>(emptyList())

    /** Snapshot de los cursos insertados, útil para asserts. */
    val courses: List<CourseEntity> get() = coursesFlow.value

    /**
     * Observa los cursos de un usuario con sus tareas anidadas.
     *
     * @param userId ID del usuario logueado.
     * @return `Flow` filtrado por `userId` y ordenado por `fullname`.
     */
    override fun getCoursesWithAssignments(userId: Int): Flow<List<CourseWithAssignments>> =
        coursesFlow.asStateFlow().map { entities ->
            entities
                .filter { it.userId == userId }
                .sortedBy { it.fullname }
                .map { CourseWithAssignments(course = it, assignments = emptyList()) }
        }

    /**
     * Reemplaza el contenido por el lote indicado.
     *
     * @param courses Cursos a guardar.
     */
    override suspend fun insertCourses(courses: List<CourseEntity>) {
        coursesFlow.value = courses
    }

    /**
     * Devuelve los cursos de un usuario (no reactivo).
     *
     * @param userId ID del usuario logueado.
     * @return Lista de cursos sin tareas.
     */
    override suspend fun getCoursesOnce(userId: Int): List<CourseEntity> =
        coursesFlow.value.filter { it.userId == userId }

    /**
     * Borra los cursos de un usuario.
     *
     * @param userId ID del usuario cuyos cursos se eliminan.
     */
    override suspend fun clearCoursesForUser(userId: Int) {
        coursesFlow.value = coursesFlow.value.filterNot { it.userId == userId }
    }
}