package onion.gh0st.iMoodle.data.schedule

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché del horario del alumno, indexada por usuario de la Junta.
 *
 * ## Qué problema resuelve
 *
 * Obtener el horario de Pasen no es gratis: implica un login completo
 * contra los servidores de la Junta (con su espera de varios segundos
 * y su tráfico de red). Si cada vez que el usuario entra en la
 * pestaña de horario se hiciera ese login, la experiencia sería
 * horrible y, además, la Junta podría considerar la app abusiva.
 *
 * Para evitarlo, esta clase guarda el horario en el dispositivo. La
 * primera vez que el usuario entra en la pestaña, se pide a Pasen y
 * se guarda. Las siguientes veces, se lee de aquí: instantáneo y sin
 * red.
 *
 * ## Por qué la caché es por `username` y no por otra cosa
 *
 * Hay que elegir con cuidado la "clave" de la caché. Las opciones
 * obvias y por qué no sirven:
 *
 * - **Por `userId` de Moodle**: no es único entre provincias. Cada
 *   instancia de Moodle tiene su propia numeración, así que dos
 *   alumnos de provincias distintas podrían tener el mismo
 *   `userId` y compartir caché sin querer.
 * - **Por provincia**: no distingue entre dos alumnos de la misma
 *   provincia. Si dos hermanos usan el mismo dispositivo, verían el
 *   horario del otro.
 * - **Por sesión**: se perdería al reiniciar la app. Cada arranque
 *   haría un login nuevo.
 *
 * La clave correcta es el **`username` de la Junta** (el NIF, NIE o
 * IdEA del alumno). Es único en toda Andalucía, es estable entre
 * dispositivos y sesiones, y no cambia al cambiar de provincia.
 *
 * ## Qué se guarda y dónde
 *
 * - **En memoria** (un `Map`): acceso instantáneo durante la sesión.
 *   Se pierde al cerrar la app.
 * - **En disco** (`SharedPreferences`): sobrevive a reinicios. Al
 *   arrancar, se lee de aquí para no perder la caché.
 *
 * El formato en disco es sencillo: por cada usuario, se guarda un
 * `JSONArray` con todos sus slots y un `long` con la fecha del último
 * guardado (por si en el futuro se quiere implementar caducidad).
 *
 * ## Qué NO se guarda
 *
 * **No se guardan credenciales ni tokens**. Solo los slots del
 * horario (día, hora, asignatura, profesor y `codeSection`). Estos
 * datos no son sensibles: si alguien accediera al fichero, vería el
 * horario del alumno, pero no podría autenticarse en su nombre.
 *
 * ## Sobre la concurrencia
 *
 * La caché en memoria se actualiza de forma síncrona antes de
 * suspender para escribir en disco. Si dos corrutinas llaman a [get]
 * y [put] al mismo tiempo, la peor consecuencia posible es que un
 * `get` lea de disco aunque `put` acabe de actualizar la memoria
 * (benigno: vuelve a escribir el mismo valor). Por eso no se necesita
 * un `Mutex` ni ningún otro mecanismo de sincronización: el peor caso
 * es un pequeño trabajo redundante, no un dato incorrecto.
 */
