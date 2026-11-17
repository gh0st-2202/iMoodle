package onion.gh0st.iMoodle.domain.model.web

/**
 * Sesión web mínima para alimentar un `WebView`.
 *
 * ## Qué contiene
 *
 * Solo lo imprescindible para que una vista web pueda navegar
 * autenticada:
 *
 * - [moodleHost]: el host (con puerto, si lo lleva) al que se permite
 *   navegar.
 * - [cookies]: las cookies de la sesión de Moodle, ya convertidas a un
 *   modelo de dominio.
 *
 * ## Por qué no se expone `MoodleSession`
 *
 * Porque `MoodleSession` es infraestructura pura (cliente HTTP,
 * `CookieStore`, configuración de OkHttp). Exponerla obligaría a que
 * `ui/` importara `api/`, que es justo lo que la arquitectura evita.
 * Este modelo es un dato puro: la capa de datos lo construye a partir
 * de la sesión real y la UI lo consume sin saber de dónde sale.
 *
 * ## Qué NO incluye
 *
 * - **No incluye el token**: el WebView usa cookies, no la API REST.
 * - **No incluye el cliente HTTP**: la vista web no hace peticiones
 *   con OkHttp.
 *
 * @property moodleHost Host de Moodle permitido para navegar.
 * @property cookies Cookies de la sesión activa.
 */
data class WebSession(
    val moodleHost: String,
    val cookies: List<WebCookie>,
)
