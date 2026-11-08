package onion.gh0st.iMoodle.api.moodle.config

import onion.gh0st.iMoodle.core.Defaults
import onion.gh0st.iMoodle.domain.model.session.Provincia

/**
 * Configuración de la instancia de Moodle para una provincia concreta.
 *
 * ## Qué es Moodle Centros
 *
 * Moodle es la plataforma de aprendizaje que usa la Junta de Andalucía
 * (bajo el nombre comercial "Moodle Centros"). Cada provincia tiene
 * **su propia instancia**, con su propia URL. Por ejemplo:
 *
 * - Córdoba: `https://educacionadistancia.juntadeandalucia.es/centros/cordoba/`
 * - Sevilla: `https://educacionadistancia.juntadeandalucia.es/centros/sevilla/`
 * - Málaga: `https://educacionadistancia.juntadeandalucia.es/centros/malaga/`
 *
 * Todas las instancias están en el mismo servidor, pero se distinguen
 * por el segmento de la URL (`cordoba`, `sevilla`, `malaga`…). Ese
 * segmento es lo que esta clase llama **slug**.
 *
 * ## Para qué sirve esta clase
 *
 * Es un contenedor de datos que reúne **todo lo necesario** para
 * hablar con la instancia de Moodle de una provincia:
 *
 * - La URL base (a partir del slug).
 * - La URL a la que el CAS debe devolver al usuario tras el login.
 * - La URL del endpoint que emite el token de la API REST.
 * - El User-Agent que se enviará en cada petición.
 *
 * ## Por qué es un `data class` inmutable
 *
 * Porque la configuración no cambia durante la vida de la app. Una
 * vez construida para una provincia, no hay motivo para modificarla.
 * Que sea inmutable evita bugs raros (dos sitios cambiando la URL al
 * mismo tiempo, etc.) y facilita el razonamiento sobre el código.
 *
 * ## Por qué no tiene métodos de "guardar" o "cargar"
 *
 * Porque no hace falta: la configuración se construye al vuelo a
 * partir de la provincia. No se guarda en disco ni en preferencias.
 * Se deriva siempre del `Provincia` que el usuario ha elegido en la
 * pantalla de login.
 *
 * @property provinciaSlug Slug de la provincia, tal cual aparece en
 *   la URL de Moodle. Por ejemplo, `"cordoba"`, `"sevilla"`,
 *   `"malaga"`. Debe ir en minúsculas y sin tildes, porque así es
 *   como la Junta lo usa en sus URLs.
 * @property moodleHost Host del servidor donde viven todas las
 *   instancias de Moodle. Por defecto es el de la Junta. Puede incluir
 *   puerto (por ejemplo, `"localhost:8080"` en tests con servidores
 *   locales).
 * @property userAgent Cabecera `User-Agent` que se enviará en cada
 *   petición. Algunos servidores de la Junta rechazan peticiones sin
 *   un User-Agent reconocible. Por defecto, el de la app.
 * @property protocol Protocolo a usar en las URLs. `"https"` en
 *   producción (obligatorio por seguridad); `"http"` solo en tests
 *   con servidores locales, porque los certificados de pruebas no
 *   suelen estar firmados por una autoridad reconocida.
 */
