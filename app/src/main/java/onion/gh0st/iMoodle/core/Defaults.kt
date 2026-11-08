package onion.gh0st.iMoodle.core

/**
 * Constantes compartidas por toda la app.
 *
 * ## Qué contiene
 *
 * Valores que se usan en múltiples sitios y que, si hubiera que
 * cambiarlos, conviene tenerlos centralizados:
 *
 * - **`USER_AGENT`**: la cabecera que dice a los servidores qué app
 *   está haciendo la petición.
 * - **`MOODLE_HOST`**: el host donde viven todas las instancias de
 *   Moodle de la Junta.
 * - **`CAS_URL`**: la URL del servidor de identificación único de la
 *   Junta (CAS).
 *
 * ## Por qué están aquí y no en cada `Config`
 *
 * Porque son valores comunes a todos los servicios. `MoodleConfig`
 * los usa para el host y el User-Agent; `CasConfig` los usa para el
 * CAS y el User-Agent. Tenerlos en un solo sitio evita
 * inconsistencias (por ejemplo, que el User-Agent de una petición sea
 * distinto del de otra).
 *
 * ## Cuándo cambiarlos
 *
 * Casi nunca. El `USER_AGENT` puede necesitar cambios si la Junta
 * empieza a rechazar versiones antiguas de la app. El `MOODLE_HOST` y
 * el `CAS_URL` son infraestructura de la Junta: si cambian, habrá que
 * actualizarlos, pero no es algo que se toque a menudo.
 *
 * ## Por qué el objeto es `internal`
 *
 * Porque estas constantes son un detalle interno de la app. No forman
 * parte de su API pública (que, en el contexto de un módulo Android,
 * significa "lo que otros módulos del mismo proyecto pueden usar").
 * Si algún día la app se modulariza, estas constantes se quedan en el
 * módulo `core` y no se exponen.
 */
internal object Defaults {

    /**
     * Cabecera `User-Agent` que se envía en las peticiones a los
     * servidores de la Junta.
     *
     * ## Qué es un `User-Agent`
     *
     * Es una cabecera HTTP que dice al servidor qué aplicación está
     * haciendo la petición. Originalmente se usaba para estadísticas,
     * pero hoy muchos servidores la validan: si no empieza por un
     * patrón reconocido, rechazan la petición.
     *
     * En este caso, los servidores de la Junta esperan que empiece
     * por `iMoodle/`, seguido de la versión. Si la versión es
     * demasiado antigua, pueden rechazarla.
     */
    const val USER_AGENT = "iMoodle/2.0"

    /**
     * Host donde viven todas las instancias de Moodle de la Junta.
     *
     * Cada provincia tiene su propia instancia, pero todas comparten
     * el mismo host. La diferencia está en el path
     * (`/centros/<slug>/`), no en el host.
     */
    const val MOODLE_HOST = "educacionadistancia.juntadeandalucia.es"

    /**
     * URL del servidor de identificación único de la Junta.
     *
     * El CAS es el "portero electrónico" de la Junta. Esta URL
     * apunta al endpoint de login del CAS, al que hay que redirigir
     * al usuario para que se identifique.
     */
    const val CAS_URL = "https://edea.juntadeandalucia.es/cas/login"
}