package onion.gh0st.iMoodle.api.pasen.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.api.pasen.config.PasenConfig
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.domain.model.session.Enrollment
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import org.json.JSONObject

/**
 * Cliente HTTP de la API de Pasen.
 *
 * ## Qué hace esta clase
 *
 * Es la encargada de hablar directamente con los servidores de
 * Pasen. Concretamente, expone cuatro operaciones:
 *
 * 1. **Login**: autentica al usuario con sus credenciales de la
 *    Junta y obtiene un token de acceso.
 * 2. **Info de sesión**: recupera la matrícula del alumno en el
 *    centro (necesaria para el resto de peticiones).
 * 3. **Horario**: descarga el JSON del horario semanal.
 * 4. **Datos del alumno**: descarga el JSON con los datos personales
 *    y de tutores.
 *
 * ## Qué NO hace esta clase
 *
 * - **No parsea los JSONs**. Los devuelve como cadenas. El parseo lo
 *   hacen `PasenScheduleParser` y `PasenStudentInfoParser`. Esta
 *   separación permite testear el parseo sin hacer peticiones HTTP.
 * - **No guarda nada en caché**. La caché es responsabilidad de los
 *   repositorios que usan este cliente.
 * - **No gestiona tokens caducados**. El token de Pasen dura unos 30
 *   minutos y no hay forma de refrescarlo. Cuando caduca, hay que
 *   volver a hacer login. Por eso los repositorios no cachean los
 *   tokens: los piden frescos cada vez.
 *
 * ## Sobre los errores
 *
 * Cualquier fallo (red caída, timeout, respuesta inesperada, código
 * HTTP no exitoso) se convierte en `IllegalStateException` con un
 * mensaje descriptivo. La excepción de cancelación de corrutinas se
 * propaga tal cual.
 *
 * ## Sobre la seguridad
 *
 * Las credenciales se envían siempre por HTTPS. El token se envía en
 * la cabecera `Authorization` con el prefijo `Bearer`, que es el
 * estándar OAuth2. La contraseña nunca se registra en logs.
 *
 * @param client Cliente HTTP de OkHttp ya configurado. Se reutiliza
 *   el cliente global de la app para compartir el pool de conexiones.
 */
