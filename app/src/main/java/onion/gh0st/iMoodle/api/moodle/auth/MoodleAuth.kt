package onion.gh0st.iMoodle.api.moodle.auth

import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.cas.CasAuthenticator
import onion.gh0st.iMoodle.api.cas.CasConfig
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.network.MoodleTokenFetcher

/**
 * Punto de entrada del flujo de autenticación completo contra la Junta
 * (CAS) y contra Moodle.
 *
 * ## Qué papel juega dentro de la app
 *
 * Esta clase une dos piezas que, por separado, no tienen sentido:
 *
 * 1. **`CasAuthenticator`**: identifica al usuario contra el CAS de la
 *    Junta y obtiene las cookies de sesión.
 * 2. **`MoodleTokenFetcher`**: usa esas cookies para pedir a Moodle el
 *    token de la API REST (`wstoken`).
 *
 * La gracia de tener esta clase es que el resto de la app no tiene
 * que saber que hay dos pasos. Solo dice "autentica a este usuario en
 * esta provincia" y recibe una `MoodleSession` lista para hacer
 * peticiones a Moodle.
 *
 * ## Por qué se construye por provincia
 *
 * Cada provincia andaluza tiene su propia instancia de Moodle
 * (`.../centros/cordoba`, `.../centros/sevilla`, etc.). Por eso el
 * constructor recibe la provincia (o mejor dicho, la configuración
 * ya derivada de ella) y no se puede reutilizar entre provincias
 * distintas.
 *
 * ## Constructores
 *
 * - **Constructor público**: recibe las configuraciones ya
 *   construidas ([casConfig], [moodleConfig]). Se usa cuando quieres
 *   controlar la configuración al detalle (por ejemplo, en tests).
 * - **`forProvincia(...)`**: constructor de conveniencia que deriva
 *   las dos configuraciones a partir de una `Provincia`. Es el que
 *   usa la app en producción.
 *
 * @param casConfig Configuración del CAS (URL del servicio de vuelta,
 *   User-Agent…).
 * @param moodleConfig Configuración de Moodle (URL base, User-Agent…).
 * @param baseClient Cliente HTTP base a partir del cual se construyen
 *   los clientes internos. Se clona para cada sesión.
 * @param onTokenRefreshed Callback que se invoca cuando Moodle emite
 *   un token nuevo. Se propaga hasta `MoodleSessionFactory` y, de
 *   ahí, al `DefaultSessionRepository`.
 * @param onInvalidToken Callback que se invoca cuando el token deja
 *   de valer y no se ha podido refrescar. El repositorio lo usa para
 *   forzar el cierre de sesión.
 */
class MoodleAuth(
    casConfig: CasConfig,
    moodleConfig: MoodleConfig,
    baseClient: OkHttpClient = OkHttpClient(),
    onTokenRefreshed: suspend (String) -> Unit = {},
    onInvalidToken: suspend () -> Unit = {},
) {

    /** Autenticador del CAS ya configurado para esta provincia. */
    private val casAuthenticator = CasAuthenticator(casConfig, baseClient)

    /** Obtenedor del token de Moodle a partir de una `CasSession`. */
    private val moodleTokenFetcher = MoodleTokenFetcher(
        moodleConfig, baseClient, onTokenRefreshed, onInvalidToken,
    )

    /**
     * Ejecuta el login completo: CAS primero, luego Moodle.
     *
     * ## Flujo interno
     *
     * 1. Delega en [CasAuthenticator] para identificar al usuario
     *    contra el CAS. Obtiene una `CasSession` con las cookies.
     * 2. Pasa esa `CasSession` a [MoodleTokenFetcher] para que pida a
     *    Moodle el `wstoken` usando las cookies. Devuelve una
     *    `MoodleSession` completa.
     *
     * ## Manejo de errores
     *
     * Cualquier `AuthException` que lancen los pasos internos sube
     * tal cual. La pantalla de login sabe traducir cada subtipo a un
     * mensaje concreto para el usuario.
     *
     * @param credentials Credenciales del usuario.
     * @return La sesión completa con cookies y token de Moodle.
     * @throws AuthException si cualquiera de los dos pasos falla.
     */
    suspend fun login(credentials: CasCredentials): MoodleSession {
        val casSession = casAuthenticator.authenticate(credentials)
        return moodleTokenFetcher.fetchToken(casSession)
    }

    companion object {

        /**
         * Construye un `MoodleAuth` a partir de una provincia.
         *
         * ## Qué hace
         *
         * Deriva las dos configuraciones necesarias a partir del slug
         * de la provincia:
         *
         * - `MoodleConfig`: URL base, User-Agent y demás datos de la
         *   instancia de Moodle de esa provincia.
         * - `CasConfig`: a partir de la configuración anterior,
         *   construye la URL del servicio de vuelta tras el login CAS
         *   (la del login de Moodle de esa provincia).
         *
         * ## Cuándo usarlo
         *
         * En producción, siempre. La app no tiene más que una
         * `Provincia` en la mano y esta función hace el resto.
         *
         * @param provincia Provincia destino.
         * @param baseClient Cliente HTTP base a partir del cual se
         *   construirán los clientes internos.
         * @param onTokenRefreshed Callback de refresco de token.
         * @param onInvalidToken Callback de invalidación de token.
         * @return Un `MoodleAuth` listo para hacer login.
         */
        fun forProvincia(
            provincia: Provincia,
            baseClient: OkHttpClient = OkHttpClient(),
            onTokenRefreshed: suspend (String) -> Unit = {},
            onInvalidToken: suspend () -> Unit = {},
        ): MoodleAuth {
            val moodleConfig = MoodleConfig.of(provincia)
            return MoodleAuth(
                casConfig = CasConfig(serviceUrl = moodleConfig.loginServiceUrl),
                moodleConfig = moodleConfig,
                baseClient = baseClient,
                onTokenRefreshed = onTokenRefreshed,
                onInvalidToken = onInvalidToken,
            )
        }
    }
}