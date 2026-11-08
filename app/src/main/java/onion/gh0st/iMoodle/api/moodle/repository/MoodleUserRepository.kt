package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleParams
import onion.gh0st.iMoodle.data.session.UserCache
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.UserRepository
import org.json.JSONObject

/**
 * Implementación real de [UserRepository] sobre la API REST de
 * Moodle.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de obtener los datos del **usuario logueado**
 * (nombre, apellidos, email, avatar) desde Moodle. Esos datos se
 * piden con la función `core_webservice_get_site_info`, que devuelve
 * información sobre el sitio y sobre el usuario autenticado.
 *
 * ## Caché en memoria
 *
 * Pedir los datos del usuario en cada pantalla sería un desperdicio
 * de red (y de tiempo para el usuario). Por eso esta clase combina
 * dos fuentes:
 *
 * - **La red**: cuando `forceRefresh = true`, se piden datos frescos
 *   a Moodle y se actualiza la caché.
 * - **La caché**: cuando `forceRefresh = false` y hay datos en
 *   caché, se devuelven sin tocar la red.
 *
 * La caché (`UserCache`) vive en memoria y es un `@Singleton`, así
 * que sobrevive a la recreación de ViewModels y Fragments. Se limpia
 * al hacer logout.
 *
 * ## Sobre la imagen de perfil
 *
 * Moodle devuelve la URL del avatar del usuario sin token. Aquí se
 * reescribe a un endpoint que sí lo acepta y se le añade el token,
 * para que Coil pueda cargarla.
 *
 * @param api Cliente REST de Moodle ya configurado con el token de
 *   la sesión.
 * @param cache Caché compartida del usuario actual.
 */
class MoodleUserRepository(
    private val api: MoodleApiClient,
    private val cache: UserCache,
) : UserRepository {

    /**
     * Devuelve el usuario actual.
     *
     * ## Comportamiento
     *
     * - Si `forceRefresh = false` y hay caché, la usa.
     * - En caso contrario, consulta la API, actualiza la caché y
     *   devuelve el usuario.
     *
     * ## Cuándo se usa `forceRefresh`
     *
     * - **Al hacer login**: siempre `true`, para traer datos frescos.
     * - **Al abrir el perfil**: `false`, para no pedir datos que ya
     *   tenemos.
     *
     * @param forceRefresh Si `true`, ignora la caché.
     * @return El usuario cargado.
     */
    override suspend fun getUser(forceRefresh: Boolean): User {
        if (!forceRefresh) {
            cache.get()?.let { cachedUser -> return cachedUser }
        }
        val fetchedUser = fetchFromNetwork()
        cache.set(fetchedUser)
        return fetchedUser
    }

    // --- Obtención de datos de red ---

    /**
     * Consulta `core_webservice_get_site_info` y mapea la respuesta.
     *
     * @return Usuario de dominio.
     */
    private suspend fun fetchFromNetwork(): User {
        val siteInfoRawJson = api.callREST("core_webservice_get_site_info", MoodleParams())
        val siteInfoJson = JSONObject(siteInfoRawJson)
        return User(
            id = siteInfoJson.optInt("userid", 0),
            username = siteInfoJson.optString("username", ""),
            firstname = siteInfoJson.optString("firstname", ""),
            lastname = siteInfoJson.optString("lastname", ""),
            fullname = siteInfoJson.optString("fullname", "Usuario"),
            email = siteInfoJson.optString("email", ""),
            profileImageUrl = resolveProfileImageUrl(
                siteInfoJson.optString("userpictureurl", ""),
            ),
        )
    }

    /**
     * Resuelve la URL del avatar del usuario.
     *
     * ## Qué devuelve Moodle por defecto
     *
     * Moodle devuelve `userpictureurl` como una URL de
     * `pluginfile.php` sin token. Esa URL no sirve para cargar la
     * imagen desde la app, porque el servidor exige token.
     *
     * ## Cómo se transforma
     *
     * Hay dos casos:
     *
     * 1. **URL con context ID** (`/pluginfile.php/123/...`): se
     *    reescribe al endpoint de Web Services
     *    (`/webservice/pluginfile.php/123/user/icon/f1`) y se le
     *    añade el token. Ese endpoint sí acepta token.
     * 2. **URL sin context ID**: se reescribe `pluginfile.php` →
     *    `webservice/pluginfile.php` y se añade el token.
     *
     * ## Cuándo devuelve `null`
     *
     * Si la URL de entrada está vacía (usuario sin foto configurada).
     * En ese caso, la UI muestra un placeholder con las iniciales.
     *
     * @param rawUrl `userpictureurl` tal cual la envía Moodle.
     * @return URL con token, o `null` si la entrada está vacía.
     */
    private fun resolveProfileImageUrl(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        val currentToken = api.token.value
        val moodleBaseUrl = api.config.moodleBase
        val contextId = Regex("/pluginfile\\.php/(\\d+)/")
            .find(rawUrl)
            ?.groupValues
            ?.get(1)
        return if (contextId != null) {
            "$moodleBaseUrl/webservice/pluginfile.php/$contextId/user/icon/f1?token=$currentToken"
        } else {
            val rewrittenUrl = rawUrl.replace("pluginfile.php", "webservice/pluginfile.php")
            if (rewrittenUrl.contains("?")) {
                "$rewrittenUrl&token=$currentToken"
            } else {
                "$rewrittenUrl?token=$currentToken"
            }
        }
    }
}