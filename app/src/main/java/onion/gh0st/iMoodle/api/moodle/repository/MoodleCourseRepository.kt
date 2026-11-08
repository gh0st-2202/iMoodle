package onion.gh0st.iMoodle.api.moodle.repository

import android.util.LruCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.api.moodle.network.appendMoodleToken
import onion.gh0st.iMoodle.data.local.InlineTransactionRunner
import onion.gh0st.iMoodle.data.local.TransactionRunner
import onion.gh0st.iMoodle.data.local.dao.AssignmentDao
import onion.gh0st.iMoodle.data.local.dao.CourseDao
import onion.gh0st.iMoodle.data.local.entity.AssignmentEntity
import onion.gh0st.iMoodle.data.local.entity.CourseEntity
import onion.gh0st.iMoodle.data.local.relation.CourseWithAssignments
import onion.gh0st.iMoodle.domain.model.course.Course
import onion.gh0st.iMoodle.domain.model.course.CourseContent
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [CourseRepository] con persistencia local
 * (Room) y caché en memoria para los contenidos de cada curso.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de gestionar los **cursos** del usuario: el listado
 * de asignaturas en las que está matriculado, y el contenido de cada
 * una (secciones, módulos, archivos). Es una de las piezas más
 * grandes del proyecto porque los cursos son la base de casi toda la
 * navegación de la app.
 *
 * ## Estrategia offline-first para el listado
 *
 * El listado de cursos sigue el patrón **offline-first**:
 *
 * - [getCoursesFlow] lee de Room. La UI lo observa y se actualiza
 *   sola cuando cambian los datos.
 * - [refreshCourses] va a Moodle, descarga los cursos y sus tareas,
 *   y los guarda en Room. El `Flow` notifica a la UI.
 *
 * Así el listado se abre al instante, aunque no haya conexión, y los
 * datos que el usuario ya había visto siguen ahí.
 *
 * ## Caché en memoria para los contenidos
 *
 * El listado de cursos es pequeño y se puede persistir en Room sin
 * problema. Pero el contenido de un curso (secciones, módulos,
 * archivos) es mucho más grande y cambia poco. Cachearlo en Room
 * añadiría complejidad (transacciones, invalidación por módulo, etc.)
 * sin mucho beneficio. Por eso se cachea solo en memoria, con un
 * `LruCache`: cuando el usuario entra por primera vez en un curso, se
 * descarga; las siguientes visitas son instantáneas.
 *
 * ## Sobre las URLs de las imágenes
 *
 * Moodle devuelve las URLs de las portadas de los cursos sin token.
 * Si se guardaran en Room con el token, el token quedaría en claro en
 * la base de datos local. Para evitarlo, se guardan sin token y se
 * añade al vuelo cuando se leen, en el `map` de [getCoursesFlow].
 *
 * ## Sobre las tareas
 *
 * Este repositorio también guarda las **tareas** de cada curso en
 * Room, aunque no las expone directamente. Lo hace para que otras
 * partes de la app (por ejemplo, el listado de tareas pendientes por
 * curso) puedan consultarlas sin volver a la red. La pantalla de
 * detalle de una tarea concreta no las usa: para eso está
 * `MoodleAssignmentRepository`, que consulta siempre fresco.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 * @param courseDao DAO de cursos de Room.
 * @param assignmentDao DAO de tareas de Room.
 * @param transactionRunner Ejecutor de transacciones.
 */
