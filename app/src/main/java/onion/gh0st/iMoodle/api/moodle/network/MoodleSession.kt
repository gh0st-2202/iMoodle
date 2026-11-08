package onion.gh0st.iMoodle.api.moodle.network

import okhttp3.OkHttpClient
import java.net.CookieManager

/**
 * Representación **viva** de una sesión autenticada con Moodle.
 *
 * ## Qué es una sesión "viva"
 *
 * En el dominio de la app, una sesión se representa con un
 * `SessionContext` (datos puros: provincia, usuario, token). Pero
 * para hacer peticiones reales a Moodle hace falta más: un cliente
 * HTTP configurado, un gestor de cookies con las cookies del CAS ya
 * cargadas, y un cliente REST listo para hablar con la API.
 *
 * Ese conjunto es lo que representa esta clase: el "estado vivo" de
 * la sesión.
 *
 * ## Qué contiene
 *
 * - El **usuario** de Moodle.
 * - El **gestor de cookies** de la sesión (con las cookies del CAS).
 * - El **cliente HTTP** con esas cookies enganchadas.
 * - El **cliente REST** de Moodle (`MoodleApiClient`).
 *
 * ## Por qué no se expone a `domain`
 *
 * Porque contiene infraestructura pura (cliente HTTP, cookies). El
 * dominio solo debe conocer conceptos de negocio. Por eso esta clase
 * vive en `api/` y se expone solo a través de `MoodleSessionProvider`.
 *
 * ## Cómo se construye
 *
 * No se construye a mano: la construye `MoodleSessionFactory` a
 * partir de un `SessionContext` puro. Esa fábrica es la que se
 * encarga de crear el gestor de cookies, clonar el cliente HTTP y
 * cablear el `MoodleApiClient`.
 *
 * @property user Nombre de usuario de Moodle. Viene de la `CasSession`
 *   original.
 * @property cookieManager Gestor de cookies de la sesión. Contiene las
 *   cookies del CAS (incluida la `MoodleSession`, que es la que
 *   demuestra que el usuario está autenticado). Se expone para poder
 *   inyectarlas en el `WebView` cuando el usuario abre contenido de
 *   Moodle dentro de la app.
 * @property client Cliente HTTP con las cookies ya enganchadas. Se
 *   usa para peticiones que no son de la API REST (por ejemplo, para
 *   descargar archivos con `DownloadManager`, o para el WebView).
 * @property api Cliente REST de Moodle (`MoodleApiClient`), que añade
 *   el `wstoken` a cada petición y lo refresca de forma transparente
 *   cuando caduca.
 */
data class MoodleSession(
    val user: String,
    val cookieManager: CookieManager,
    val client: OkHttpClient,
    val api: MoodleApiClient,
) {

    /**
     * Token actual de la API REST de Moodle.
     *
     * ## Qué es
     *
     * Es un atajo a `api.token.value`. Existe porque hay sitios
     * (descarga de archivos, inyección de cookies en el WebView) que
     * solo necesitan el token, sin el resto del `MoodleApiClient`.
     *
     * ## Por qué es `get()` y no una propiedad guardada
     *
     * Porque el token puede cambiar durante la vida de la sesión
     * (cuando se refresca). Si lo guardáramos como propiedad, nos
     * quedaríamos con el token viejo. Al hacerlo `get()`, siempre
     * devuelve el token actual del `MoodleApiClient`.
     */
    val wstoken: String get() = api.token.value
}