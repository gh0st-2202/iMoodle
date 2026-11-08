package onion.gh0st.iMoodle.api.pasen.repository

import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import onion.gh0st.iMoodle.api.pasen.network.PasenScheduleParser
import onion.gh0st.iMoodle.data.schedule.ScheduleCache
import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [ScheduleRepository] sobre la API de Pasen,
 * con caché por usuario.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de obtener el horario del alumno. Combina tres
 * piezas:
 *
 * 1. **`PasenApiClient`**: hace las peticiones HTTP a Pasen.
 * 2. **`PasenScheduleParser`**: convierte el JSON crudo en slots de
 *    dominio.
 * 3. **`ScheduleCache`**: guarda el horario en disco (y en memoria)
 *    para no pedirlo cada vez.
 *
 * ## Estrategia cache-first
 *
 * La app no quiere llamar a Pasen cada vez que el usuario entra en
 * la pestaña de horario. Cada llamada implica un login completo (con
 * sus 30 segundos de espera y su tráfico) y, además, la Junta podría
 * considerar abusivo hacerlo tantas veces. Por eso esta clase
 * implementa una estrategia **cache-first**:
 *
 * - Si `forceRefresh = false` y hay caché para el usuario actual, se
 *   devuelve la caché sin tocar la red.
 * - Si no hay caché, o `forceRefresh = true`, se hace login a Pasen,
 *   se pide el horario y se guarda en caché.
 *
 * El `forceRefresh = true` solo se usa cuando el usuario pulsa
 * pull-to-refresh, porque sabe que quiere datos frescos.
 *
 * ## Por qué la caché es por `username` y no por `userId`
 *
 * Porque el `userId` de Moodle no es único entre provincias: cada
 * instancia de Moodle tiene su propia numeración. En cambio, el
 * `username` de la Junta es único y estable en toda Andalucía. Si
 * dos alumnos comparten dispositivo, sus horarios no se pisan porque
 * la caché está indexada por `username`.
 *
 * ## Flujo de una llamada a [getSchedule]
 *
 * 1. Carga las credenciales guardadas del usuario. Si no hay,
 *    lanza una excepción.
 * 2. Si no se ha pedido `forceRefresh`, consulta la caché. Si hay
 *    algo, devuélvelo.
 * 3. Hace login a Pasen con las credenciales.
 * 4. Pide la matrícula del alumno (`getSessionInfo`).
 * 5. Pide el horario (`getHorario`).
 * 6. Parsea el JSON con `PasenScheduleParser`.
 * 7. Guarda el resultado en la caché.
 * 8. Devuelve el resultado.
 *
 * ## Manejo de errores
 *
 * Cualquier fallo (red, parseo, login) se propaga como
 * `IllegalStateException` con un mensaje descriptivo. El
 * `ScheduleViewModel` lo captura y muestra un toast genérico. El
 * detalle se registra en logcat.
 *
 * @param apiClient Cliente HTTP de Pasen.
 * @param credentialsStore Almacén cifrado de credenciales de la
 *   Junta.
 * @param scheduleCache Caché por usuario del horario.
 */
@Singleton
class PasenScheduleRepository @Inject constructor(
    private val apiClient: PasenApiClient,
    private val credentialsStore: CredentialsStore,
    private val scheduleCache: ScheduleCache,
) : ScheduleRepository {

    /**
     * Obtiene el horario del usuario autenticado.
     *
     * ## Comportamiento
     *
     * - Si `forceRefresh = false` y hay caché para el usuario actual,
     *   se devuelve la caché sin tocar la red.
     * - Si no hay caché, o `forceRefresh = true`, se va a Pasen, se
     *   parsea y se guarda en caché.
     *
     * ## Cuándo lanza excepción
     *
     * - Si no hay credenciales guardadas (el usuario no ha hecho
     *   login).
     * - Si el login a Pasen falla.
     * - Si la petición del horario falla.
     * - Si el JSON no se puede parsear.
     *
     * @param forceRefresh Si `true`, ignora la caché y va a la red.
     * @return Lista de slots del horario.
     * @throws IllegalStateException si no hay credenciales o falla
     *   alguna petición.
     */
    override suspend fun getSchedule(forceRefresh: Boolean): List<ScheduleSlot> {
        val credentials = credentialsStore.load()
            ?: throw IllegalStateException("No hay credenciales guardadas para iPasen")

        if (!forceRefresh) {
            scheduleCache.get(credentials.username)?.let { cachedSchedule ->
                return cachedSchedule
            }
        }

        val freshSchedule = fetchFromPasen(credentials.username, credentials.password)
        scheduleCache.put(credentials.username, freshSchedule)
        return freshSchedule
    }

    /**
     * Hace login a Pasen, pide el horario y lo parsea.
     *
     * ## Qué hace paso a paso
     *
     * 1. Construye un `JuntaCredentials` con el usuario y la
     *    contraseña recibidos.
     * 2. Hace login y obtiene el token.
     * 3. Pide la matrícula del alumno.
     * 4. Pide el horario usando la matrícula.
     * 5. Parsea el JSON y devuelve la lista de slots.
     *
     * @param username Usuario de la Junta.
     * @param password Contraseña del usuario.
     * @return Lista de slots del horario.
     */
    private suspend fun fetchFromPasen(
        username: String,
        password: String,
    ): List<ScheduleSlot> {
        val credentials = onion.gh0st.iMoodle.domain.model.session.JuntaCredentials(
            username = username,
            password = password,
        )
        val token = apiClient.login(credentials)
        val sessionInfo = apiClient.getSessionInfo(token)
        val rawScheduleJson = apiClient.getHorario(token, sessionInfo.id)

        return PasenScheduleParser.parse(rawScheduleJson)
    }
}