@Singleton
class MoodleCourseRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
    private val courseDao: CourseDao,
    private val assignmentDao: AssignmentDao,
    private val transactionRunner: TransactionRunner = InlineTransactionRunner,
) : CourseRepository {

    /**
     * Caché en memoria de contenidos de curso.
     *
     * ## Cómo funciona
     *
     * `LruCache` (Least Recently Used) es una caché con un tamaño
     * máximo. Cuando se llena, expulsa la entrada menos recientemente
     * usada para hacer sitio a la nueva. Así la caché no crece
     * indefinidamente, aunque el usuario visite muchos cursos.
     *
     * ## Clave
     *
     * El `courseId` de Moodle. Los IDs son globales, así que dos
     * usuarios que vean el mismo curso comparten caché sin problema
     * (el contenido del curso es el mismo para todos).
     */
    private val courseContentsCache = LruCache<Int, List<CourseContent>>(MAX_CACHED_COURSES)

    // --- Observación de datos (lectura de Room) ---

    /**
     * Observa los cursos de un usuario desde Room y los mapea a
     * dominio.
     *
     * ## Qué hace
     *
     * Devuelve un `Flow` que emite la lista de cursos cada vez que
     * cambia algo en Room. Aprovecha para añadir el token a las URLs
     * de las imágenes (que se guardan sin token).
     *
     * @param userId ID del usuario logueado.
     * @return `Flow` que emite la lista de cursos.
     */
    override fun getCoursesFlow(userId: Int): Flow<List<Course>> =
        courseDao.getCoursesWithAssignments(userId).map { courseRelations ->
            val sessionAuth = currentAuth()
            courseRelations.map { courseRelation -> courseRelation.toDomainModel(sessionAuth) }
        }

    // --- Sincronización con la red ---

    /**
     * Sincroniza cursos y tareas del usuario desde Moodle y los
     * guarda en Room.
     *
     * ## Qué hace paso a paso
     *
     * 1. Descarga la lista de cursos en los que está matriculado el
     *    usuario (`core_enrol_get_users_courses`).
     * 2. Extrae los IDs de esos cursos.
     * 3. Convierte cada curso a una entidad de Room, guardando la URL
     *    de la portada **sin token**.
     * 4. Descarga las tareas de esos cursos
     *    (`mod_assign_get_assignments`). Si falla, se ignora y se
     *    guardan solo los cursos (las tareas se pueden recuperar en
     *    otra pasada).
     * 5. Dentro de una transacción: borra los cursos anteriores del
     *    usuario e inserta los nuevos. Si hay tareas, también se
     *    insertan.
     *
     * ## Por qué todo va en una transacción
     *
     * Porque si se borraran los cursos y luego fallara la inserción,
     * el usuario se quedaría sin cursos en la base de datos. Con una
     * transacción, o se hace todo, o no se hace nada.
     *
     * @param userId ID del usuario logueado.
     */
    override suspend fun refreshCourses(userId: Int) {
        val coursesRawJson = fetchEnrolledCoursesRaw(userId)
        val courseIds = coursesRawJson.map { courseJson -> courseJson.optInt("id") }

        val courseEntities = coursesRawJson.map { courseJson ->
            CourseEntity(
                id = courseJson.optInt("id"),
                fullname = courseJson.optString("fullname"),
                shortname = courseJson.optString("shortname"),
                summary = courseJson.optString("summary"),
                progress = courseJson.optInt("progress", -1).takeIf { it >= 0 },
                startDate = courseJson.optLong("startdate", 0).takeIf { it > 0 },
                endDate = courseJson.optLong("enddate", 0).takeIf { it > 0 },
                imageUrl = extractRawImageUrl(courseJson.optJSONArray("overviewfiles")),
                userId = userId,
            )
        }

        val assignmentEntities = fetchAssignmentEntities(courseIds)

        transactionRunner.run {
            courseDao.clearCoursesForUser(userId)
            courseDao.insertCourses(courseEntities)
            if (assignmentEntities != null) {
                assignmentDao.insertAssignments(assignmentEntities)
            }
        }
    }

    // --- Contenidos de curso ---

    /**
     * Devuelve los contenidos de un curso (secciones, módulos,
     * archivos), con caché en memoria.
     *
     * ## Comportamiento
     *
     * - Si `forceRefresh = false` y hay caché para `courseId`, se
     *   devuelve la caché sin tocar la red.
     * - Si `forceRefresh = true` o no hay caché, se va a la red y se
     *   actualiza la caché.
     *
     * ## Cuándo se usa `forceRefresh`
     *
     * Cuando el usuario hace pull-to-refresh en la pantalla de detalle
     * de un curso. El resto de aperturas usan la caché.
     *
     * @param courseId ID del curso.
     * @param forceRefresh Si `true`, ignora la caché.
     * @return Lista de secciones con módulos parseados.
     */
    override suspend fun getCourseContents(
        courseId: Int,
        forceRefresh: Boolean,
    ): List<CourseContent> {
        if (!forceRefresh) {
            courseContentsCache.get(courseId)?.let { cachedContents ->
                return cachedContents
            }
        }
        val fetchedContents = fetchCourseContents(courseId)
        courseContentsCache.put(courseId, fetchedContents)
        return fetchedContents
    }

    /**
     * Resuelve la URL externa real de un módulo de tipo `url`.
     *
     * ## Qué es un módulo `url` en Moodle
     *
     * Es un módulo que simplemente contiene un enlace a una web
     * externa (por ejemplo, un artículo, un vídeo, un documento en
     * Drive). Moodle no devuelve la URL externa en el listado de
     * contenidos del curso; hay que pedirla aparte con
     * `mod_url_get_urls_by_courses`.
     *
     * ## Manejo de errores
     *
     * Envuelto en `runCatching`, así que cualquier fallo (red,
     * parseo) devuelve `null`. La pantalla de detalle de curso trata
     * el `null` mostrando un toast genérico, pero no rompe.
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo `url`.
     * @param forceRefresh Ignorado actualmente (no hay caché de URLs).
     * @return URL externa, o `null` si no se encuentra o falla.
     */
    override suspend fun getUrlExternalUrl(
        courseId: Int,
        instanceId: Int,
        forceRefresh: Boolean,
    ): String? = runCatching {
        val requestParams = MoodleParams().apply {
            putArray("courseids", listOf(courseId))
        }
        val urlsResponseJson = JSONObject(callRest("mod_url_get_urls_by_courses", requestParams))
        findExternalUrl(urlsResponseJson.optJSONArray("urls"), instanceId)
    }.getOrNull()

    /**
     * Limpia las cachés en memoria del repositorio.
     *
     * ## Qué limpia
     *
     * Solo la caché de contenidos de curso. Room sigue teniendo los
     * datos persistidos (se limpia aparte con `LocalDataCleaner`).
     *
     * ## Cuándo se llama
     *
     * Actualmente no se llama desde ningún sitio. Se deja por simetría
     * con otros repositorios y para un futuro "borrar caché" manual.
     */
    override fun clearCache() {
        courseContentsCache.evictAll()
    }

    // --- Obtención de datos de red ---

    /**
     * Descarga los contenidos de un curso desde la red.
     *
     * @param courseId ID del curso.
     * @return Lista de secciones con módulos parseados.
     */
    private suspend fun fetchCourseContents(courseId: Int): List<CourseContent> {
        val requestParams = MoodleParams().apply { put("courseid", courseId) }
        val sectionsJson = JSONArray(callRest("core_course_get_contents", requestParams))
        return (0 until sectionsJson.length()).map { sectionIndex ->
            parseSection(sectionsJson.getJSONObject(sectionIndex))
        }
    }

    /**
     * Descarga la lista de cursos en los que está matriculado el
     * usuario.
     *
     * @param userId ID del usuario.
     * @return Lista de `JSONObject`, uno por curso.
     */
    private suspend fun fetchEnrolledCoursesRaw(userId: Int): List<JSONObject> {
        val requestParams = MoodleParams().apply { put("userid", userId) }
        val coursesJson = JSONArray(callRest("core_enrol_get_users_courses", requestParams))
        return (0 until coursesJson.length()).map { courseIndex ->
            coursesJson.getJSONObject(courseIndex)
        }
    }

    /**
     * Descarga las tareas de los cursos indicados y las convierte en
     * entidades de Room.
     *
     * ## Manejo de errores
     *
     * Devuelve `null` si la descarga falla. El llamador
     * ([refreshCourses]) interpreta `null` como "no se pudieron
     * obtener las tareas" y guarda solo los cursos. La próxima
     * sincronización volverá a intentarlo.
     *
     * @param courseIds IDs de los cursos.
     * @return Lista de entidades, o `null` si falló la descarga.
     */
    private suspend fun fetchAssignmentEntities(courseIds: List<Int>): List<AssignmentEntity>? {
        if (courseIds.isEmpty()) return emptyList()
        return runCatching {
            val requestParams = MoodleParams().apply {
                putArray("courseids", courseIds)
            }
            val assignmentsResponseJson = JSONObject(
                callRest("mod_assign_get_assignments", requestParams),
            )
            val coursesArray = assignmentsResponseJson.optJSONArray("courses")
                ?: return@runCatching emptyList()
            val assignmentEntities = mutableListOf<AssignmentEntity>()
            for (courseIndex in 0 until coursesArray.length()) {
                val courseJson = coursesArray.getJSONObject(courseIndex)
                val assignmentsArray = courseJson.optJSONArray("assignments")
                if (assignmentsArray != null) {
                    for (assignmentIndex in 0 until assignmentsArray.length()) {
                        assignmentEntities.add(
                            assignmentsArray.getJSONObject(assignmentIndex).toEntity(),
                        )
                    }
                }
            }
            assignmentEntities
        }.getOrNull()
    }

    /**
     * Ejecuta una función REST contra el cliente autenticado.
     *
     * @param function Nombre de la función de Moodle.
     * @param params Parámetros ya montados.
     * @return Cuerpo JSON de la respuesta.
     * @throws IllegalStateException si no hay sesión activa.
     */
    private suspend fun callRest(function: String, params: MoodleParams): String =
        sessionApi.requireApi().callREST(function, params)

    // --- Mapeos entidad → dominio ---

    /**
     * Devuelve el token y el host de la sesión activa.
     *
     * @return Par `(token, host)`, o `null` si no hay sesión.
     */
    private fun currentAuth(): Pair<String, String>? {
        val moodleApiClient = try {
            sessionApi.requireApi()
        } catch (exception: IllegalStateException) {
            return null
        }
        return moodleApiClient.token.value to moodleApiClient.config.moodleHost
    }

    /**
     * Mapea una relación de Room al modelo de dominio `Course`.
     *
     * ## Qué hace con la URL de la imagen
     *
     * En Room, la URL está guardada **sin token**. Aquí se le añade
     * con `appendMoodleToken`, limitado al host de Moodle. Si no hay
     * sesión activa, se deja la URL sin token (la imagen no se podrá
     * cargar, pero el resto del curso sí).
     *
     * @param sessionAuth Par `(token, host)` de la sesión activa, o
     *   `null`.
     * @return Curso de dominio.
     */
    private fun CourseWithAssignments.toDomainModel(sessionAuth: Pair<String, String>?): Course {
        val courseEntity = this.course
        return Course(
            id = courseEntity.id,
            fullname = courseEntity.fullname,
            shortname = courseEntity.shortname,
            summary = courseEntity.summary,
            progress = courseEntity.progress,
            startDate = courseEntity.startDate,
            endDate = courseEntity.endDate,
            imageUrl = courseEntity.imageUrl?.let { imageUrl ->
                if (sessionAuth == null) imageUrl
                else imageUrl.appendMoodleToken(sessionAuth.first, sessionAuth.second)
            },
        )
    }

    /**
     * Mapea un `JSONObject` de tarea al modelo de entidad Room.
     *
     * @receiver Objeto JSON con los campos de una tarea de Moodle.
     * @return Entidad lista para Room.
     */
    private fun JSONObject.toEntity(): AssignmentEntity = AssignmentEntity(
        id = optInt("id"),
        courseId = optInt("course"),
        name = optString("name"),
        description = optString("intro"),
        dueDate = optLong("duedate", 0).takeIf { it > 0 },
        allowSubmissionsFromDate = optLong("allowsubmissionsfromdate", 0).takeIf { it > 0 },
        grade = optDouble("grade", 0.0).takeIf { it.isNaN().not() },
        submissionStatus = null,
    )

    // --- Parseo de contenidos ---

    /**
     * Convierte una sección cruda de Moodle en `CourseContent`.
     *
     * ## Qué es una sección
     *
     * En Moodle, un curso se organiza en secciones (temas). Cada
     * sección agrupa módulos (tareas, foros, recursos…).
     *
     * @param sectionJson `JSONObject` de una sección.
     * @return Sección con módulos ya parseados.
     */
    private fun parseSection(sectionJson: JSONObject): CourseContent {
        val modulesArray = sectionJson.optJSONArray("modules") ?: JSONArray()
        val modules = (0 until modulesArray.length()).map { moduleIndex ->
            parseModule(modulesArray.getJSONObject(moduleIndex))
        }
        return CourseContent(
            id = sectionJson.optInt("id"),
            name = sectionJson.optString("name"),
            modules = modules,
        )
    }

    /**
     * Convierte un módulo crudo de Moodle en `CourseModule`.
     *
     * ## Qué es un módulo
     *
     * Es cada actividad o recurso de una sección: una tarea, un foro,
     * un PDF, un enlace, etc. Cada uno tiene un tipo (`modname`) que
     * determina cómo se maneja en la UI.
     *
     * @param moduleJson `JSONObject` de un módulo.
     * @return Módulo con archivos ya resueltos.
     */
    private fun parseModule(moduleJson: JSONObject): CourseModule = CourseModule(
        id = moduleJson.optInt("id"),
        instance = moduleJson.optInt("instance"),
        name = moduleJson.optString("name"),
        modname = moduleJson.optString("modname"),
        url = moduleJson.optString("url").takeIf { it.isNotEmpty() },
        description = moduleJson.optString("description").takeIf { it.isNotEmpty() },
        files = parseModuleFiles(moduleJson.optJSONArray("contents")),
    )

    /**
     * Resuelve la lista de archivos de un módulo.
     *
     * ## Qué pasa si no hay sesión
     *
     * Si no hay sesión activa, se devuelve una lista vacía. Los
     * módulos se ven, pero sin archivos descargables. Es preferible a
     * romper el listado entero.
     *
     * @param contentsArray Array de contenidos del módulo, o `null`.
     * @return Lista de archivos descargables.
     */
    private fun parseModuleFiles(contentsArray: JSONArray?): List<CourseFile> {
        if (contentsArray == null) return emptyList()
        val (token, host) = currentAuth() ?: return emptyList()
        return (0 until contentsArray.length()).mapNotNull { fileIndex ->
            parseModuleFile(contentsArray.getJSONObject(fileIndex), token, host)
        }
    }

    /**
     * Convierte un archivo crudo de Moodle en `CourseFile`.
     *
     * @param fileJson `JSONObject` de archivo.
     * @param token Token de la sesión activa.
     * @param host Host de Moodle (el token solo se añade a este host).
     * @return Archivo de dominio, o `null` si no tiene URL.
     */
    private fun parseModuleFile(fileJson: JSONObject, token: String, host: String): CourseFile? {
        val rawFileUrl = fileJson.optString("fileurl", "")
        if (rawFileUrl.isEmpty()) return null
        return CourseFile(
            fileName = fileJson.optString("filename"),
            fileUrl = rawFileUrl.appendMoodleToken(token, host),
            fileSize = fileJson.optLong("filesize", 0),
            mimeType = fileJson.optString("mimetype").takeIf { it.isNotEmpty() },
            timeModified = fileJson.optLong("timemodified", 0),
        )
    }

    /**
     * Busca la URL externa de una instancia `url` en el array de URLs.
     *
     * @param urlsArray Array de URLs, o `null`.
     * @param instanceId ID de instancia del módulo `url`.
     * @return URL externa, o `null` si no se encuentra.
     */
    private fun findExternalUrl(urlsArray: JSONArray?, instanceId: Int): String? {
        if (urlsArray == null) return null
        return (0 until urlsArray.length())
            .map { urlIndex -> urlsArray.getJSONObject(urlIndex) }
            .firstOrNull { urlJson -> urlJson.optInt("id") == instanceId }
            ?.optString("externalurl")
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extrae la URL de portada del curso **sin** `wstoken`.
     *
     * ## Por qué sin token
     *
     * Porque la URL se va a guardar en Room. Si se guardara con
     * token, el token quedaría en claro en la base de datos. El token
     * se añade al vuelo cuando se lee (en
     * [CourseWithAssignments.toDomainModel]).
     *
     * @param overviewFilesArray Array `overviewfiles` del curso, o
     *   `null`.
     * @return URL de portada, o `null` si no hay.
     */
    private fun extractRawImageUrl(overviewFilesArray: JSONArray?): String? {
        if (overviewFilesArray == null || overviewFilesArray.length() == 0) return null
        return overviewFilesArray
            .getJSONObject(0)
            .optString("fileurl", "")
            .takeIf { it.isNotEmpty() }
    }

    private companion object {
        /**
         * Tamaño máximo de [courseContentsCache].
         *
         * 8 cursos es suficiente para una sesión típica (un alumno de
         * FP suele tener 5-7 asignaturas). Más allá, se desaloja el
         * menos usado recientemente.
         */
        const val MAX_CACHED_COURSES = 8
    }
}