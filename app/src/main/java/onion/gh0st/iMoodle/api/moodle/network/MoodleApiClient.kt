package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import java.net.URLEncoder

/**
 * Cliente REST de la API de Moodle, con refresco de token
 * transparente.
 *
 * ## Qué hace esta clase
 *
 * Es la pieza central de toda la comunicación con Moodle. Los
 * repositorios de `api/moodle/repository/` le piden cosas como
 * "dame los cursos del usuario 42" o "dame las tareas del curso 7", y
 * esta clase se encarga de:
 *
 * 1. **Construir la URL** de la petición con el formato que espera
 *    Moodle (token en query string, función como parámetro, etc.).
 * 2. **Ejecutar la petición HTTP** y devolver el cuerpo de la
 *    respuesta.
 * 3. **Detectar errores** en el cuerpo (Moodle devuelve errores con
 *    `HTTP 200` y el error dentro del JSON, así que hay que
 *    inspeccionar el cuerpo).
 * 4. **Refrescar el token de forma transparente** cuando caduca, sin
 *    que el usuario se entere.
 *
 * ## Cómo funciona el refresco transparente
 *
 * Cuando una petición a Moodle devuelve `invalidtoken`, esta clase:
 *
 * 1. Pide un token nuevo a la estrategia de refresco
 *    ([tokenRefresher]).
 * 2. Si lo obtiene, actualiza su token interno, notifica al llamador
 *    con [onTokenRefreshed] y reintenta la petición original.
 * 3. Si no lo obtiene, notifica al llamador con [onInvalidToken] y
 *    lanza `AuthException.InvalidToken`. El llamador (normalmente,
 *    `DefaultSessionRepository`) forzará el cierre de sesión.
 *
 * ## Concurrencia: single-flight
 *
 * Imagina que la app hace 8 peticiones en paralelo y todas fallan con
 * `invalidtoken` al mismo tiempo. Sin protección, las 8 intentarían
 * refrescar el token a la vez, haciendo 8 logins seguidos. Eso sería
 * un desastre: mucho tráfico innecesario, posible bloqueo de la
 * cuenta, etc.
 *
 * Para evitarlo, esta clase usa un `Mutex` (un cerrojo) que
 * garantiza que **solo una petición refresca el token a la vez**. Las
 * demás, cuando llegan al cerrojo, ven que el token ya ha cambiado y
 * simplemente reintentan su petición original con el token nuevo.
 *
 * A este patrón se le llama **single-flight**: múltiples peticiones
 * concurrentes comparten una única operación costosa.
 *
 * ## Marca de "ya invalidado"
 *
 * Cuando el refresco falla, esta clase no llama a `onInvalidToken`
 * una vez por cada petición en vuelo. Solo lo llama **una vez** por
 * cliente, aunque haya 8 peticiones fallando. Para eso usa la
 * variable interna `invalidated`, protegida por el mismo `Mutex`.
 *
 * ## Qué NO hace
 *
 * - **No decide cuándo refrescar**. Solo reacciona cuando Moodle
 *   devuelve `invalidtoken`.
 * - **No gestiona cookies**. Las cookies las lleva el `OkHttpClient`
 *   que recibe por constructor.
 * - **No distingue tipos de error de Moodle**. Cualquier
 *   `accessexception`, `invalidparameter`, etc. se envuelve en
 *   `MoodleApiException`. Solo el `invalidtoken` se trata de forma
 *   especial.
 *
 * ## Por qué es `open`
 *
 * Porque los tests unitarios necesitan sustituir la implementación
 * real de las peticiones HTTP por una falsa. Para eso, la clase se
 * marca `open` y el método `callREST` también, de forma que los tests
 * puedan heredar y sobrescribir. En producción nunca se hereda.
 *
 * @param client Cliente HTTP con las cookies de la sesión ya
 *   enganchadas. Se usa para todas las peticiones.
 * @param config Configuración de Moodle de la provincia. Determina la
 *   URL base y el User-Agent.
 * @param tokenRefresher Estrategia de refresco del token. Se invoca
 *   cuando Moodle devuelve `invalidtoken`.
 * @param onTokenRefreshed Callback que se invoca cuando se ha
 *   refrescado el token con éxito. Normalmente,
 *   `DefaultSessionRepository` lo usa para actualizar el estado
 *   reactivo.
 * @param onInvalidToken Callback que se invoca cuando el token deja
 *   de valer y no se ha podido refrescar. Normalmente,
 *   `DefaultSessionRepository` lo usa para forzar el cierre de
 *   sesión.
 * @param initialToken Token inicial de la sesión. Viene del login
 *   inicial (`MoodleTokenFetcher`) o del auto-login (recuperado del
 *   disco).
 */
