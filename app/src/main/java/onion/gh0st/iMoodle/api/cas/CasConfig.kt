package onion.gh0st.iMoodle.api.cas

import onion.gh0st.iMoodle.core.Defaults
import java.net.URLEncoder

/**
 * Configuración del servidor de identificación de la Junta de
 * Andalucía (CAS) para una provincia concreta.
 *
 * ## Qué guarda esta clase
 *
 * Para que el flujo de login funcione, el CAS necesita saber a qué
 * servicio tiene que devolver al usuario tras identificarlo. Ese
 * servicio es la instancia de Moodle de la provincia del alumno. Por
 * ejemplo, un alumno de Córdoba tiene que volver a
 * `https://educacionadistancia.juntadeandalucia.es/centros/cordoba/...`,
 * mientras que uno de Sevilla vuelve a
 * `https://educacionadistancia.juntadeandalucia.es/centros/sevilla/...`.
 *
 * Además del servicio de destino, la clase guarda:
 * - La URL base del CAS (la misma para toda Andalucía).
 * - El User-Agent que se enviará en las peticiones (para que el
 *   servidor sepa que es una app Android y no un navegador raro).
 *
 * ## Por qué se calculan dos URLs a partir de una
 *
 * La URL del servicio que se pasa al CAS **tiene que ir codificada**
 * (los caracteres especiales como `?`, `:` y `/` se sustituyen por
 * secuencias como `%3F`, `%3A`, `%2F`). Si no se codifica, el CAS
 * interpreta los parámetros del servicio como parámetros del propio
 * CAS y el flujo falla.
 *
 * Por eso esta clase expone dos propiedades:
 * - [serviceUrl]: la URL tal cual la escribimos, sin codificar. Es la
 *   que necesita el usuario para leerla.
 * - [service]: la URL ya codificada, lista para meter en el query
 *   string del CAS. Es la que usa [CasAuthenticator] internamente.
 *
 * @property serviceUrl URL del servicio de destino (Moodle de la
 *   provincia). Es la URL **sin codificar**, la legible.
 * @property casUrl Endpoint del CAS de la Junta. Por defecto, el de
 *   toda Andalucía. Se puede cambiar para tests o para otras
 *   comunidades autónomas que usen CAS.
 * @property userAgent Cabecera `User-Agent` que se enviará en cada
 *   petición. Por defecto, el de la app.
 */
data class CasConfig(
    val serviceUrl: String,
    val casUrl: String = Defaults.CAS_URL,
    val userAgent: String = Defaults.USER_AGENT,
) {

    /**
     * Comprueba que la configuración es válida.
     *
     * Solo se exige que [serviceUrl] no esté vacía. Sin esa URL, el
     * CAS no sabría a dónde devolver al usuario y el login sería
     * inútil.
     *
     * @throws IllegalArgumentException si [serviceUrl] está en blanco.
     */
    init {
        require(serviceUrl.isNotBlank()) { "serviceUrl no puede estar vacío" }
    }

    /**
     * URL del servicio ya codificada, lista para usar como parámetro
     * `service` en la URL del CAS.
     *
     * ## Qué significa "codificada"
     *
     * En una URL, los caracteres como `?`, `&`, `=`, `:` o `/` tienen
     * un significado especial (separan parámetros, indican rutas,
     * etc.). Si queremos meter una URL entera dentro del valor de un
     * parámetro de otra URL, hay que "escapar" esos caracteres para
     * que no se confundan. Eso es lo que hace `URLEncoder.encode`:
     * convierte `https://x.com/a?b=c` en
     * `https%3A%2F%2Fx.com%2Fa%3Fb%3Dc`.
     *
     * El CAS de la Junta **exige** que el parámetro `service` vaya
     * codificado. Si no, no reconoce la URL y devuelve al usuario al
     * inicio.
     */
    val service: String = URLEncoder.encode(serviceUrl, "UTF-8")

    /**
     * URL completa a la que se hace el primer `GET` del flujo CAS.
     *
     * Esta URL incluye el parámetro `service` ya codificado, así que
     * es la que se pasa tal cual al constructor de la petición HTTP.
     */
    val fullLoginUrl: String = "$casUrl?service=$service"
}