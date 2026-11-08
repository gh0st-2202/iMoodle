package onion.gh0st.iMoodle.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché de los datos personales de Pasen, indexada por usuario de la
 * Junta.
 *
 * ## Qué problema resuelve
 *
 * Obtener los datos personales del alumno desde Pasen (centro, fecha
 * de nacimiento, edad, sexo) implica un login completo contra los
 * servidores de la Junta. Si cada vez que el usuario abre la pestaña
 * de perfil se hiciera ese login, la experiencia sería horrible y la
 * Junta podría considerar la app abusiva.
 *
 * Para evitarlo, esta clase guarda los cuatro campos en el
 * dispositivo. La primera vez que el usuario abre el perfil, se piden
 * a Pasen y se guardan. Las siguientes veces, se leen de aquí:
 * instantáneo y sin red.
 *
 * ## Por qué la caché es por `username` y no por otra cosa
 *
 * Mismo motivo que `ScheduleCache`: el `userId` de Moodle no es único
 * entre provincias, la provincia no distingue cuentas, y la sesión se
 * perdería al reiniciar. El `username` de la Junta es único, estable
 * y no cambia.
 *
 * ## Qué guarda y dónde
 *
 * - **En memoria** (un `Map`): acceso instantáneo durante la sesión.
 * - **En disco** (`SharedPreferences`): sobrevive a reinicios. Al
 *   arrancar, se lee de aquí.
 *
 * ## Qué NO guarda
 *
 * **No guarda el `User` completo**. Solo los cuatro campos que aporta
 * Pasen:
 *
 * - `centerName`: nombre del centro.
 * - `birthDate`: fecha de nacimiento.
 * - `age`: edad.
 * - `gender`: sexo.
 *
 * El resto de campos del `User` (nombre, apellidos, email, avatar)
 * los aporta Moodle y se cachean en `UserCache` (en memoria). Esta
 * separación es importante: cada caché guarda lo que su proveedor
 * aporta, sin mezclar.
 *
 * ## Sobre la concurrencia
 *
 * Igual que en `ScheduleCache`: la caché en memoria se actualiza
 * antes de suspender para escribir en disco. No se necesita `Mutex`
 * porque el peor caso es un trabajo redundante, no un dato
 * incorrecto.
 */
