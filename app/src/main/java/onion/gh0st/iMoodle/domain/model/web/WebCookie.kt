package onion.gh0st.iMoodle.domain.model.web

/**
 * Una cookie de sesión en términos de dominio.
 *
 * ## Qué representa
 *
 * Los atributos de una cookie que el `WebView` necesita para
 * reconstruirla: nombre, valor, dominio, ruta y los indicadores
 * `Secure` y `HttpOnly`.
 *
 * ## Por qué es un modelo propio
 *
 * Porque `java.net.HttpCookie` es una clase de la plataforma que el
 * dominio no debe conocer. La capa de datos traduce de una a otra y la
 * UI consume esta versión, sin dependencias de Android ni de OkHttp.
 *
 * ## Sobre `SameSite`
 *
 * No se modela a propósito: `HttpCookie` no lo expone y el `WebView`
 * aplica su política por defecto (`Lax` en Chromium). La UI lo añade
 * al serializar, dejando el criterio a la vista.
 *
 * @property name Nombre de la cookie.
 * @property value Valor de la cookie.
 * @property domain Dominio al que aplica, o `null` si es de sesión
 *   sin dominio explícito.
 * @property path Ruta a la que aplica, o `null`.
 * @property secure `true` si solo debe enviarse por HTTPS.
 * @property httpOnly `true` si no debe ser accesible desde JavaScript.
 */
data class WebCookie(
    val name: String,
    val value: String,
    val domain: String?,
    val path: String?,
    val secure: Boolean,
    val httpOnly: Boolean,
)
