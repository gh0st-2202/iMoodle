package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import onion.gh0st.iMoodle.core.await
import java.net.URLEncoder

/**
 * Cliente de la API REST de Moodle (web services).
 *
 * ## Qué hace
 *
 * Es el único punto por el que la app habla con `webservice/rest/server.php`.
 * Los repositorios le piden "ejecuta la función `core_course_get_courses`
 * con estos parámetros" y reciben el JSON de respuesta como `String`; ellos
 * se encargan de interpretarlo. Este cliente solo se ocupa de lo común:
 *
 * 1. Añadir el `wstoken`, la función y el formato a cada petición.
 * 2. Detectar los errores de Moodle (`{"exception": ...}`) y convertirlos
 *    en [MoodleApiException].
 * 3. Detectar que el token ha caducado (`invalidtoken`), **refrescarlo una
 *    sola vez** y reintentar la petición original.
 * 4. Traducir los fallos de red a [AuthException.Network].
 *
 * ## Dos formas de enviar los parámetros
 *
 * - [callREST]: parámetros en la *query string* de un `GET`. Es el modo
 *   habitual.
 * - [callRESTWithFormBody]: parámetros en el cuerpo de un `POST`
 *   (`application/x-www-form-urlencoded`). Se usa cuando hay parámetros
 *   anidados con corchetes (`plugindata[...]`) o contenido largo que no
 *   conviene meter en la URL.
 *
 * Ambos comparten **exactamente** la misma lógica de detección de errores
 * y de refresco del token (ver [execute]); solo difieren en cómo se
 * construye la petición HTTP.
 *
 * ## Refresco de token «single-flight»
 *
 * Varias pantallas pueden lanzar peticiones a la vez. Si el token caduca,
 * todas fallarán casi al mismo tiempo, pero solo una debe pedir el token
 * nuevo. Por eso el refresco se serializa con un [Mutex] y cada petición
 * recuerda **qué token usó**: al entrar en la sección crítica, si el token
 * actual ya es distinto del que ella usó, otra corrutina ya lo refrescó y
 * basta con reintentar.
 *
 * Cuando el refresco no es posible de forma definitiva (el
 * [TokenRefresher] devuelve `null`), se llama **exactamente una vez** a
 * [onInvalidToken] y todas las peticiones fallan con
 * [AuthException.InvalidToken]. Los fallos transitorios del refresco
 * (sin red, 5xx) llegan como excepción y **no** cierran la sesión.
 *
 * ## Por qué es `open`
 *
 * Para poder sustituir [callREST] y [callRESTWithFormBody] en los tests
 * por fakes que devuelven JSON fijo sin tocar la red.
 *
 * @property config Configuración de la instancia de Moodle (base URL, `User-Agent`…).
 * @param client Cliente HTTP ya configurado (cookies, timeouts…).
 * @param tokenRefresher Estrategia para obtener un token nuevo.
 * @param onTokenRefreshed Se invoca con el token nuevo cada vez que se refresca,
 *   para que el llamador lo persista.
 * @param onInvalidToken Se invoca una única vez cuando el token no se puede
 *   refrescar y hace falta un login manual.
 * @param initialToken Token con el que arranca el cliente.
 */
