package onion.gh0st.iMoodle.api.pasen.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
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
 * HTTP no exitoso) se convierte en [PasenApiException] con un
 * mensaje descriptivo que indica la operación que falló. La
 * excepción de cancelación de corrutinas se propaga siempre tal cual
 * (antes se envolvía en una `IllegalStateException`, lo que impedía
 * cancelar correctamente las cargas al salir de la pantalla).
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
     * Autentica al usuario contra Pasen y devuelve el token de acceso.
     *
     * ## Cómo funciona OAuth2 con contraseña
     *
     * Pasen usa el "flujo de contraseña" de OAuth2: la app envía el
     * usuario y la contraseña de la Junta junto con el identificador
     * de cliente de la app oficial, y el servidor responde con un
     * `access_token` que se usará en el resto de peticiones.
     *
     * @param credentials Credenciales de la Junta.
     * @return El `access_token` (válido unos 30 minutos).
     * @throws PasenApiException si falla la red, el servidor responde
     *   con error o la respuesta no contiene token.
     */
    suspend fun login(credentials: JuntaCredentials): String = guarded("login") {
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

        val responseBody = fetchBody(request, operation = "login")
        JSONObject(responseBody).optString("access_token")
            .takeIf { accessToken -> accessToken.isNotBlank() }
            ?: throw PasenApiException("iPasen no devolvió access_token")
    }

    /**
     * Obtiene la matrícula del alumno (la primera, si hay varias).
     *
     * Es necesaria para pedir el horario y los datos personales.
     *
     * @param token Token devuelto por [login].
     * @return La matrícula con el identificador y el centro.
     * @throws PasenApiException si falla la petición o el alumno no
     *   tiene matrículas.
     */
    suspend fun getSessionInfo(token: String): Enrollment = guarded("info-session") {
        val request = Request.Builder()
            .url("${PasenConfig.BASE_URL}/generales/info-session")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", PasenConfig.USER_AGENT)
            .build()

        val responseBody = fetchBody(request, operation = "info-session", requireSuccessfulStatus = false)
        val responseJson = JSONObject(responseBody)
        if (!responseJson.optBoolean("success")) {
            throw PasenApiException(
                responseJson.optString("message").ifBlank { "iPasen rechazó la petición info-session" },
            )
        }

        val firstEnrollment = responseJson
            .optJSONObject("data")
            ?.optJSONArray("enrollments")
            ?.optJSONObject(0)
            ?: throw PasenApiException("El alumno no tiene matrículas en iPasen")

        Enrollment(
            id = firstEnrollment.optString("enrollment"),
            centerCode = firstEnrollment.optString("centerCode"),
            centerName = firstEnrollment.optString("description"),
        )
    }

    /**
     * Descarga el horario semanal en bruto (JSON como cadena).
     *
     * @param token Token devuelto por [login].
     * @param enrollment Identificador de la matrícula.
     * @return El JSON del horario, sin interpretar.
     * @throws PasenApiException si falla la red o el servidor responde con error.
     */
    suspend fun getHorario(token: String, enrollment: String): String = guarded("get-horario") {
        fetchBody(
            authorizedGet(token, "/horarios/get-horario", enrollment),
            operation = "get-horario",
        )
    }

    /**
     * Descarga los datos personales del alumno en bruto (JSON como cadena).
     *
     * @param token Token devuelto por [login].
     * @param enrollment Identificador de la matrícula.
     * @return El JSON de datos del alumno, sin interpretar.
     * @throws PasenApiException si falla la red o el servidor responde con error.
     */
    suspend fun getDatosAlumno(token: String, enrollment: String): String = guarded("datos-alumno") {
        fetchBody(
            authorizedGet(token, "/seguimiento/datos-alumno", enrollment),
            operation = "datos-alumno",
        )
    }

    /**
     * Construye una petición `GET` autenticada que lleva la matrícula
     * como parámetro.
     *
     * La matrícula se añade con el constructor de URLs de OkHttp, que
     * se encarga de codificarla correctamente, en lugar de concatenarla
     * a mano.
     *
     * @param token Token de acceso.
     * @param path Ruta relativa a [PasenConfig.BASE_URL].
     * @param enrollment Identificador de la matrícula.
     * @return La petición lista para enviar.
     */
    private fun authorizedGet(token: String, path: String, enrollment: String): Request {
        val url = "${PasenConfig.BASE_URL}$path".toHttpUrl().newBuilder()
            .addQueryParameter("enrollment", enrollment)
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", PasenConfig.USER_AGENT)
            .header("Accept", PasenConfig.API_CONTENT_TYPE)
            .build()
    }

    /**
     * Envía la petición y devuelve el cuerpo de la respuesta.
     *
     * @param request Petición a enviar.
     * @param operation Nombre de la operación, para el mensaje de error.
     * @param requireSuccessfulStatus Si es `true`, un HTTP no exitoso
     *   lanza [PasenApiException]. Se desactiva solo cuando el cuerpo
     *   del error trae información útil (como en `info-session`).
     * @return Cuerpo de la respuesta (cadena vacía si no hay cuerpo).
     */
    private suspend fun fetchBody(
        request: Request,
        operation: String,
        requireSuccessfulStatus: Boolean = true,
    ): String = client.newCall(request).await().use { httpResponse ->
        val responseBody = httpResponse.body?.string().orEmpty()
        if (requireSuccessfulStatus && !httpResponse.isSuccessful) {
            throw PasenApiException("iPasen respondió HTTP ${httpResponse.code} en $operation")
        }
        responseBody
    }

    /**
     * Ejecuta una operación en `Dispatchers.IO` unificando el
     * tratamiento de errores de todo el cliente.
     *
     * - [CancellationException]: se propaga sin tocar.
     * - [PasenApiException]: se propaga tal cual.
     * - Cualquier otra excepción (red, JSON mal formado…): se envuelve
     *   en [PasenApiException] indicando la operación.
     *
     * @param operation Nombre de la operación, para el mensaje de error.
     * @param block Trabajo a realizar.
     * @return El resultado de [block].
     */
    private suspend fun <T> guarded(operation: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PasenApiException) {
                throw exception
            } catch (exception: Exception) {
                throw PasenApiException("Error en iPasen ($operation)", exception)
            }
        }
}