open class MoodleApiClient(
    private val client: OkHttpClient,
    val config: MoodleConfig,
    private val tokenRefresher: TokenRefresher,
    private val onTokenRefreshed: suspend (String) -> Unit = {},
    private val onInvalidToken: suspend () -> Unit = {},
    initialToken: String,
) {

    /** Estado interno del token. Solo se modifica con el `refreshMutex` tomado. */
    private val _token = MutableStateFlow(initialToken)

    /**
     * Token actual, observable.
     *
     * ## Por qué es un `StateFlow`
     *
     * Porque hay sitios que necesitan observar el token y reaccionar
     * a sus cambios (por ejemplo, para descargar archivos con la URL
     * correcta). Con un `StateFlow`, esos sitios pueden hacer
     * `api.token.collect { ... }` y enterarse de los refrescos.
     *
     * ## Por qué es `val` público pero `_token` privado
     *
     * `_token` es la fuente de verdad, mutable. `token` es la vista
     * pública, de solo lectura. Así nadie de fuera puede modificar el
     * token sin pasar por el flujo de refresco.
     */
    val token: StateFlow<String> = _token.asStateFlow()

    /**
     * Cerrojo que serializa el refresco del token.
     *
     * ## Qué protege
     *
     * - El propio refresco (para que solo uno se ejecute a la vez).
     * - La variable `invalidated` (para que solo uno notifique la
     *   invalidez).
     *
     * ## Por qué es `Mutex` y no `synchronized`
     *
     * Porque las operaciones son `suspend` (el refresco hace
     * peticiones HTTP). `synchronized` bloquearía el hilo, que es
     * justo lo que no queremos en una corrutina. `Mutex` es la
     * versión "suspend-friendly" de un cerrojo.
     */
    private val refreshMutex = Mutex()

    /**
     * Indica si ya se ha notificado la invalidez del token.
     *
     * ## Para qué sirve
     *
     * Evita llamar a [onInvalidToken] más de una vez por cliente. Si
     * llegan 8 peticiones en paralelo y todas fallan, solo la primera
     * que llegue al cerrojo notifica la invalidez; las demás ven la
     * marca puesta y no vuelven a notificar.
     *
     * ## Por qué es `var` y no `val`
     *
     * Porque cambia de `false` a `true` una vez. Solo se toca con el
     * [refreshMutex] tomado, así que no hay riesgo de concurrencia.
     */
    private var invalidated = false

    /**
     * Ejecuta una llamada a una función REST de Moodle.
     *
     * ## Qué hace
     *
     * Es el punto de entrada público de la clase. Delega en
     * [callRESTInternal] con `allowRetry = true`, permitiendo que la
     * petición se reintente una vez si el token caduca.
     *
     * ## Por qué es `open`
     *
     * Para que los tests unitarios puedan heredar y sobrescribir este
     * método con una implementación falsa que no haga peticiones
     * HTTP. En producción nunca se hereda.
     *
     * @param function Nombre de la función REST de Moodle. Por
     *   ejemplo, `"core_enrol_get_users_courses"` o
     *   `"mod_assign_get_assignments"`.
     * @param params Parámetros de la función. Se construyen con
     *   `MoodleParams`.
     * @return El cuerpo de la respuesta (JSON) como cadena.
     * @throws MoodleApiException si Moodle devuelve un error distinto
     *   de `invalidtoken`.
     * @throws AuthException.InvalidToken si el token caduca y no se
     *   puede refrescar.
     * @throws AuthException.Network si hay un fallo de transporte.
     */
    open suspend fun callREST(
        function: String,
        params: MoodleParams = MoodleParams(),
    ): String = callRESTInternal(function, params.build(), allowRetry = true)

    // --- Métodos internos ---

    /**
     * Implementación recursiva de [callREST] con control de reintento.
     *
     * ## Flujo
     *
     * 1. Ejecuta la petición con el token actual.
     * 2. Si la respuesta no es un `invalidtoken`, la devuelve tal cual
     *    (lanzando `MoodleApiException` si contiene un error de
     *    Moodle).
     * 3. Si es un `invalidtoken` y no se permite reintentar, falla
     *    con `AuthException.InvalidToken`.
     * 4. Si es un `invalidtoken` y sí se permite reintentar, intenta
     *    refrescar el token y reintentar la petición.
     *
     * ## Por qué es recursiva
     *
     * Porque tras refrescar el token, la petición se vuelve a
     * ejecutar. En lugar de duplicar el código, se llama a este mismo
     * método con `allowRetry = false` (para evitar bucles infinitos
     * si el token nuevo también falla).
     *
     * @param function Nombre de la función REST.
     * @param params Parámetros ya serializados.
     * @param allowRetry Si `true`, permite un reintento con token
     *   refrescado al detectar `invalidtoken`. Si `false`, falla
     *   directamente.
     * @return El cuerpo de la respuesta (JSON).
     * @throws MoodleApiException si Moodle devuelve un error distinto
     *   de `invalidtoken`.
     * @throws AuthException.InvalidToken si el token caduca y no se
     *   puede refrescar.
     * @throws AuthException.Network si hay un fallo de transporte.
     */
    private suspend fun callRESTInternal(
        function: String,
        params: Map<String, String>,
        allowRetry: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val usedToken = _token.value
        val responseBody = performRequest(function, params, usedToken)
        if (!responseBody.hasInvalidTokenError()) {
            responseBody.throwIfMoodleException()
            return@withContext responseBody
        }
        if (!allowRetry) return@withContext failInvalid()
        retryWithFreshToken(function, params, usedToken)
    }

    /**
     * Ejecuta una petición HTTP a Moodle y devuelve el cuerpo como
     * cadena.
     *
     * ## Manejo de errores
     *
     * - **Errores de red** (timeout, sin conexión, DNS, etc.):
     *   `CancellationException` se propaga; cualquier otra excepción
     *   se envuelve en `AuthException.Network`.
     * - **Errores de Moodle tipados** (`AuthException`,
     *   `MoodleApiException`): se propagan tal cual.
     * - **Códigos HTTP no exitosos** (4xx, 5xx): se convierten en
     *   `MoodleApiException` con código `http_<número>`.
     *
     * ## Por qué el cuerpo se devuelve como `String` y no como
     * `JSONObject`
     *
     * Porque `org.json` no está disponible en tests JVM puros. Al
     * devolver `String`, los repositorios pueden parsear el JSON con
     * la librería que prefieran (o incluso no parsearlo, si solo
     * quieren pasarlo a otra capa).
     *
     * @param function Nombre de la función REST.
     * @param params Parámetros ya serializados.
     * @param token Token a usar en esta petición concreta.
     * @return El cuerpo de la respuesta (JSON) como cadena.
     * @throws MoodleApiException si el código HTTP no es 2xx.
     * @throws AuthException.Network si hay un fallo de transporte.
     */
    private suspend fun performRequest(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = try {
        client.newCall(
            Request.Builder()
                .url(buildUrl(function, params, token))
                .header("User-Agent", config.userAgent)
                .build(),
        ).await().use { httpResponse ->
            val responseBody = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                throw MoodleApiException(
                    "http_${httpResponse.code}",
                    "Error HTTP ${httpResponse.code}",
                )
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
     * Refresca el token (una sola vez entre todas las llamadas
     * concurrentes) y reintenta la petición.
     *
     * ## Cómo funciona
     *
     * 1. Toma el cerrojo.
     * 2. Comprueba tres casos:
     *    - **Ya invalidado**: no hay nada que hacer, falla.
     *    - **Otra petición ya refrescó**: el token actual es distinto
     *      del que usó esta petición, así que otra corrutina ya hizo
     *      el trabajo. Solo hay que reintentar.
     *    - **Es la primera en llegar**: se pide un token nuevo con
     *      [tokenRefresher]. Si devuelve uno, se actualiza el estado,
     *      se notifica con [onTokenRefreshed] y se marca como
     *      refrescado.
     * 3. Suelta el cerrojo.
     * 4. Si no se pudo refrescar, falla con
     *    `AuthException.InvalidToken`. Si se pudo, reintenta la
     *    petición con `allowRetry = false` (para no entrar en bucle).
     *
     * @param function Nombre de la función REST.
     * @param params Parámetros ya serializados.
     * @param usedToken Token con el que falló la petición original.
     *   Sirve para distinguir "otra corrutina ya refrescó" de "hay
     *   que refrescar".
     * @return El cuerpo de la respuesta (JSON) del reintento.
     * @throws AuthException.InvalidToken si no se pudo refrescar.
     */
    private suspend fun retryWithFreshToken(
        function: String,
        params: Map<String, String>,
        usedToken: String,
    ): String {
        val refreshedSuccessfully: Boolean = refreshMutex.withLock {
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
        return callRESTInternal(function, params, allowRetry = false)
    }

    /**
     * Notifica la invalidez del token (una sola vez) y lanza
     * `AuthException.InvalidToken`.
     *
     * ## Por qué usa el cerrojo
     *
     * Para garantizar que [onInvalidToken] se llama como máximo una
     * vez por cliente, aunque haya varias peticiones fallando en
     * paralelo. El llamador recibe una única notificación de "sesión
     * expirada", y la app reacciona cerrando sesión una sola vez.
     *
     * ## Por qué siempre lanza
     *
     * Porque este método se invoca desde rutas que ya saben que el
     * token no se puede recuperar. Devolver `Unit` no tendría sentido:
     * el llamador necesita saber que la operación ha fallado.
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
     * Construye la URL completa de una llamada REST.
     *
     * ## Formato de la URL
     *
     * ```
     * <base>/webservice/rest/server.php
     *     ?wstoken=<token>
     *     &wsfunction=<function>
     *     &moodlewsrestformat=json
     *     &<param1>=<value1>
     *     &<param2>=<value2>
     *     ...
     * ```
     *
     * ## Por qué se codifican los valores
     *
     * Porque los valores pueden contener caracteres especiales
     * (`&`, `=`, espacios, tildes, etc.) que romperían la URL si se
     * metieran tal cual. `URLEncoder.encode` los convierte en
     * secuencias seguras (`%20`, `%C3%B3`, etc.).
     *
     * @param function Nombre de la función REST.
     * @param params Parámetros ya serializados.
     * @param token Token a incluir.
     * @return La URL completa, lista para usar en una petición HTTP.
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
            append("&").append(URLEncoder.encode(parameterName, "UTF-8"))
                .append("=").append(URLEncoder.encode(parameterValue, "UTF-8"))
        }
    }
}