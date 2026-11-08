package onion.gh0st.iMoodle.api.pasen.config

/**
 * Configuración de los endpoints de la API de Pasen.
 *
 * ## Qué es Pasen
 *
 * Pasen (o iPasen) es la aplicación oficial de la Junta de Andalucía
 * para que las familias y el alumnado consulten información
 * académica: horario, calificaciones, faltas de asistencia,
 * comunicaciones del centro, etc. La app oficial funciona mediante
 * una API privada que la Junta no documenta públicamente.
 *
 * ## De dónde salen estos valores
 *
 * Los valores de esta clase (`BASE_URL`, `CLIENT_ID`, `SCOPE`,
 * `USER_AGENT`, etc.) se han obtenido analizando el tráfico de la
 * aplicación oficial de Pasen. No están documentados por la Junta y
 * podrían cambiar sin previo aviso. Están centralizados aquí para
 * que, si algún día cambian, solo haya que tocar este fichero.
 *
 * ## Qué contiene cada constante
 *
 * - **`BASE_URL`**: la dirección del servidor de la Junta donde vive
 *   la API de Pasen.
 * - **`OAUTH_URL`**: el endpoint concreto al que se piden los tokens
 *   de acceso (la "llave" para hacer el resto de peticiones).
 * - **`CLIENT_ID`**: el identificador que la app oficial de Pasen
 *   usa para identificarse ante el servidor. Es como el "nombre de
 *   usuario" de la aplicación, no del alumno.
 * - **`SCOPE`**: el permiso que se solicita al pedir el token. En
 *   este caso, `PASENDROID` indica que se pide un token para una app
 *   Android.
 * - **`USER_AGENT`**: la cabecera que dice al servidor qué
 *   aplicación está haciendo la petición. El servidor de Pasen
 *   valida que empiece por `iPasen/` seguido de una versión reciente;
 *   si no, puede rechazar la petición.
 * - **`OAUTH_CONTENT_TYPE`**: el formato en el que se envían los
 *   datos al pedir el token (`application/x-www-form-urlencoded` es
 *   un formato estándar de formularios HTML).
 * - **`API_CONTENT_TYPE`**: el formato en el que se piden las
 *   respuestas. Pasen espera un tipo MIME específico
 *   (`application/vnd.piomin.app-v1.0+json`) en lugar del típico
 *   `application/json`.
 *
 * ## Cuándo no tocar esto
 *
 * No cambiar los valores salvo que se sepa exactamente lo que se está
 * haciendo. Si la Junta cambia los endpoints o el `USER_AGENT`, habrá
 * que actualizarlos aquí. Pero cambiar un valor "porque suena mejor"
 * romperá la integración.
 */
object PasenConfig {

    /** Dirección base del servidor de la API de Pasen. */
    const val BASE_URL = "https://seneca.juntadeandalucia.es/api/public"

    /** Endpoint al que se piden los tokens de acceso. */
    const val OAUTH_URL = "$BASE_URL/security/oauth/token"

    /** Identificador de cliente OAuth2 que usa la app oficial de Pasen. */
    const val CLIENT_ID = "ipasen"

    /** Permiso que se solicita al pedir el token. */
    const val SCOPE = "PASENDROID"

    /** Cabecera `User-Agent` que el servidor de Pasen espera recibir. */
    const val USER_AGENT = "iPasen/12.10.1(Android;Scale/2.8125)"

    /** Tipo de contenido al enviar la petición de token. */
    const val OAUTH_CONTENT_TYPE = "application/x-www-form-urlencoded"

    /** Tipo de contenido que se pide en las respuestas de la API. */
    const val API_CONTENT_TYPE = "application/vnd.piomin.app-v1.0+json"
}