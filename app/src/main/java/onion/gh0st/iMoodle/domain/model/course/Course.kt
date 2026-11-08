package onion.gh0st.iMoodle.domain.model.course

/**
 * Representación de un curso de Moodle en el dominio de la app.
 *
 * ## Qué es un curso
 *
 * En Moodle, un curso es una asignatura (o un espacio formativo) en
 * el que el alumno está matriculado. Por ejemplo, "Acceso a datos",
 * "Desarrollo de interfaces", "Programación de servicios y procesos".
 *
 * Cada curso tiene:
 *
 * - Un **nombre completo** (el que se muestra en la UI).
 * - Un **nombre corto** (un código que usa Moodle internamente).
 * - Un **resumen** (opcional, con formato HTML).
 * - Un **progreso** del alumno (0-100, si Moodle lo informa).
 * - Fechas de inicio y fin (opcionales).
 * - Una **imagen de portada** (opcional).
 *
 * ## Diferencia con `CourseContent`
 *
 * Este modelo es el del curso en sí (metadatos). El contenido del
 * curso (secciones, módulos, archivos) es un modelo distinto:
 * `CourseContent`. Se separan porque se cargan en momentos distintos:
 *
 * - El listado de cursos carga solo `Course` (metadatos).
 * - Al entrar en un curso, se carga `CourseContent` (secciones y
 *   módulos).
 *
 * Separarlos permite que el listado sea rápido y solo cargue el
 * contenido cuando el usuario entra.
 *
 * ## Sobre la URL de la imagen
 *
 * El campo `imageUrl` puede venir de dos fuentes:
 *
 * - **Una URL real** de portada del curso (si el profesor la ha
 *   configurado en Moodle).
 * - **`null`** si el curso no tiene portada. En ese caso, la UI
 *   genera un patrón SVG determinista a partir del nombre y el ID del
 *   curso (con `MoodleGeoPattern`), para que la lista no se vea
 *   plana.
 *
 * ## Sobre el token
 *
 * La URL de la imagen **no** lleva token en este modelo. El token se
 * añade al vuelo en el repositorio cuando se construye el `Course`,
 * porque el token que se guarda en Room es sin token (por
 * seguridad). Si la UI necesita cargar la imagen, ya la recibe con
 * token desde el repositorio.
 *
 * @property id ID del curso en Moodle. Es único por instancia de
 *   Moodle (no entre provincias).
 * @property fullname Nombre completo del curso. Es el que se muestra
 *   al usuario.
 * @property shortname Nombre corto del curso. Es un código que usa
 *   Moodle internamente, y que a veces se muestra en la UI como
 *   "abreviatura".
 * @property summary Resumen del curso, con formato HTML tal cual lo
 *   envía Moodle.
 * @property progress Progreso del usuario en el curso (0-100), o
 *   `null` si Moodle no lo informa. Un valor de 50 significa que el
 *   usuario ha completado la mitad de las actividades del curso.
 * @property startDate Fecha de inicio del curso en **segundos**, o
 *   `null` si no está definida.
 * @property endDate Fecha de fin del curso en **segundos**, o
 *   `null` si no está definida.
 * @property imageUrl URL de la imagen de portada, o `null` si el
 *   curso no tiene. Si la tiene, **no** lleva token: el token se
 *   añade al vuelo en el repositorio.
 */
data class Course(
    val id: Int,
    val fullname: String,
    val shortname: String,
    val summary: String,
    val progress: Int?,
    val startDate: Long?,
    val endDate: Long?,
    val imageUrl: String?,
)