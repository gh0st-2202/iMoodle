package onion.gh0st.iMoodle.api.cas

import okhttp3.OkHttpClient
import java.net.CookieManager

/**
 * Resultado de un login exitoso contra el CAS.
 *
 * ## Qué contiene
 *
 * Es la "sesión viva" del usuario con el CAS: las cookies que el
 * servidor ha emitido al identificarse correctamente, más el cliente
 * HTTP que las lleva enganchadas para que las peticiones posteriores
 * se hagan como ese usuario.
 *
 * ## Qué NO contiene
 *
 * No contiene el `wstoken` de Moodle. Conseguir ese token es un paso
 * posterior (lo hace `MoodleTokenFetcher`), porque el CAS y Moodle son
 * dos servicios distintos: el CAS solo dice "este usuario es quien
 * dice ser", pero para hacer peticiones a la API REST de Moodle hace
 * falta además un token específico de Moodle. Esa separación en dos
 * pasos es la que permite reutilizar el login CAS para otros
 * servicios de la Junta (iPasen, secretaría virtual, etc.).
 *
 * ## Por qué expone el `cookieManager`
 *
 * El cliente HTTP ya lleva el gestor de cookies enganchado, así que en
 * teoría nadie necesitaría acceder al gestor directamente. Lo
 * exponemos por dos motivos:
 *
 * 1. **WebView**: para inyectar las cookies de la sesión en el
 *    `CookieManager` del `WebView` cuando el usuario abre contenido
 *    de Moodle dentro de la app. Sin esto, el WebView cargaría las
 *    páginas como anónimo.
 * 2. **Depuración**: en caso de problemas, permite inspeccionar qué
 *    cookies ha recibido la sesión.
 *
 * @property user Nombre de usuario con el que se ha hecho login.
 * @property cookieManager Gestor de cookies de la sesión. Contiene la
 *   cookie `MoodleSession` (y otras que el CAS haya podido emitir)
 *   para que las peticiones posteriores se hagan autenticadas.
 * @property client Cliente HTTP que lleva enganchado el
 *   [cookieManager] y sigue redirecciones automáticamente. Es el que
 *   hay que usar para todas las peticiones autenticadas de esta
 *   sesión.
 */
data class CasSession(
    val user: String,
    val cookieManager: CookieManager,
    val client: OkHttpClient,
)