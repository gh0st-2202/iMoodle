package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.model.grade.GradeItem
import onion.gh0st.iMoodle.domain.repository.grade.GradeRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [GradeRepository] sobre la API REST de
 * Moodle.
 *
 * ## Qué pide
 *
 * `gradereport_user_get_grade_items` con el curso y, cuando se conoce,
 * el alumno. Moodle responde con `usergrades[]`; dentro de cada
 * elemento viene `gradeitems[]` con una fila por actividad y otra para
 * el total del curso.
 *
 * ## Qué se descarta
 *
 * Moodle devuelve muchos campos de configuración del libro de
 * calificaciones (pesos, categorías, agregación…) que el alumno no
 * necesita. Aquí solo se conservan los que se pintan: nombre, nota,
 * escala, estado y comentario de retroalimentación.
 *
 * @param sessionApi Fuente del cliente REST autenticado.
 */
@Singleton
class MoodleGradeRepository @Inject constructor(
    private val sessionApi: SessionApiProvider,
) : GradeRepository {

    /**
     * Devuelve las notas de un curso.
     *
     * Si Moodle devuelve varias entradas para el mismo usuario (no
     * debería), se toma la primera. Si no devuelve ninguna, se lanza
     * una excepción para que la pantalla muestre el error en lugar de
     * una lista vacía sin explicación.
     *
     * @param courseId Identificador del curso.
     * @param userId Identificador del alumno, o `null` para usar el del token.
     * @return Notas del curso.
     * @throws IllegalStateException si Moodle no devuelve notas.
     */
    override suspend fun getCourseGrades(courseId: Int, userId: Int?): CourseGrade {
        val requestParams = MoodleParams().apply {
            put("courseid", courseId)
            put("userid", userId)
        }
        val responseJson = JSONObject(
            sessionApi.requireApi().callREST("gradereport_user_get_grade_items", requestParams),
        )
        val userGradeJson = responseJson.optJSONArray("usergrades").objects().firstOrNull()
            ?: error("Moodle no devolvió las notas del curso")
        return parseCourseGrade(userGradeJson, courseId)
    }

    /**
     * Convierte la entrada de un usuario en [CourseGrade].
     *
     * @param userGradeJson Objeto `usergrades[i]`.
     * @param fallbackCourseId Curso pedido, por si Moodle no lo repite.
     * @return Las notas del curso.
     */
    private fun parseCourseGrade(userGradeJson: JSONObject, fallbackCourseId: Int): CourseGrade =
        CourseGrade(
            courseId = userGradeJson.optInt("courseid", fallbackCourseId),
            items = userGradeJson.optJSONArray("gradeitems").objects().map(::parseGradeItem),
        )

    /**
     * Convierte una fila del libro de calificaciones en [GradeItem].
     *
     * @param itemJson Objeto `gradeitems[i]`.
     * @return La fila interpretada.
     */
    private fun parseGradeItem(itemJson: JSONObject): GradeItem = GradeItem(
        id = itemJson.optInt("id"),
        name = itemJson.optString("itemname"),
        itemType = itemJson.optString("itemtype"),
        itemModule = itemJson.optionalString("itemmodule"),
        cmid = itemJson.optInt("cmid", 0).takeIf { it > 0 },
        itemInstance = itemJson.optInt("iteminstance", 0).takeIf { it > 0 },
        gradeFormatted = itemJson.optionalString("gradeformatted"),
        gradeRaw = itemJson.optionalGrade(),
        gradeMin = itemJson.optionalDouble("grademin"),
        gradeMax = itemJson.optionalDouble("grademax"),
        isHidden = itemJson.optInt("gradeishidden", 0) == 1,
        isLocked = itemJson.optInt("gradeislocked", 0) == 1,
        feedback = itemJson.optionalString("feedback"),
    )

    /**
     * Lee la nota numérica de una fila.
     *
     * Moodle puede devolver `gradenull` como `true` cuando la actividad
     * todavía no tiene nota. En ese caso se devuelve `null` en vez de
     * un `0` que se confundiría con un cero real.
     *
     * @receiver Fila del libro de calificaciones.
     * @return La nota, o `null` si no hay.
     */
    private fun JSONObject.optionalGrade(): Double? {
        if (optBoolean("gradenull", false)) return null
        if (isNull("graderaw")) return null
        return optDouble("graderaw").takeIf { grade -> !grade.isNaN() }
    }

    /**
     * Lee una cadena opcional de un campo.
     *
     * `optString` devuelve la cadena `"null"` cuando el campo existe pero
     * vale `null`, así que se comprueba antes con [isNull].
     *
     * @receiver Objeto JSON.
     * @param key Nombre del campo.
     * @return El texto, o `null` si falta, es nulo o está vacío.
     */
    private fun JSONObject.optionalString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { text -> text.isNotEmpty() }
    }

    /**
     * Lee un número opcional de un campo.
     *
     * @receiver Objeto JSON.
     * @param key Nombre del campo.
     * @return El número, o `null` si falta o no es válido.
     */
    private fun JSONObject.optionalDouble(key: String): Double? {
        if (isNull(key)) return null
        return optDouble(key).takeIf { number -> !number.isNaN() }
    }
}
