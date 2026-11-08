package onion.gh0st.iMoodle.api.pasen.repository

import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import onion.gh0st.iMoodle.api.pasen.network.PasenStudentInfoParser
import onion.gh0st.iMoodle.data.session.UserProfileCache
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real de [UserProfileRepository] sobre la API de
 * Pasen, con caché por usuario.
 *
 * ## Qué hace esta clase
 *
 * Enriquece el `User` de dominio con los datos personales que
 * proporciona Pasen (centro, fecha de nacimiento, edad, sexo). No
 * crea una entidad paralela: rellena campos opcionales del mismo
 * `User`.
 *
 * ## Estrategia cache-first en tres niveles
 *
 * Pedir los datos a Pasen implica un login completo (con sus 30
 * segundos de espera y su tráfico). Para no hacerlo en cada apertura
 * del perfil, esta clase implementa tres niveles de caché, del más
 * rápido al más lento:
 *
 * 1. **Memoria de la sesión**: si el `User` ya trae los cuatro
 *    campos de Pasen informados (porque otra pantalla ya llamó a
 *    este repositorio y los persistió en `sessionRepo.updateUser`),
 *    se devuelve tal cual. **No hay login ni lectura de disco**.
 * 2. **Caché en disco** (`UserProfileCache`): si hay datos guardados
 *    para el `username`, se usan para rellenar el `User` sin tocar
 *    la red. **No hay login**, solo lectura de `SharedPreferences`.
 * 3. **Red**: si no hay nada en los niveles anteriores, se hace
 *    login a Pasen, se piden los datos, se guardan en caché de
 *    disco y se devuelven.
 *
 * Gracias a esta estrategia, el usuario solo "paga" el login a Pasen
 * la primera vez que abre el perfil en su dispositivo. El resto de
 * aperturas son instantáneas y sin red.
 *
 * ## Qué pasa si Pasen no responde
 *
 * Si el login o la petición fallan, la excepción se propaga al
 * `ProfileViewModel`, que la captura y se limita a registrar el
 * error. La UI muestra el perfil básico de Moodle (nombre, email,
 * avatar) sin los datos personales. No es un error fatal: la app
 * sigue funcionando.
 *
 * ## Por qué la caché es por `username` y no por `userId`
 *
 * Mismo motivo que en `PasenScheduleRepository`: el `userId` de
 * Moodle no es único entre provincias, mientras que el `username` de
 * la Junta sí lo es.
 *
 * @param apiClient Cliente HTTP de Pasen.
 * @param credentialsStore Almacén cifrado de credenciales de la
 *   Junta.
 * @param userProfileCache Caché por usuario de los datos de Pasen.
 */
@Singleton
class PasenUserProfileRepository @Inject constructor(
    private val apiClient: PasenApiClient,
    private val credentialsStore: CredentialsStore,
    private val userProfileCache: UserProfileCache,
) : UserProfileRepository {

    /**
     * Devuelve una copia del `User` con los datos de Pasen
     * rellenados.
     *
     * ## Comportamiento (tres niveles)
     *
     * 1. Si el `User` ya trae los cuatro campos de Pasen (porque otra
     *    pantalla los cargó antes), se devuelve tal cual. **Cero
     *    red, cero disco**.
     * 2. Si hay caché en disco para el `username`, se rellena el
     *    `User` con ella. **Cero red**, solo disco.
     * 3. Si no hay caché, se hace login a Pasen, se piden los datos,
     *    se guardan en caché y se devuelve el `User` enriquecido.
     *
     * ## Qué pasa si el `User` llega con datos parciales
     *
     * Si por algún motivo el `User` de entrada solo trae algunos de
     * los cuatro campos (por ejemplo, porque una carga anterior
     * falló a medias), esta clase completará los que falten con los
     * de la caché o con los de la red. Los campos que ya tuviera se
     * respetan.
     *
     * @param user Usuario a enriquecer.
     * @return Copia del usuario con los campos de Pasen rellenados.
     * @throws IllegalStateException si no hay credenciales guardadas
     *   o falla el login a Pasen.
     */
    override suspend fun enrich(user: User): User {
        if (user.hasPasenData()) return user

        userProfileCache.get(user.username)?.let { cachedProfile ->
            return user.copy(
                centerName = cachedProfile.centerName,
                birthDate = cachedProfile.birthDate,
                age = cachedProfile.age,
                gender = cachedProfile.gender,
            )
        }

        return fetchFromPasen(user)
    }

    // --- Métodos internos ---

    /**
     * Comprueba si el `User` ya trae los cuatro campos de Pasen.
     *
     * ## Por qué se comprueba que estén los cuatro y no solo uno
     *
     * Porque los campos se rellenan juntos. Si están todos, la
     * carga fue completa. Si falta alguno, es que la carga anterior
     * falló a medias y conviene reintentar (leyendo de la caché o de
     * la red).
     *
     * @receiver Usuario a comprobar.
     * @return `true` si los cuatro campos están informados.
     */
    private fun User.hasPasenData(): Boolean =
        centerName != null && birthDate != null && age != null && gender != null

    /**
     * Hace login a Pasen, pide `datos-alumno`, parsea la sección
     * "Datos Personales", guarda en caché y devuelve el `User`
     * enriquecido.
     *
     * ## Qué hace paso a paso
     *
     * 1. Carga las credenciales guardadas. Si no hay, lanza una
     *    excepción.
     * 2. Hace login y obtiene el token.
     * 3. Pide la matrícula del alumno.
     * 4. Pide el JSON de `datos-alumno`.
     * 5. Parsea el JSON y busca la sección `"Datos Personales"`.
     * 6. Extrae los cuatro campos que interesan (`Fecha de
     *    nacimiento`, `Edad`, `Sexo` y el nombre del centro que viene
     *    en `info-session`).
     * 7. Guarda en caché si los cuatro campos se han rellenado
     *    correctamente.
     * 8. Devuelve el `User` enriquecido.
     *
     * ## Qué pasa si la sección "Datos Personales" no existe
     *
     * Se rellenan los cuatro campos con `"No disponible"`. Así la UI
     * muestra un mensaje claro en lugar de dejar los campos vacíos.
     *
     * @param user Usuario a enriquecer.
     * @return Copia del usuario con los campos rellenados.
     * @throws IllegalStateException si no hay credenciales o falla
     *   alguna petición.
     */
    private suspend fun fetchFromPasen(user: User): User {
        val credentials = credentialsStore.load()
            ?: throw IllegalStateException("No hay credenciales guardadas para iPasen")

        val token = apiClient.login(credentials)
        val sessionInfo = apiClient.getSessionInfo(token)
        val rawStudentDataJson = apiClient.getDatosAlumno(token, sessionInfo.id)

        val sections = PasenStudentInfoParser.parse(rawStudentDataJson)
        val personalData = sections
            .find { section -> section.title.contains("Personales", ignoreCase = true) }
            ?.items
            ?.toMap()
            ?: emptyMap()

        val enrichedUser = user.copy(
            centerName = sessionInfo.centerName,
            birthDate = personalData["Fecha de nacimiento"] ?: "No disponible",
            age = personalData["Edad"] ?: "No disponible",
            gender = personalData["Sexo"] ?: "No disponible",
        )

        if (enrichedUser.hasPasenData()) {
            userProfileCache.put(
                user.username,
                UserProfileCache.CachedProfile(
                    centerName = enrichedUser.centerName.orEmpty(),
                    birthDate = enrichedUser.birthDate.orEmpty(),
                    age = enrichedUser.age.orEmpty(),
                    gender = enrichedUser.gender.orEmpty(),
                ),
            )
        }

        return enrichedUser
    }
}