package onion.gh0st.iMoodle.ui.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.net.toUri
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Defaults
import onion.gh0st.iMoodle.ui.common.WebUrls
import onion.gh0st.iMoodle.ui.common.showCustomToast

/**
 * Punto de entrada centralizado para abrir cualquier enlace que aparezca
 * dentro de la app (módulos URL de un curso, enlaces de descripciones,
 * páginas, libros, wikis, foros, glosario, etc.).
 *
 * ## Qué decide
 *
 * - Si la URL pertenece al host de Moodle, se abre en
 *   [MoodleWebViewActivity] (autenticado, dentro de la app).
 * - En caso contrario, se abre en [WebActivity] (WebView genérico).
 *   También se puede forzar ese destino con [openExternal], que no
 *   consulta el host de Moodle.
 *
 * Solo se aceptan URLs web (`http`/`https`); cualquier otro esquema se
 * rechaza con un aviso.
 *
 * ## Uso
 *
 * - Desde código: `WebLinks.open(context, url, title)`.
 * - Desde un `TextView` con enlaces: `movementMethod = WebLinks.movementMethod`.
 */
object WebLinks {

    /**
     * `LinkMovementMethod` que intercepta los clics en enlaces y los
     * enruta a través de [open] en lugar de lanzar un `Intent.ACTION_VIEW`
     * al navegador del sistema.
     */
    val movementMethod: LinkMovementMethod = InternalWebLinkMovementMethod()

    /**
     * Abre un enlace dentro de la app.
     *
     * @param context Contexto desde el que se lanza la Activity.
     * @param url URL del enlace. Puede ser `null` o en blanco.
     * @param title Título opcional para la barra superior.
     */
    fun open(context: Context, url: String?, title: String? = null) {
        val target = url?.trim().orEmpty()
        if (!WebUrls.isSafeWebUrl(target)) {
            notifyUnsafe(context)
            return
        }
        val isMoodle = isMoodleUrl(target)
        val intent = Intent(
            context,
            if (isMoodle) MoodleWebViewActivity::class.java else WebActivity::class.java,
        ).apply {
            putExtra(WebExtras.URL, target)
            putExtra(
                WebExtras.TITLE,
                title?.takeIf { it.isNotBlank() }
                    ?: context.getString(
                        if (isMoodle) R.string.title_moodle else R.string.title_web_link,
                    ),
            )
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Abre un enlace siempre en [WebActivity], sin pasar por la
     * comparación con el host de Moodle de [open].
     *
     * ## Por qué un método separado
     *
     * [open] decide según [Defaults.MOODLE_HOST] y
     * [MoodleWebViewActivity] valida según su propio `allowedMoodleHost`.
     * Si ambos valores llegaran a diferir, enrutar una navegación
     * externa desde el WebView de Moodle con [open] podría reabrir el
     * mismo WebView y crear un bucle. Forzar [WebActivity] evita esa
     * posibilidad.
     *
     * ## Seguridad
     *
     * Las cookies se aíslan por dominio en Chromium, así que una
     * página externa no recibe las cookies de la sesión de Moodle.
     * Solo se aceptan URLs `http`/`https`.
     *
     * @param context Contexto desde el que se lanza la Activity.
     * @param url URL del enlace. Puede ser `null` o en blanco.
     * @param title Título opcional para la barra superior.
     */
    fun openExternal(context: Context, url: String?, title: String? = null) {
        val target = url?.trim().orEmpty()
        if (!WebUrls.isSafeWebUrl(target)) {
            notifyUnsafe(context)
            return
        }
        val intent = Intent(context, WebActivity::class.java).apply {
            putExtra(WebExtras.URL, target)
            putExtra(
                WebExtras.TITLE,
                title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.title_web_link),
            )
        }
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Comprueba si una URL pertenece al host de Moodle.
     *
     * Usa el host fijo de la Junta ([Defaults.MOODLE_HOST]); en
     * producción es el mismo host contra el que valida
     * [MoodleWebViewActivity].
     *
     * @param url URL a comprobar.
     * @return `true` si el host coincide.
     */
    internal fun isMoodleUrl(url: String): Boolean {
        val host = runCatching { url.toUri().host }.getOrNull() ?: return false
        return host.equals(Defaults.MOODLE_HOST, ignoreCase = true)
    }

    private fun notifyUnsafe(context: Context) {
        if (context is Activity) {
            context.showCustomToast(context.getString(R.string.error_unsafe_url))
        } else {
            android.widget.Toast.makeText(
                context,
                R.string.error_unsafe_url,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/**
 * `LinkMovementMethod` que replica el comportamiento del original (selección
 * al pulsar, scroll) pero, al soltar el dedo sobre un enlace, lo enruta
 * a través de [WebLinks.open] en lugar de lanzar el navegador del sistema.
 *
 * Los enlaces con esquemas no web habituales (`mailto:`, `tel:`) se
 * delegan al `URLSpan` original para preservar su comportamiento.
 */
private class InternalWebLinkMovementMethod : LinkMovementMethod() {

    override fun onTouchEvent(
        widget: TextView,
        buffer: Spannable,
        event: MotionEvent,
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
            val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
            val layout = widget.layout
            if (layout != null) {
                val line = layout.getLineForVertical(y.coerceIn(0, layout.height - 1))
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                val links = buffer.getSpans(offset, offset, ClickableSpan::class.java)
                if (links.isNotEmpty()) {
                    val link = links[0]
                    if (link is URLSpan) {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            val url = link.url
                            if (WebUrls.isSafeWebUrl(url)) {
                                WebLinks.open(widget.context, url)
                            } else if (isExternalScheme(url)) {
                                link.onClick(widget)
                            }
                        } else {
                            Selection.setSelection(
                                buffer,
                                buffer.getSpanStart(link),
                                buffer.getSpanEnd(link),
                            )
                        }
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun isExternalScheme(url: String): Boolean {
        val scheme = runCatching { url.toUri().scheme }.getOrNull()?.lowercase()
        return scheme == "mailto" || scheme == "tel"
    }
}
