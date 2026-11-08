package onion.gh0st.iMoodle.api.cas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.core.await
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Realiza el proceso de inicio de sesión contra el servidor de
 * identificación único de la Junta de Andalucía, conocido como CAS.
 *
 * ## Qué es el CAS y por qué lo usamos
 *
 * El CAS (Central Authentication Service) es el "portero electrónico"
 * de la Junta. En lugar de tener una contraseña distinta para cada
 * servicio (Moodle, iPasen, secretaría virtual…), el alumno tiene
 * **una sola cuenta** que el CAS valida. Cuando un servicio necesita
 * saber quién es el usuario, lo redirige al CAS, el usuario se
 * identifica allí, y el CAS le devuelve al servicio una "pulsera"
 * temporal (una cookie) que demuestra que ya se ha identificado.
 *
 * Esta clase hace precisamente eso: hablar con el CAS en nombre del
 * usuario, recoger las "pulseras" (cookies) y devolverlas empaquetadas
 * en un objeto [CasSession] para que el resto de la aplicación pueda
 * usarlas al hacer peticiones a Moodle o a iPasen.
 *
 * ## Cómo funciona el proceso
 *
 * El CAS no es una API REST normal. Para identificarse hay que seguir
 * una secuencia de tres pasos obligatorios:
 *
 * 1. **Pedir el formulario**. La aplicación hace una petición al CAS
 *    con la URL del servicio al que quiere acceder. El CAS responde
 *    con una página HTML que contiene el formulario de login. Dentro
 *    de ese HTML hay un valor oculto llamado `execution` que es único
 *    para esa petición concreta y que hay que devolver en el paso 2.
 * 2. **Enviar las credenciales**. La aplicación rellena el formulario
 *    con el usuario, la contraseña y el `execution` del paso 1, y lo
 *    envía de vuelta al CAS. Si las credenciales son correctas, el
 *    CAS redirige al servicio (en nuestro caso, Moodle), que genera
 *    una cookie de sesión. Si son incorrectas, redirige de vuelta al
 *    formulario con un aviso de error.
 * 3. **Comprobar el resultado**. La aplicación verifica que entre las
 *    cookies que ha recibido está la de Moodle. Si está, el login ha
 *    ido bien. Si no, algo ha fallado.
 *
 * ## Detalle importante sobre los errores
 *
 * El CAS **no devuelve un código de error HTTP** cuando el usuario se
 * equivoca de contraseña. En lugar de eso, redirige al formulario con
 * un parámetro `?error=true`. Por eso no podemos limitarnos a mirar el
 * código HTTP: tenemos que inspeccionar las cookies de la respuesta
 * final. Esta clase ya lo hace.
 *
 * ## Qué NO hace esta clase
 *
 * - **No guarda las credenciales**. Solo las usa durante el login. La
 *   persistencia (para el refresco silencioso) la gestiona otra capa.
 * - **No pide el token de Moodle**. Solo devuelve la sesión CAS con
 *   las cookies. Obtener el `wstoken` de Moodle es trabajo de
 *   `MoodleTokenFetcher`.
 * - **No distingue entre provincias**. La configuración de la URL a la
 *   que hay que volver tras el login viene en [CasConfig], que se
 *   construye para cada provincia concreta.
 *
 * @property config Configuración del CAS para esta instalación. Incluye
 *   la URL del servicio de destino (Moodle de la provincia
 *   correspondiente) y el User-Agent a enviar en cada petición.
 * @property baseClient Cliente HTTP base a partir del cual se construye
 *   el cliente real de la sesión. Se clona para engancharle un gestor
 *   de cookies propio de esta sesión. Compartir el cliente base entre
 *   varias sesiones ahorra recursos (pool de conexiones, hilos) pero
 *   cada sesión tiene su propio "tarro" de cookies para no mezclar
 *   usuarios.
 */
