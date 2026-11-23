package onion.gh0st.iMoodle.api.pasen.repository

import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import onion.gh0st.iMoodle.api.pasen.network.PasenApiException
import onion.gh0st.iMoodle.data.pasen.PasenGradeCache
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.repository.pasen.PasenGradeRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [PasenGradeRepository] sobre la API de iPasen,
 * con caché por usuario.
 *
 * @param apiClient Cliente HTTP de iPasen.
 * @param credentialsStore Almacén cifrado de credenciales de la Junta.
 * @param gradeCache Caché por usuario de las calificaciones.
 */
@Singleton
class PasenGradeRepository @Inject constructor(
    private val apiClient: PasenApiClient,
    private val credentialsStore: CredentialsStore,
    private val gradeCache: PasenGradeCache,
) : PasenGradeRepository {

    /**
     * Devuelve las convocatorias de evaluación.
     *
     * ## Estrategia cache-first
     *
     * - Si `forceRefresh = false` y hay caché para el usuario actual, se
     *   devuelve la caché sin tocar la red.
     * - Si no hay caché, o `forceRefresh = true`, se va a iPasen, se
     *   parsea y se guarda en caché.
     *
     * @param forceRefresh Si `true`, ignora la caché y va a la red.
     * @return Lista de convocatorias.
     * @throws IllegalStateException si no hay credenciales guardadas.
     * @throws PasenApiException si falla la red o el parseo.
     */
    override suspend fun getGrades(forceRefresh: Boolean): List<PasenConvocatoria> {
        val credentials = credentialsStore.load()
            ?: throw IllegalStateException("No hay credenciales guardadas para iPasen")

        if (!forceRefresh) {
            gradeCache.get(credentials.username)?.let { cachedGrades ->
                return cachedGrades
            }
        }

        val freshGrades = fetchFromPasen(credentials)
        gradeCache.put(credentials.username, freshGrades)
        return freshGrades
    }

    /**
     * Descarga las calificaciones desde iPasen con un login nuevo.
     *
     * @param credentials Credenciales de la Junta guardadas.
     * @return Las convocatorias ya interpretadas.
     * @throws PasenApiException si falla cualquier paso contra iPasen.
     */
    private suspend fun fetchFromPasen(credentials: JuntaCredentials): List<PasenConvocatoria> {
        val token = apiClient.login(credentials)
        val sessionInfo = apiClient.getSessionInfo(token)
        val rawJson = apiClient.getNotasConvocatorias(token, sessionInfo.id)
        return parseConvocatorias(rawJson)
    }

    /**
     * Interpreta el JSON de iPasen.
     *
     * iPasen envuelve los datos en `{ success, data: [...] }`. Si la
     * respuesta no es exitosa o no trae datos, se devuelve lista vacía.
     *
     * @param rawJson Cuerpo de la respuesta.
     * @return Convocatorias interpretadas.
     */
    private fun parseConvocatorias(rawJson: String): List<PasenConvocatoria> {
        val root = JSONObject(rawJson)
        if (!root.optBoolean("success", true)) return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        return data.objects().map(::parseConvocatoria)
    }

    /**
     * Interpreta una convocatoria.
     *
     * @param json Objeto de la convocatoria.
     * @return La convocatoria interpretada.
     */
    private fun parseConvocatoria(json: JSONObject): PasenConvocatoria = PasenConvocatoria(
        course = json.optString("course"),
        isFinished = json.optBoolean("isFinished", false),
        dateStart = json.optString("dateStart"),
        dateFinish = json.optString("dateFinish"),
        isActive = json.optBoolean("isActive", false),
        idCenter = json.optInt("idCenter", 0),
        subjects = json.optJSONArray("subjects").objects().map(::parseSubject),
    )

    /**
     * Interpreta una asignatura.
     *
     * ## Estrategia flexible
     *
     * iPasen no documenta con precisión los campos de cada asignatura.
     * Por eso se recorren **todas** las claves del objeto y se guardan
     * en [PasenSubjectGrade.fields], intentando además reconocer los
     * nombres más habituales para la nota, la media y la anotación.
     *
     * @param json Objeto de la asignatura.
     * @return La asignatura interpretada.
     */
    private fun parseSubject(json: JSONObject): PasenSubjectGrade {
        val name = firstString(json, "subject", "name", "materia", "D_MATERIA", "asignatura")
            ?: json.keys().asSequence().firstOrNull { key -> json.opt(key) is String }
                ?.let { key -> json.optString(key) }
            ?: ""
        val grade = firstString(json, "grade", "nota", "NOTA", "mark", "value", "calificacion", "calificación")
        val average = firstString(json, "average", "media", "averageGrade", "mediaClase")
        val annotation = firstString(json, "annotation", "anotacion", "anotación", "observacion", "observación", "comments")
        val fields = json.keys().asSequence().mapNotNull { key ->
            val value = json.opt(key)
            if (value == null || value == JSONObject.NULL) null
            else key to value.toString()
        }.filter { (key, _) -> key !in setOf("subject", "name", "materia", "D_MATERIA", "asignatura") }
            .toList()
        return PasenSubjectGrade(name, grade, average, annotation, fields)
    }

    /**
     * Devuelve el primer valor de texto no vacío entre las claves dadas.
     *
     * @param json Objeto JSON.
     * @param keys Claves candidatas, en orden de preferencia.
     * @return El texto, o `null` si ninguna clave tiene valor.
     */
    private fun firstString(json: JSONObject, vararg keys: String): String? =
        keys.asSequence()
            .mapNotNull { key -> json.optString(key).takeIf { text -> text.isNotBlank() } }
            .firstOrNull()

    /**
     * Convierte un array JSON en objetos, descartando los elementos que
     * no son objetos.
     *
     * @receiver Array leído de iPasen, o `null`.
     * @return Lista de objetos JSON válidos.
     */
    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }
}
