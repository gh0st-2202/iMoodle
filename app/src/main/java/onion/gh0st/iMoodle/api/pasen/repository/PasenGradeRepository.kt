package onion.gh0st.iMoodle.api.pasen.repository

import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import onion.gh0st.iMoodle.api.pasen.network.PasenApiException
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade
import onion.gh0st.iMoodle.domain.repository.pasen.PasenGradeRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [PasenGradeRepository] sobre la API de iPasen.
 *
 * ## Qué hace
 *
 * Hace login a iPasen, pide la matrícula y descarga las convocatorias
 * de evaluación. El JSON se parsea en [parseConvocatorias].
 *
 * ## Por qué no hay caché
 *
 * Las notas cambian cuando el centro las publica. Se piden frescas
 * cada vez que se abre la pantalla.
 *
 * @param apiClient Cliente HTTP de iPasen.
 * @param credentialsStore Almacén cifrado de credenciales de la Junta.
 */
@Singleton
class PasenGradeRepository @Inject constructor(
    private val apiClient: PasenApiClient,
    private val credentialsStore: CredentialsStore,
) : PasenGradeRepository {

    /**
     * Devuelve las convocatorias de evaluación.
     *
     * @param forceRefresh Ignorado (siempre se consulta a iPasen).
     * @return Lista de convocatorias.
     * @throws IllegalStateException si no hay credenciales guardadas.
     * @throws PasenApiException si falla la red o el parseo.
     */
    override suspend fun getGrades(forceRefresh: Boolean): List<PasenConvocatoria> {
        val credentials = credentialsStore.load()
            ?: throw IllegalStateException("No hay credenciales guardadas para iPasen")
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

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }
}