data class MoodleConfig(
    val provinciaSlug: String,
    val moodleHost: String = Defaults.MOODLE_HOST,
    val userAgent: String = Defaults.USER_AGENT,
    val protocol: String = "https",
) {

    /**
     * Comprueba que la configuración es válida.
     *
     * ## Qué se valida
     *
     * Solo se exige que [provinciaSlug] no esté vacío. Sin slug, no se
     * puede construir la URL de Moodle, así que la configuración no
     * tiene sentido.
     *
     * ## Por qué se valida aquí y no en el constructor de `Provincia`
     *
     * Porque `Provincia` es un enum con valores conocidos y
     * controlados. Esta clase, en cambio, puede construirse con un
     * slug arbitrario (por ejemplo, desde un test). Validar aquí
     * atrapa los errores lo antes posible.
     *
     * @throws IllegalArgumentException si [provinciaSlug] está en
     *   blanco.
     */
    init {
        require(provinciaSlug.isNotBlank()) { "provinciaSlug no puede estar vacío" }
    }

    /**
     * URL base de Moodle para esta provincia.
     *
     * ## Cómo se construye
     *
     * Se compone de cuatro partes:
     *
     * ```
     * <protocolo>://<host>/centros/<slug>
     * ```
     *
     * Por ejemplo:
     * ```
     * https://educacionadistancia.juntadeandalucia.es/centros/cordoba
     * ```
     *
     * ## Detalle importante sobre `/centros/`
     *
     * El segmento `/centros/` es literal del servidor de la Junta. No
     * es un nombre descriptivo elegido por esta app: es la ruta real
     * que usa el servidor. Si algún día la Junta cambia esa ruta,
     * habrá que cambiar esta propiedad. No renombrar a `/provincias/`
     * ni a nada parecido, aunque suene mejor.
     */
    val moodleBase: String
        get() = "$protocol://$moodleHost/centros/$provinciaSlug"

    /**
     * URL del servicio al que el CAS debe devolver al usuario tras el
     * login.
     *
     * ## Qué es esto
     *
     * El CAS (el servidor de identificación de la Junta) necesita
     * saber a qué aplicación tiene que mandar al usuario después de
     * que se identifique. Para nosotros, esa aplicación es el login de
     * Moodle de la provincia correspondiente.
     *
     * La URL apunta al login de Moodle con un parámetro especial
     * (`?authCAS=CAS`) que le dice a Moodle: "no pidas usuario y
     * contraseña, ya vienen validados por el CAS".
     *
     * ## Quién la usa
     *
     * `CasConfig` la recibe como `serviceUrl` y la pasa al CAS en el
     * proceso de login. `CasAuthenticator` la envía en cada petición
     * del flujo.
     */
    val loginServiceUrl: String
        get() = "$moodleBase/login/index.php?authCAS=CAS"

    /**
     * URL del endpoint interno que emite el token de la API REST.
     *
     * ## Qué es `launch.php`
     *
     * Moodle tiene una página especial (`launch.php`) que la app
     * oficial de Moodle usa para obtener el `wstoken` sin tener que
     * pasar por el formulario de login completo. Esa página, cuando el
     * usuario está autenticado (porque ya tiene las cookies del CAS),
     * responde con una redirección (`302`) cuya cabecera `Location`
     * contiene el token codificado en Base64.
     *
     * ## Parámetros de la URL
     *
     * - `service=moodle_mobile_app`: le dice a Moodle que emita un
     *   token para la app móvil (no para el navegador).
     * - `passport=12345`: un valor de relleno que Moodle espera.
     * - `urlscheme=moodlemobile`: el esquema de URL de la app oficial
     *   de Moodle. Lo replicamos para que la respuesta sea idéntica a
     *   la que recibiría la app oficial.
     *
     * ## Quién la usa
     *
     * `MoodleTokenFetcher` la usa durante el login inicial para
     * obtener el token. `CookiesBasedTokenRefresher` la usa para
     * refrescar el token reutilizando las cookies del CAS.
     */
    val launchUrl: String
        get() = "$moodleBase/admin/tool/mobile/launch.php" +
                "?service=moodle_mobile_app&passport=12345&urlscheme=moodlemobile"

    companion object {

        /**
         * Construye una configuración a partir de una provincia.
         *
         * ## Qué hace
         *
         * Es un atajo para no tener que escribir
         * `MoodleConfig(provinciaSlug = provincia.slug)` cada vez. El
         * resto de campos se dejan con sus valores por defecto, que
         * son los correctos para producción.
         *
         * ## Cuándo usarlo
         *
         * En producción, siempre. Solo en tests se construye
         * directamente con otros valores (host local, protocolo
         * `http`, etc.).
         *
         * @param provincia Provincia de la que derivar el slug.
         * @return Una configuración lista para usar.
         */
        fun of(provincia: Provincia): MoodleConfig =
            MoodleConfig(provinciaSlug = provincia.slug)
    }
}