@Singleton
class UserProfileCache @Inject constructor(
    @ApplicationContext context: Context,
) {

    /**
     * Preferencias compartidas donde se guarda el perfil en disco.
     *
     * Se abre una sola vez al crear la instancia (la clase es
     * `@Singleton`).
     */
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Los cuatro campos de Pasen que se cachean.
     *
     * ## Por qué es una `data class` interna
     *
     * Porque es un detalle de implementación de esta caché. Quien
     * consume la caché (el `PasenUserProfileRepository`) trabaja con
     * el `User` de dominio, no con este contenedor. Exponerlo como
     * `public` obligaría a los consumidores a conocerlo, cuando no lo
     * necesitan.
     *
     * @property centerName Nombre del centro educativo.
     * @property birthDate Fecha de nacimiento (formato `dd/MM/yyyy`).
     * @property age Edad formateada (por ejemplo, `"21 años"`).
     * @property gender Sexo (`"Hombre"`, `"Mujer"`).
     */
    data class CachedProfile(
        val centerName: String,
        val birthDate: String,
        val age: String,
        val gender: String,
    )

    /**
     * Caché en memoria, indexada por `username` de la Junta.
     *
     * ## Por qué es `@Volatile`
     *
     * Porque puede ser leída y escrita desde distintos hilos.
     * `@Volatile` garantiza visibilidad entre hilos sin necesidad de
     * sincronización explícita.
     *
     * ## Por qué es inmutable
     *
     * Se reemplaza entero en cada escritura, igual que en
     * `ScheduleCache`. Los `Map` inmutables son más seguros y fáciles
     * de razonar.
     */
    @Volatile
    private var memoryCache: Map<String, CachedProfile> = emptyMap()

    /**
     * Devuelve el perfil cacheado de un usuario, o `null` si no hay.
     *
     * ## Cómo busca
     *
     * 1. Mira primero en memoria (instantáneo).
     * 2. Si no está, lee de disco.
     * 3. Si lo encuentra en disco, actualiza la memoria.
     *
     * @param username Usuario de la Junta (NIF/NIE/IdEA).
     * @return El perfil cacheado, o `null` si no hay.
     */
    suspend fun get(username: String): CachedProfile? {
        memoryCache[username]?.let { cachedProfile -> return cachedProfile }
        return withContext(Dispatchers.IO) {
            readFromPreferences(username)?.also { loadedProfile ->
                memoryCache = memoryCache + (username to loadedProfile)
            }
        }
    }

    /**
     * Guarda el perfil de un usuario en memoria y en disco.
     *
     * @param username Usuario de la Junta.
     * @param profile Datos a persistir.
     */
    suspend fun put(username: String, profile: CachedProfile) {
        memoryCache = memoryCache + (username to profile)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(profilePreferenceKey(username), serializeProfile(profile))
                .putLong(updatedAtPreferenceKey(username), System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Borra el perfil cacheado de un usuario concreto.
     *
     * ## Cuándo se llama
     *
     * En el flujo normal, **nunca**. La caché está indexada por
     * `username`, así que el logout no necesita limpiarla. Pero el
     * método está disponible para:
     *
     * - Un futuro "borrar caché" manual desde ajustes.
     * - Tests, para aislar casos.
     *
     * @param username Usuario de la Junta.
     */
    suspend fun clear(username: String) {
        memoryCache = memoryCache - username
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .remove(profilePreferenceKey(username))
                .remove(updatedAtPreferenceKey(username))
                .apply()
        }
    }

    // --- Serialización ---

    /**
     * Lee el perfil de un usuario desde `SharedPreferences`.
     *
     * ## Manejo de datos corruptos
     *
     * La lectura se envuelve en `runCatching`. Si el JSON está
     * corrupto (porque una escritura se interrumpió), la función
     * devuelve `null` en lugar de lanzar una excepción. El llamador
     * tratará `null` como "no hay caché" y volverá a pedir los datos
     * a Pasen.
     *
     * @param username Usuario de la Junta.
     * @return El perfil cacheado, o `null` si no hay o está corrupto.
     */
    private fun readFromPreferences(username: String): CachedProfile? {
        val rawJson = sharedPreferences.getString(profilePreferenceKey(username), null)
            ?: return null
        return runCatching {
            val profileJson = JSONObject(rawJson)
            CachedProfile(
                centerName = profileJson.getString("centerName"),
                birthDate = profileJson.getString("birthDate"),
                age = profileJson.getString("age"),
                gender = profileJson.getString("gender"),
            )
        }.getOrNull()
    }

    /**
     * Serializa un perfil a JSON.
     *
     * @param profile Perfil a serializar.
     * @return Cadena JSON con los cuatro campos.
     */
    private fun serializeProfile(profile: CachedProfile): String = JSONObject().apply {
        put("centerName", profile.centerName)
        put("birthDate", profile.birthDate)
        put("age", profile.age)
        put("gender", profile.gender)
    }.toString()

    /**
     * Compone la clave de `SharedPreferences` para el perfil de un
     * usuario.
     *
     * @param username Usuario de la Junta.
     * @return La clave, por ejemplo `"profile_jdoe"`.
     */
    private fun profilePreferenceKey(username: String) = "profile_$username"

    /**
     * Compone la clave de `SharedPreferences` para el timestamp del
     * último guardado de un usuario.
     *
     * @param username Usuario de la Junta.
     * @return La clave, por ejemplo `"updatedAt_jdoe"`.
     */
    private fun updatedAtPreferenceKey(username: String) = "updatedAt_$username"

    private companion object {
        /**
         * Nombre del fichero de `SharedPreferences`.
         *
         * Se elige un nombre descriptivo para que, al inspeccionar el
         * sistema de ficheros, quede claro qué contiene.
         */
        const val PREFERENCES_FILE_NAME = "imoodle_user_profile_cache"
    }
}