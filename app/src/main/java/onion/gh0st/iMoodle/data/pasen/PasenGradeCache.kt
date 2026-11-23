package onion.gh0st.iMoodle.data.pasen

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.pasen.PasenConvocatoria
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché de las calificaciones de iPasen, indexada por usuario de la
 * Junta.
 *
 * ## Por qué existe
 *
 * Las calificaciones de iPasen apenas cambian entre sesiones (solo cuando
 * el centro las publica). Pedirlas cada vez implica un login completo a
 * iPasen (con su espera y su tráfico), así que se guardan en el
 * dispositivo y se reutilizan.
 *
 * ## Estrategia
 *
 * Igual que [onion.gh0st.iMoodle.data.schedule.ScheduleCache]: memoria
 * (instantánea) + disco (`SharedPreferences`, sobrevive a reinicios),
 * indexada por `username` de la Junta. No se guardan credenciales ni
 * tokens.
 *
 * @param context Contexto para acceder a `SharedPreferences`.
 */
@Singleton
class PasenGradeCache @Inject constructor(
    @ApplicationContext context: Context,
) {

    /** Disco: sobrevive al reinicio y se consulta en segundo plano. */
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    /** Memoria: respuesta inmediata mientras la app sigue abierta. */
    private val memoryCache = ConcurrentHashMap<String, List<PasenConvocatoria>>()

    /**
     * Devuelve las calificaciones cacheadas de [username].
     *
     * Se mira primero en memoria y, si no están, en disco (leyendo en
     * `Dispatchers.IO` para no bloquear la UI).
     *
     * @param username Usuario de la Junta, usado como clave.
     * @return Convocatorias guardadas, o `null` si no hay caché.
     */
    suspend fun get(username: String): List<PasenConvocatoria>? {
        memoryCache[username]?.let { cached -> return cached }
        return withContext(Dispatchers.IO) {
            readFromPreferences(username)?.also { loaded ->
                memoryCache[username] = loaded
            }
        }
    }

    /**
     * Guarda las calificaciones de [username] en memoria y en disco.
     *
     * @param username Usuario de la Junta, usado como clave.
     * @param grades Convocatorias recién descargadas de iPasen.
     */
    suspend fun put(username: String, grades: List<PasenConvocatoria>) {
        memoryCache[username] = grades
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(gradesPreferenceKey(username), serializeGrades(grades))
                putLong(updatedAtPreferenceKey(username), System.currentTimeMillis())
            }
        }
    }

    /**
     * Borra la caché de un usuario concreto, en memoria y en disco.
     *
     * @param username Usuario de la Junta cuya caché se descarta.
     */
    suspend fun clear(username: String) {
        memoryCache.remove(username)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                remove(gradesPreferenceKey(username))
                remove(updatedAtPreferenceKey(username))
            }
        }
    }

    /**
     * Borra todas las calificaciones cacheadas.
     *
     * Se usa al cerrar sesión: los datos personales no deben quedar en
     * el dispositivo para el siguiente usuario (§7.107).
     */
    suspend fun clearAll() {
        memoryCache.clear()
        withContext(Dispatchers.IO) {
            sharedPreferences.edit(commit = true) { clear() }
        }
    }

    /**
     * Lee y deserializa las convocatorias guardadas en disco.
     *
     * Un JSON corrupto se trata como «sin caché»: se descarta y se
     * vuelve a consultar iPasen.
     *
     * @param username Usuario de la Junta, usado como clave.
     * @return Convocatorias leídas, o `null` si no hay datos válidos.
     */
    private fun readFromPreferences(username: String): List<PasenConvocatoria>? {
        val rawJson = sharedPreferences.getString(gradesPreferenceKey(username), null)
            ?: return null
        return runCatching {
            val array = JSONArray(rawJson)
            (0 until array.length()).map { index ->
                array.getJSONObject(index).toConvocatoria()
            }
        }.getOrNull()
    }

    /**
     * Convierte las convocatorias a JSON para guardarlas en disco.
     *
     * @param grades Convocatorias a serializar.
     * @return Texto JSON del array de convocatorias.
     */
    private fun serializeGrades(grades: List<PasenConvocatoria>): String {
        val array = JSONArray()
        grades.forEach { grade -> array.put(grade.toJsonObject()) }
        return array.toString()
    }

    /**
     * Serializa una convocatoria y sus asignaturas a JSON.
     *
     * @receiver Convocatoria a guardar.
     * @return Objeto JSON con los campos de la convocatoria.
     */
    private fun PasenConvocatoria.toJsonObject() = JSONObject().apply {
        put("course", course)
        put("isFinished", isFinished)
        put("dateStart", dateStart)
        put("dateFinish", dateFinish)
        put("isActive", isActive)
        put("idCenter", idCenter)
        val subjectsArray = JSONArray()
        subjects.forEach { subject -> subjectsArray.put(subject.toJsonObject()) }
        put("subjects", subjectsArray)
    }

    /**
     * Reconstruye una convocatoria desde su JSON, de forma defensiva.
     *
     * @receiver Objeto JSON leído del disco.
     * @return Convocatoria con sus asignaturas (vacía si no hay campo).
     */
    private fun JSONObject.toConvocatoria() = PasenConvocatoria(
        course = optString("course"),
        isFinished = optBoolean("isFinished", false),
        dateStart = optString("dateStart"),
        dateFinish = optString("dateFinish"),
        isActive = optBoolean("isActive", false),
        idCenter = optInt("idCenter", 0),
        subjects = optJSONArray("subjects")?.objects()?.map { it.toSubject() } ?: emptyList(),
    )

    /**
     * Serializa una nota de asignatura a JSON.
     *
     * @receiver Nota a guardar.
     * @return Objeto JSON con la nota y los campos extra de iPasen.
     */
    private fun PasenSubjectGrade.toJsonObject() = JSONObject().apply {
        put("name", name)
        putOpt("grade", grade)
        putOpt("average", average)
        putOpt("annotation", annotation)
        val fieldsObject = JSONObject()
        fields.forEach { (key, value) -> fieldsObject.put(key, value) }
        put("fields", fieldsObject)
    }

    /**
     * Reconstruye una nota de asignatura desde su JSON.
     *
     * @receiver Objeto JSON leído del disco.
     * @return Nota con los campos en blanco convertidos a `null`.
     */
    private fun JSONObject.toSubject() = PasenSubjectGrade(
        name = optString("name"),
        grade = optString("grade").takeIf { it.isNotBlank() },
        average = optString("average").takeIf { it.isNotBlank() },
        annotation = optString("annotation").takeIf { it.isNotBlank() },
        fields = optJSONObject("fields")?.let { obj ->
            obj.keys().asSequence().map { key -> key to obj.optString(key) }.toList()
        } ?: emptyList(),
    )

    /**
     * Convierte un array JSON en objetos, descartando elementos no objeto.
     *
     * @receiver Array leído del disco, o `null`.
     * @return Lista de objetos JSON válidos.
     */
    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }

    /**
     * Clave de las convocatorias de un usuario.
     *
     * @param username Usuario de la Junta.
     * @return Clave dentro del fichero de preferencias.
     */
    private fun gradesPreferenceKey(username: String) = "grades_$username"

    /**
     * Clave de la fecha de actualización de un usuario.
     *
     * @param username Usuario de la Junta.
     * @return Clave dentro del fichero de preferencias.
     */
    private fun updatedAtPreferenceKey(username: String) = "updatedAt_$username"

    private companion object {
        /** Fichero privado donde se guardan las convocatorias por usuario. */
        const val PREFERENCES_FILE_NAME = "imoodle_pasen_grades_cache"
    }
}