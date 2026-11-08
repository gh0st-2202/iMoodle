package onion.gh0st.iMoodle.fakes

import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionContext
import onion.gh0st.iMoodle.domain.model.session.User
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Factorías de objetos de sesión para tests.
 *
 * Construyen instancias reales (no mocks) que no realizan I/O porque
 * nadie invoca `callREST` en los tests de ViewModel.
 */
object TestSessions {

    /**
     * Construye un [User] de prueba con valores por defecto.
     *
     * @param id ID interno del usuario.
     * @param firstname Nombre de pila.
     * @param lastname Apellidos.
     * @return El usuario configurado.
     */
    fun user(
        id: Int = 1,
        firstname: String = "John",
        lastname: String = "Doe",
    ) = User(
        id = id,
        username = "jdoe",
        firstname = firstname,
        lastname = lastname,
        fullname = "$firstname $lastname",
        email = "jdoe@example.com",
        profileImageUrl = null,
    )

    /**
     * Construye un [MoodleSession] de prueba sin red.
     *
     * @param username Usuario de Moodle.
     * @param token Token ficticio.
     * @return La sesión configurada.
     */
    fun moodle(
        username: String = "jdoe",
        token: String = "fake_token",
    ): MoodleSession {
        val client = OkHttpClient()
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val config = MoodleConfig(provinciaSlug = "cordoba")
        return MoodleSession(
            user = username,
            cookieManager = cookies,
            client = client,
            api = MoodleApiClient(
                client = client,
                config = config,
                tokenRefresher = TokenRefresher { null },
                initialToken = token,
            ),
        )
    }

    /**
     * Construye un [SessionContext] de prueba.
     *
     * @param provincia Provincia asociada.
     * @param user Nombre de usuario de Moodle.
     * @param token Token ficticio.
     * @param userId ID de usuario, o `0` si no se conoce.
     * @return El contexto configurado.
     */
    fun context(
        provincia: Provincia = Provincia.CORDOBA,
        user: String = "jdoe",
        token: String = "fake_token",
        userId: Int = 1,
    ) = SessionContext(
        provincia = provincia,
        user = user,
        wstoken = token,
        userId = userId,
    )

    /**
     * Construye una [ActiveSession] de prueba con la forma nueva
     * (`context` + `user`).
     *
     * @param user Usuario cargado, o `null` si aún no se ha pedido.
     * @param provincia Provincia asociada.
     * @return La sesión activa configurada.
     */
    fun active(
        user: User? = user(),
        provincia: Provincia = Provincia.CORDOBA,
    ) = ActiveSession(
        context = context(provincia = provincia),
        user = user,
    )
}