class CasAuthenticator(
    private val config: CasConfig,
    private val baseClient: OkHttpClient = OkHttpClient(),
) {

    /**
     * Ejecuta el proceso completo de login contra el CAS y devuelve una
     * sesión con las cookies listas para hacer peticiones autenticadas.
     *
     * ## Qué hace
     *
     * Es el punto de entrada público de la clase. Internamente delega
     * en [doAuthenticate], pero envuelve la llamada en un bloque de
     * gestión de errores que convierte cualquier excepción inesperada
     * en un [AuthException] tipado. Esto permite que la capa superior
     * (el `LoginViewModel`) decida qué mensaje mostrar al usuario sin
     * tener que inspeccionar excepciones de red o de librerías.
     *
     * ## Por qué se ejecuta en un hilo aparte
     *
     * El login implica varias peticiones HTTP que pueden tardar varios
     * segundos (sobre todo si la red es lenta). Si se hicieran en el
     * hilo principal, la interfaz se congelaría. Por eso todo el
     * proceso se ejecuta en `Dispatchers.IO`, que es el grupo de hilos
     * reservado para operaciones de entrada/salida como esta.
     *
     * ## Manejo de errores
     *
     * Se distinguen tres casos:
     *
     * 1. **Cancelación de la corrutina**: si el usuario cierra la
     *    pantalla o el proceso muere a mitad del login, la excepción
     *    de cancelación se propaga tal cual. No se convierte en error
     *    de login porque no lo es.
     * 2. **Error ya tipado**: si [doAuthenticate] lanza un
     *    [AuthException] (credenciales malas, formulario cambiado,
     *    etc.), se propaga sin modificar.
     * 3. **Cualquier otro error**: se envuelve en
     *    [AuthException.Network] para que la interfaz lo trate como un
     *    problema de conexión.
     *
     * @param credentials Credenciales del usuario (usuario y contraseña
     *   de la Junta).
     * @return La [CasSession] con las cookies de sesión ya cargadas.
     * @throws AuthException si el login falla por cualquier motivo
     *   (credenciales incorrectas, formulario inesperado, fallo de
     *   red).
     * @throws kotlinx.coroutines.CancellationException si la operación
     *   se cancela mientras está en curso.
     */
    suspend fun authenticate(credentials: CasCredentials): CasSession =
        try {
            withContext(Dispatchers.IO) { doAuthenticate(credentials) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AuthException) {
            throw exception
        } catch (exception: Exception) {
            throw AuthException.Network(exception)
        }

    /**
     * Implementación real del proceso de login. No gestiona errores
     * genéricos: deja que [authenticate] se encargue de eso.
     *
     * ## Cómo funciona paso a paso
     *
     * 1. **Prepara el gestor de cookies**. Crea un `CookieManager` que
     *    aceptará todas las cookies que lleguen del servidor. Estas
     *    cookies son la "memoria" de la sesión: sin ellas, el
     *    siguiente paso no sabría que ya nos hemos identificado.
     * 2. **Clona el cliente HTTP**. A partir del [baseClient]
     *    configurado, crea un cliente nuevo que usa ese gestor de
     *    cookies y sigue automáticamente las redirecciones HTTP. Las
     *    redirecciones son clave: el CAS responde con un `302` que
     *    apunta al servicio final, y nosotros queremos que el cliente
     *    las siga para llegar hasta el final del flujo.
     * 3. **Pide el formulario de login**. Hace un `GET` a la URL del
     *    CAS. La respuesta es el HTML del formulario, que incluye el
     *    valor oculto `execution` que hay que reenviar.
     * 4. **Extrae el `execution`**. Con una expresión regular busca el
     *    `<input name="execution" value="...">` del HTML. Si no lo
     *    encuentra, el formulario ha cambiado y no podemos continuar:
     *    lanza [AuthException.NoExecutionToken].
     * 5. **Envía las credenciales**. Hace un `POST` al CAS con el
     *    usuario, la contraseña, el `execution` y un campo fijo
     *    `_eventId=submit` que el CAS espera. El cliente sigue las
     *    redirecciones automáticamente hasta la página final.
     * 6. **Comprueba las cookies**. Inspecciona el gestor de cookies
     *    buscando una que se llame `MoodleSession`. Si está, el login
     *    ha ido bien y podemos devolver la sesión. Si no, o bien las
     *    credenciales eran incorrectas, o bien el flujo del CAS ha
     *    cambiado: lanza [AuthException.NoMoodleSession].
     *
     * @param credentials Credenciales del usuario.
     * @return La [CasSession] con las cookies cargadas.
     * @throws AuthException.NoExecutionToken si no se encuentra el
     *   `execution` en el HTML del formulario.
     * @throws AuthException.LoginFailed si el `POST` devuelve un código
     *   HTTP que no es 2xx.
     * @throws AuthException.NoMoodleSession si el flujo termina pero no
     *   hay cookie de Moodle (credenciales incorrectas u otro
     *   problema).
     */
    private suspend fun doAuthenticate(credentials: CasCredentials): CasSession {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val sessionHttpClient = baseClient.newBuilder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .followRedirects(true)
            .build()

        val loginPageHtml = sessionHttpClient.newCall(
            Request.Builder()
                .url(config.fullLoginUrl)
                .header("User-Agent", config.userAgent)
                .build(),
        ).await().use { loginResponse ->
            loginResponse.body?.string().orEmpty()
        }

        val executionToken = extractExecution(loginPageHtml)
            ?: throw AuthException.NoExecutionToken()

        val loginFormBody = FormBody.Builder()
            .add("username", credentials.user)
            .add("password", credentials.password)
            .add("execution", executionToken)
            .add("_eventId", "submit")
            .build()

        sessionHttpClient.newCall(
            Request.Builder()
                .url(config.fullLoginUrl)
                .header("User-Agent", config.userAgent)
                .post(loginFormBody)
                .build(),
        ).await().use { postResponse ->
            if (!postResponse.isSuccessful) {
                throw AuthException.LoginFailed("HTTP ${postResponse.code}")
            }
        }

        val hasMoodleSessionCookie = cookieManager.cookieStore.cookies
            .any { cookie -> cookie.name.contains("MoodleSession") }
        if (!hasMoodleSessionCookie) {
            throw AuthException.NoMoodleSession()
        }

        return CasSession(credentials.user, cookieManager, sessionHttpClient)
    }

    /**
     * Extrae el valor del campo oculto `execution` del HTML del
     * formulario de login del CAS.
     *
     * ## Qué es el `execution`
     *
     * El CAS, para evitar ataques de tipo CSRF (que alguien envíe
     * credenciales desde otra web en nombre del usuario), incluye en
     * cada formulario un token único e irrepetible. Ese token va en un
     * `<input type="hidden" name="execution" value="...">` y hay que
     * devolverlo en el `POST`. Si no se envía, o se envía uno
     * caducado, el CAS rechaza el login.
     *
     * ## Cómo lo extrae
     *
     * No usamos un parser HTML completo (añadiría una dependencia
     * pesada solo para esto). En su lugar, aplicamos tres expresiones
     * regulares sobre el HTML:
     *
     * 1. **`inputTagRegex`**: encuentra todas las etiquetas
     *    `<input ...>`.
     * 2. **`nameAttributeRegex`**: comprueba cuál de ellas tiene
     *    `name="execution"` (o con comillas simples).
     * 3. **`valueAttributeRegex`**: extrae el `value="..."` de esa
     *    etiqueta.
     *
     * El primer `<input>` cuyo `name` sea `execution` es el que nos
     * interesa. Devolvemos su `value`.
     *
     * ## Por qué puede devolver `null`
     *
     * Si el CAS cambia el nombre del campo, o mete el `execution`
     * dentro de un `<script>` en lugar de un `<input>`, este método
     * devuelve `null` y el llamador lanza un error tipado. Es
     * preferible a devolver una cadena vacía y fallar más tarde con
     * un mensaje confuso.
     *
     * @param loginPageHtml Cuerpo HTML de la página de login del CAS.
     * @return El valor del `execution`, o `null` si no se encuentra.
     */
    private fun extractExecution(loginPageHtml: String): String? {
        val inputTagRegex = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
        val nameAttributeRegex = Regex(
            """name\s*=\s*["']execution["']""",
            RegexOption.IGNORE_CASE,
        )
        val valueAttributeRegex = Regex(
            """value\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE,
        )
        return inputTagRegex.findAll(loginPageHtml)
            .map { inputTagMatch -> inputTagMatch.value }
            .firstOrNull { inputTag -> nameAttributeRegex.containsMatchIn(inputTag) }
            ?.let { executionInputTag ->
                valueAttributeRegex.find(executionInputTag)?.groupValues?.get(1)
            }
    }
}