class PasenApiClient(
    private val client: OkHttpClient,
) {

    /**
     * Autentica al usuario contra Pasen y devuelve el token de
     * acceso.
     *
     * ## Cómo funciona OAuth2 con contraseña
     *
     * Pasen usa el "flujo de contraseña" de OAuth2. Es el más simple
     * de todos: el cliente (nuestra app) envía el usuario y la
     * contraseña directamente al servidor de autorización, y este
     * devuelve un token de acceso. No hay redirecciones ni pantallas
     * intermedias.
     *
     * Para que el servidor acepte la petición, hay que incluir:
     *
     * - El usuario y la contraseña del alumno.
     * - El `grant_type` (siempre `"password"` en este flujo).
     * - El `scope` (el permiso solicitado).
     * - El `client_id` (el identificador de la app).
     *
     * Todo ello se envía en el cuerpo de la petición como
     * `application/x-www-form-urlencoded`, no como JSON.
     *
     * ## Qué devuelve
     *
     * Un `access_token`, que es una cadena larga que hay que incluir
     * en cada petición posterior en la cabecera
     * `Authorization: Bearer <token>`.
     *
     * ## Cuánto dura
     *
     * El token de Pasen dura aproximadamente 30 minutos. No hay
     * `refresh_token`. Pasado ese tiempo, hay que volver a hacer
     * login con las credenciales.
     *
     * ## Manejo de errores
     *
     * - **HTTP no exitoso** (4xx, 5xx): `IllegalStateException` con
     *   el código.
     * - **Respuesta sin `access_token`**: `IllegalStateException`.
     * - **Cualquier otro fallo de red**: `IllegalStateException` con
     *   la causa original.
     * - **Cancelación**: se propaga.
     *
     * @param credentials Credenciales del usuario de la Junta.
     * @return El `access_token` listo para usar como `Bearer`.
     * @throws IllegalStateException si el login falla.
     */
    suspend fun login(credentials: JuntaCredentials): String = withContext(Dispatchers.IO) {
        val requestBody = FormBody.Builder()
            .add("username", credentials.username)
            .add("password", credentials.password)
            .add("grant_type", "password")
            .add("scope", PasenConfig.SCOPE)
            .add("client_id", PasenConfig.CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url(PasenConfig.OAUTH_URL)
            .header("User-Agent", PasenConfig.USER_AGENT)
            .header("Content-Type", PasenConfig.OAUTH_CONTENT_TYPE)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).await().use { httpResponse ->
                val responseBody = httpResponse.body?.string().orEmpty()
                if (!httpResponse.isSuccessful) {
                    throw IllegalStateException(
                        "Error login iPasen: HTTP ${httpResponse.code}",
                    )
                }
                JSONObject(responseBody).optString("access_token")
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("No access_token")
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException("Error de red en iPasen", exception)
        }
    }

    /**
     * Recupera la matrícula del alumno en el centro.
     *
     * ## Qué es `info-session`
     *
     * Es el primer endpoint al que hay que llamar después del login.
     * Devuelve información sobre el usuario autenticado: su nombre, su
     * perfil (alumno, padre, tutor), y sus "matrículas"
     * (`enrollments`).
     *
     * ## Qué es una matrícula
     *
     * Es la unidad que identifica al alumno dentro de un centro
     * concreto. Una misma persona puede tener varias matrículas (una
     * por centro, una por curso), aunque la app solo usa la primera
     * que devuelve el servidor.
     *
     * El identificador de la matrícula (`enrollment`) es necesario
     * para todos los endpoints de datos (horario, notas, faltas…).
     *
     * ## Qué se devuelve
     *
     * Un `Enrollment` de dominio con el ID, el código del centro y el
     * nombre del centro. Los demás campos del JSON se ignoran.
     *
     * @param token `access_token` de Pasen obtenido en [login].
     * @return La matrícula del alumno.
     * @throws IllegalStateException si la respuesta no es válida o no
     *   contiene `enrollments`.
     */
    suspend fun getSessionInfo(token: String): Enrollment = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${PasenConfig.BASE_URL}/generales/info-session")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", PasenConfig.USER_AGENT)
            .build()

        try {
            client.newCall(request).await().use { httpResponse ->
                val responseBody = httpResponse.body?.string().orEmpty()
                val responseJson = JSONObject(responseBody)
                if (!responseJson.optBoolean("success")) {
                    throw IllegalStateException(responseJson.optString("message"))
                }

                val enrollmentsArray = responseJson
                    .optJSONObject("data")
                    ?.optJSONArray("enrollments")
                val firstEnrollment = enrollmentsArray?.optJSONObject(0)
                    ?: throw IllegalStateException("No enrollment")

                Enrollment(
                    id = firstEnrollment.optString("enrollment"),
                    centerCode = firstEnrollment.optString("centerCode"),
                    centerName = firstEnrollment.optString("description"),
                )
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Error info-session iPasen", exception)
        }
    }

    /**
     * Descarga el JSON crudo del horario semanal del alumno.
     *
     * ## Qué hace
     *
     * Hace una petición `GET` a `horarios/get-horario` con el ID de
     * matrícula del alumno. El servidor responde con un JSON que
     * contiene la lista de franjas horarias (día, hora, asignatura,
     * profesor, etc.).
     *
     * ## Por qué se devuelve el JSON sin parsear
     *
     * Porque el parseo lo hace `PasenScheduleParser`, que es una
     * clase aparte. Así se puede testear el parseo sin hacer
     * peticiones HTTP, y se puede cambiar el formato del JSON sin
     * tocar el cliente.
     *
     * @param token `access_token` de Pasen.
     * @param enrollment ID de la matrícula del alumno.
     * @return El cuerpo JSON de la respuesta.
     * @throws IllegalStateException si la respuesta HTTP no es 2xx.
     */
    suspend fun getHorario(token: String, enrollment: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${PasenConfig.BASE_URL}/horarios/get-horario?enrollment=$enrollment")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", PasenConfig.USER_AGENT)
                .header("Accept", PasenConfig.API_CONTENT_TYPE)
                .build()

            try {
                client.newCall(request).await().use { httpResponse ->
                    val responseBody = httpResponse.body?.string().orEmpty()
                    if (!httpResponse.isSuccessful) {
                        throw IllegalStateException(
                            "Error horario iPasen: HTTP ${httpResponse.code}",
                        )
                    }
                    responseBody
                }
            } catch (exception: Exception) {
                throw IllegalStateException("Error de red en get-horario", exception)
            }
        }

    /**
     * Descarga el JSON crudo de `datos-alumno`.
     *
     * ## Qué contiene este endpoint
     *
     * Devuelve los datos personales del alumno (nombre, edad, fecha
     * de nacimiento, sexo) y los datos de sus tutores legales. En el
     * JSON, los datos vienen agrupados en "secciones" (por ejemplo,
     * "Datos Personales", "Tutores Legales"), y cada sección es una
     * lista de pares clave-valor.
     *
     * ## Por qué se devuelve el JSON sin parsear
     *
     * Mismo motivo que en [getHorario]: el parseo lo hace
     * `PasenStudentInfoParser`.
     *
     * @param token `access_token` de Pasen.
     * @param enrollment ID de la matrícula del alumno.
     * @return El cuerpo JSON de la respuesta.
     * @throws IllegalStateException si la respuesta HTTP no es 2xx.
     */
    suspend fun getDatosAlumno(token: String, enrollment: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${PasenConfig.BASE_URL}/seguimiento/datos-alumno?enrollment=$enrollment")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", PasenConfig.USER_AGENT)
                .header("Accept", PasenConfig.API_CONTENT_TYPE)
                .build()

            try {
                client.newCall(request).await().use { httpResponse ->
                    val responseBody = httpResponse.body?.string().orEmpty()
                    if (!httpResponse.isSuccessful) {
                        throw IllegalStateException(
                            "Error datos alumno iPasen: HTTP ${httpResponse.code}",
                        )
                    }
                    responseBody
                }
            } catch (exception: Exception) {
                throw IllegalStateException("Error de red en datos-alumno", exception)
            }
        }
}