@Singleton
class ScheduleCache @Inject constructor(
    @ApplicationContext context: Context,
) {

    /**
     * Preferencias compartidas donde se guarda el horario en disco.
     *
     * Se abre una sola vez al crear la instancia (la clase es
     * `@Singleton`). Las escrituras son rápidas porque Android las
     * hace en background.
     */
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Caché en memoria, indexada por `username` de la Junta.
     *
     * ## Por qué es `@Volatile`
     *
     * Porque puede ser leída y escrita desde distintos hilos (una
     * corrutina puede leer mientras otra escribe). `@Volatile`
     * garantiza que todos los hilos vean siempre el valor más
     * reciente, sin necesidad de sincronización explícita.
     *
     * ## Por qué es inmutable
     *
     * Se reemplaza entero en cada escritura (`memoryCache = memoryCache + (...)`),
     * en lugar de modificar el `Map` existente. Es una decisión de
     * estilo: los `Map` inmutables son más seguros y fáciles de
     * razonar. El coste de copiar es despreciable para el tamaño de
     * estos datos (~35 slots por usuario).
     */
    @Volatile
    private var memoryCache: Map<String, List<ScheduleSlot>> = emptyMap()

    /**
     * Devuelve el horario cacheado de un usuario, o `null` si no hay.
     *
     * ## Cómo busca
     *
     * 1. Mira primero en memoria (instantáneo).
     * 2. Si no está, lee de disco (rápido, pero no instantáneo).
     * 3. Si lo encuentra en disco, actualiza la memoria para que la
     *    próxima llamada sea instantánea.
     *
     * @param username Usuario de la Junta (NIF/NIE/IdEA).
     * @return La lista de slots, o `null` si no hay caché para ese
     *   usuario.
     */
    suspend fun get(username: String): List<ScheduleSlot>? {
        memoryCache[username]?.let { cachedSchedule -> return cachedSchedule }
        return withContext(Dispatchers.IO) {
            readFromPreferences(username)?.also { loadedSchedule ->
                memoryCache = memoryCache + (username to loadedSchedule)
            }
        }
    }

    /**
     * Guarda el horario de un usuario en memoria y en disco.
     *
     * ## Qué hace paso a paso
     *
     * 1. Actualiza la memoria (síncrono, antes de suspender). Así la
     *    próxima llamada a [get] lo verá inmediatamente.
     * 2. Escribe en disco (suspendido, en `Dispatchers.IO`). No
     *    bloquea al llamador mientras tanto.
     *
     * ## Por qué la memoria se actualiza antes que el disco
     *
     * Porque la memoria es la que se consulta primero. Si se
     * actualizara después, otra corrutina podría hacer un `get` entre
     * medias y ver la caché vieja. Actualizándola primero, se
     * minimiza la ventana.
     *
     * @param username Usuario de la Junta.
     * @param slots Lista de slots a persistir.
     */
    suspend fun put(username: String, slots: List<ScheduleSlot>) {
        memoryCache = memoryCache + (username to slots)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(slotsPreferenceKey(username), serializeSlots(slots))
                .putLong(updatedAtPreferenceKey(username), System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Borra el horario cacheado de un usuario concreto.
     *
     * ## Cuándo se llama
     *
     * En el flujo normal, **nunca**. La caché está indexada por
     * `username`, así que el logout no necesita limpiarla (sigue
     * siendo válida si el mismo usuario vuelve a entrar). Pero el
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
                .remove(slotsPreferenceKey(username))
                .remove(updatedAtPreferenceKey(username))
                .apply()
        }
    }

    // --- Serialización ---

    /**
     * Lee el horario de un usuario desde `SharedPreferences`.
     *
     * ## Manejo de datos corruptos
     *
     * La lectura se envuelve en `runCatching`, así que si el JSON está
     * corrupto (por ejemplo, porque una escritura se interrumpió a
     * medias), la función devuelve `null` en lugar de lanzar una
     * excepción. El llamador tratará `null` como "no hay caché" y
     * volverá a pedir el horario a Pasen.
     *
     * @param username Usuario de la Junta.
     * @return La lista de slots, o `null` si no hay o está corrupta.
     */
    private fun readFromPreferences(username: String): List<ScheduleSlot>? {
        val rawJson = sharedPreferences.getString(slotsPreferenceKey(username), null)
            ?: return null
        return runCatching {
            val slotsArray = JSONArray(rawJson)
            (0 until slotsArray.length()).map { slotIndex ->
                slotsArray.getJSONObject(slotIndex).toScheduleSlot()
            }
        }.getOrNull()
    }

    /**
     * Serializa una lista de slots a JSON.
     *
     * @param slots Lista de slots a serializar.
     * @return Cadena JSON con el array de slots.
     */
    private fun serializeSlots(slots: List<ScheduleSlot>): String {
        val slotsArray = JSONArray()
        slots.forEach { slot -> slotsArray.put(slot.toJsonObject()) }
        return slotsArray.toString()
    }

    /**
     * Convierte un `ScheduleSlot` de dominio en un `JSONObject`.
     *
     * @receiver Slot a serializar.
     * @return El `JSONObject` con los campos del slot.
     */
    private fun ScheduleSlot.toJsonObject() = JSONObject().apply {
        put("day", day)
        put("hoursection", hoursection)
        put("section", section)
        put("subject", subject)
        put("professor", professor)
        put("codeSection", codeSection)
    }

    /**
     * Convierte un `JSONObject` en un `ScheduleSlot` de dominio.
     *
     * @receiver `JSONObject` leído de disco.
     * @return El slot de dominio.
     */
    private fun JSONObject.toScheduleSlot() = ScheduleSlot(
        day = getString("day"),
        hoursection = getString("hoursection"),
        section = getInt("section"),
        subject = getString("subject"),
        professor = getString("professor"),
        codeSection = getInt("codeSection"),
    )

    /**
     * Compone la clave de `SharedPreferences` para los slots de un
     * usuario.
     *
     * @param username Usuario de la Junta.
     * @return La clave, por ejemplo `"slots_jdoe"`.
     */
    private fun slotsPreferenceKey(username: String) = "slots_$username"

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
        const val PREFERENCES_FILE_NAME = "imoodle_schedule_cache"
    }
}