open class MoodleApiClient(
    private val client: OkHttpClient,
    val config: MoodleConfig,
    private val tokenRefresher: TokenRefresher,
    private val onTokenRefreshed: suspend (String) -> Unit = {},
    private val onInvalidToken: suspend () -> Unit = {},
    initialToken: String,
) {

    private val _token = MutableStateFlow(initialToken)

    /**
     * Token vigente. Cambia cuando se refresca; los repositorios lo leen
     * para añadirlo a las URLs de archivos e imágenes.
     */
    val token: StateFlow<String> = _token.asStateFlow()

    /** Serializa el refresco del token y el aviso de invalidación. */
    private val refreshMutex = Mutex()

    /** `true` cuando ya se avisó de que la sesión ha dejado de ser válida. Protegido por [refreshMutex]. */
    private var invalidated = false

    /**
     * Ejecuta una función REST enviando los parámetros en la URL (`GET`).
     *
     * @param function Nombre de la función de Moodle (`core_course_get_courses`…).
     * @param params Parámetros de la función.
     * @return Cuerpo JSON de la respuesta.
     * @throws MoodleApiException si Moodle responde con un error o con un HTTP no exitoso.
     * @throws AuthException.Network si falla la red.
     * @throws AuthException.InvalidToken si el token caducó y no se pudo refrescar.
     */
    open suspend fun callREST(
        function: String,
        params: MoodleParams = MoodleParams(),
    ): String {
        val builtParams = params.build()
        return execute(allowRetry = true) { token -> performRequest(function, builtParams, token) }
    }

    /**
     * Ejecuta una función REST enviando los parámetros en el cuerpo
     * como `application/x-www-form-urlencoded` (`POST`).
     *
     * Es el modo adecuado para parámetros con corchetes
     * (`plugindata[...]`) o de gran tamaño.
     *
     * @param function Nombre de la función de Moodle.
     * @param params Parámetros ya aplanados (`clave -> valor`).
     * @return Cuerpo JSON de la respuesta.
     * @throws MoodleApiException si Moodle responde con un error o con un HTTP no exitoso.
     * @throws AuthException.Network si falla la red.
     * @throws AuthException.InvalidToken si el token caducó y no se pudo refrescar.
     */
    open suspend fun callRESTWithFormBody(
        function: String,
        params: Map<String, String>,
    ): String = execute(allowRetry = true) { token -> performFormBodyRequest(function, params, token) }

    /**
     * Ciclo común de toda llamada: pedir, comprobar el resultado y, si el
     * token ha caducado, refrescar y reintentar **una sola vez**.
     *
     * ## Pasos
     *
     * 1. Anota el token vigente y ejecuta [request] con él.
     * 2. Si la respuesta no indica `invalidtoken`, comprueba que no sea un
     *    error de Moodle (lanza [MoodleApiException]) y la devuelve.
     * 3. Si indica `invalidtoken` y ya era un reintento, invalida la sesión.
     * 4. Si era el primer intento, refresca el token ([refreshTokenOrInvalidate])
     *    y vuelve a ejecutar [request] con `allowRetry = false`.
     *
     * @param allowRetry `true` en el primer intento; `false` en el reintento
     *   posterior al refresco, para evitar bucles infinitos.
     * @param request Función que realiza la petición HTTP con el token dado.
     * @return Cuerpo JSON de la respuesta.
     */
    private suspend fun execute(
        allowRetry: Boolean,
        request: suspend (token: String) -> String,
    ): String = withContext(Dispatchers.IO) {
        val usedToken = _token.value
        val responseBody = request(usedToken)

        if (!responseBody.hasInvalidTokenError()) {
            responseBody.throwIfMoodleException()
            return@withContext responseBody
        }

        if (!allowRetry) failInvalid()

        refreshTokenOrInvalidate(usedToken)
        execute(allowRetry = false, request)
    }

    /**
     * Construye y envía una petición `GET` con los parámetros en la URL.
     *
     * @param function Función de Moodle.
     * @param params Parámetros ya aplanados.
     * @param token Token con el que firmar la petición.
     * @return Cuerpo de la respuesta.
     */
    private suspend fun performRequest(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = send(
        Request.Builder()
            .url(buildUrl(function, params, token))
            .header("User-Agent", config.userAgent)
            .build(),
    )

    /**
     * Construye y envía una petición `POST` con los parámetros en el cuerpo.
     *
     * @param function Función de Moodle.
     * @param params Parámetros ya aplanados.
     * @param token Token con el que firmar la petición.
     * @return Cuerpo de la respuesta.
     */
    private suspend fun performFormBodyRequest(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String {
        val formBody = FormBody.Builder().apply {
            add("wstoken", token)
            add("wsfunction", function)
            add("moodlewsrestformat", "json")
            params.forEach { (key, value) -> add(key, value) }
        }.build()

        return send(
            Request.Builder()
                .url("${config.moodleBase}/webservice/rest/server.php")
                .header("User-Agent", config.userAgent)
                .post(formBody)
                .build(),
        )
    }

    /**
     * Envía una petición y devuelve el cuerpo de la respuesta.
     *
     * ## Traducción de errores
     *
     * - Cancelación de la corrutina: se propaga tal cual (y `await()`
     *   cancela la petición HTTP en vuelo).
     * - [AuthException] y [MoodleApiException]: se propagan.
     * - HTTP no exitoso: [MoodleApiException] con código `http_<código>`.
     * - Cualquier otra excepción (DNS, timeout, SSL…): [AuthException.Network].
     *
     * @param request Petición ya construida.
     * @return Cuerpo de la respuesta (cadena vacía si no hay cuerpo).
     */
    private suspend fun send(request: Request): String = try {
        client.newCall(request).await().use { httpResponse ->
            val responseBody = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                throw MoodleApiException("http_${httpResponse.code}", "Error HTTP ${httpResponse.code}")
            }
            responseBody
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: AuthException) {
        throw exception
    } catch (exception: MoodleApiException) {
        throw exception
    } catch (exception: Exception) {
        throw AuthException.Network(exception)
    }

    /**
     * Garantiza que el token vigente es distinto de [usedToken] refrescándolo
     * si hace falta, o invalida la sesión si no es posible.
     *
     * Se ejecuta dentro de [refreshMutex], por lo que solo una corrutina a
     * la vez llega a llamar al [TokenRefresher]:
     *
     * - Si la sesión ya estaba invalidada → se considera fallido.
     * - Si el token ya cambió desde que se hizo la petición → otra corrutina
     *   lo refrescó; se considera correcto sin pedir otro.
     * - Si no → se pide uno nuevo. Con `null` se considera fallido; si el
     *   refresher lanza una excepción (fallo transitorio), esta se propaga
     *   y la sesión **no** se invalida.
     *
     * @param usedToken Token con el que se hizo la petición que falló.
     * @throws AuthException.InvalidToken si el refresco no es posible.
     */
    private suspend fun refreshTokenOrInvalidate(usedToken: String) {
        val refreshedSuccessfully = refreshMutex.withLock {
            when {
                invalidated -> false
                _token.value != usedToken -> true
                else -> {
                    val freshToken = tokenRefresher.refresh()
                    if (freshToken == null) {
                        false
                    } else {
                        _token.value = freshToken
                        onTokenRefreshed(freshToken)
                        true
                    }
                }
            }
        }
        if (!refreshedSuccessfully) failInvalid()
    }

    /**
     * Marca la sesión como inválida (avisando **una sola vez**, aunque
     * fallen varias peticiones en paralelo) y lanza
     * [AuthException.InvalidToken].
     *
     * @throws AuthException.InvalidToken siempre.
     */
    private suspend fun failInvalid(): Nothing {
        refreshMutex.withLock {
            if (!invalidated) {
                invalidated = true
                onInvalidToken()
            }
        }
        throw AuthException.InvalidToken()
    }

    /**
     * Construye la URL de una petición `GET` a `server.php`.
     *
     * Los nombres de parámetros anidados (`plugindata[...]`) se envían con
     * los corchetes codificados manualmente porque Moodle no interpreta
     * bien los que añade el codificador estándar de URLs.
     *
     * @param function Función de Moodle.
     * @param params Parámetros ya aplanados.
     * @param token Token de la petición.
     * @return URL completa, lista para enviar.
     */
    private fun buildUrl(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = buildString {
        append("${config.moodleBase}/webservice/rest/server.php")
        append("?wstoken=").append(URLEncoder.encode(token, "UTF-8"))
        append("&wsfunction=").append(URLEncoder.encode(function, "UTF-8"))
        append("&moodlewsrestformat=json")
        params.forEach { (parameterName, parameterValue) ->
            val encodedName = parameterName.replace("[", "%5B").replace("]", "%5D")
            append("&").append(encodedName)
                .append("=").append(URLEncoder.encode(parameterValue, "UTF-8"))
        }
    }
}
