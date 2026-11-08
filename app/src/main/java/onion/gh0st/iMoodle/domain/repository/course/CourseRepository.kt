package onion.gh0st.iMoodle.domain.repository.course

import kotlinx.coroutines.flow.Flow
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.model.course.CourseContent

/**
 * Contrato de acceso a los cursos del alumno.
 *
 * ## Estrategia offline-first
 *
 * El listado de cursos sigue el patrón **offline-first**:
 *
 * - [getCoursesFlow] lee de la base de datos local. La UI lo observa
 *   y se actualiza sola cuando cambian los datos.
 * - [refreshCourses] va a Moodle, descarga los cursos y los guarda en
 *   la base de datos local.
 *
 * ## Caché en memoria para los contenidos
 *
 * El contenido de un curso (secciones, módulos, archivos) es más
 * grande y cambia menos que el listado. Por eso no se persiste en
 * Room: se cachea en memoria con un `LruCache` (en la implementación
 * real). El parámetro [CourseRepository.getCourseContents] `forceRefresh`
 * permite saltarse esa caché cuando el usuario pulsa
 * pull-to-refresh.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleCourseRepository` (con Room + caché).
 * - **Tests**: `FakeCourseRepository` (devuelve datos controlados).
 *
 * ## Quién la consume
 *
 * - `GetCourseContents`.
 * - `GetCourseFiles`.
 * - `GetUrlExternalUrl`.
 * - `CoursesViewModel`, `CourseDetailViewModel`.
 */
interface CourseRepository {

    /**
     * Observa los cursos del usuario desde la base de datos local.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de cursos cada vez que
     * cambia algo. La UI lo recoge y se actualiza sola.
     *
     * ## Filtrado por usuario
     *
     * La base de datos puede contener cursos de varios usuarios (si
     * varios hermanos usan el mismo dispositivo). Este método filtra
     * por `userId`, así que cada usuario solo ve los suyos.
     *
     * @param userId ID del usuario logueado.
     * @return `Flow` que emite la lista de cursos del usuario.
     */
    fun getCoursesFlow(userId: Int): Flow<List<Course>>

    /**
     * Sincroniza los cursos del usuario desde Moodle y los guarda en
     * la base de datos local.
     *
     * ## Qué hace
     *
     * Descarga la lista de cursos (con sus tareas) y reemplaza la
     * que hubiera en la base de datos. El `Flow` de [getCoursesFlow]
     * notificará a la UI del cambio.
     *
     * ## Cuándo se llama
     *
     * - Al cargar el listado de cursos.
     * - Al hacer pull-to-refresh.
     *
     * @param userId ID del usuario logueado.
     */
    suspend fun refreshCourses(userId: Int)

    /**
     * Devuelve los contenidos de un curso (secciones + módulos +
     * archivos).
     *
     * ## Qué devuelve
     *
     * La lista de secciones del curso, con sus módulos ya parseados.
     * Los módulos aún no están clasificados por tipo: esa
     * clasificación la hace `GetCourseContents`.
     *
     * ## Sobre la caché
     *
     * La implementación real usa una caché en memoria por `courseId`.
     * Si ya se ha cargado el curso antes, se devuelve la caché sin ir
     * a la red. El parámetro [forceRefresh] permite saltarse la caché
     * y forzar una petición nueva.
     *
     * ## Cuándo se llama `forceRefresh`
     *
     * Cuando el usuario hace pull-to-refresh en la pantalla de
     * detalle de un curso. El resto de aperturas usan la caché.
     *
     * @param courseId ID del curso.
     * @param forceRefresh Si `true`, ignora la caché y va a la red.
     * @return Lista de secciones con sus módulos.
     */
    suspend fun getCourseContents(
        courseId: Int,
        forceRefresh: Boolean = false,
    ): List<CourseContent>

    /**
     * Devuelve la URL externa real de un módulo de tipo `url`.
     *
     * ## Por qué no está en `CourseContent`
     *
     * Los módulos de tipo `url` son enlaces a webs externas. Moodle
     * no devuelve la URL externa en el listado de contenidos del
     * curso: hay que pedirla aparte con
     * `mod_url_get_urls_by_courses`. Como es una petición específica
     * para un módulo concreto, se hace bajo demanda, solo cuando el
     * usuario pulsa el enlace.
     *
     * ## Cuándo devuelve `null`
     *
     * Si el módulo no existe, si la petición falla, o si la URL no se
     * encuentra en la respuesta. La UI trata el `null` mostrando un
     * toast genérico.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo `url`.
     * @param forceRefresh Si `true`, ignora la caché (actualmente no
     *   hay caché de URLs, pero se deja el parámetro por simetría).
     * @return La URL externa, o `null` si no se encuentra.
     */
    suspend fun getUrlExternalUrl(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean = false,
    ): String?

    /**
     * Limpia las cachés en memoria del repositorio.
     *
     * ## Qué limpia
     *
     * Solo las cachés en memoria (por ejemplo, la de contenidos de
     * curso). La base de datos local no se toca: para eso está
     * `LocalDataCleaner`.
     *
     * ## Cuándo se llama
     *
     * Actualmente no se llama desde ningún sitio. Se deja por
     * simetría con otros repositorios y para un futuro "borrar
     * caché" manual.
     *
     * ## Por qué tiene implementación por defecto
     *
     * Porque no todos los repositorios que implementan esta interfaz
     * tienen cachés en memoria (por ejemplo, los fakes de test).
     * Poner una implementación vacía por defecto evita obligarles a
     * escribir un método que no usan.
     */
    fun clearCache() {}
}