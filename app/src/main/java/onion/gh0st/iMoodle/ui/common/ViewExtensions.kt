package onion.gh0st.iMoodle.ui.common

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment

/**
 * Hace que una vista (normalmente el `AppBarLayout` de una pantalla)
 * deje espacio para las barras del sistema.
 *
 * ## Por qué hace falta
 *
 * La app dibuja de borde a borde (*edge-to-edge*): el contenido llega
 * hasta debajo de la barra de estado y la de navegación. Para que la
 * cabecera no quede tapada, se le añade como relleno superior la altura
 * de la barra de estado. Este mismo bloque estaba copiado en una docena
 * de pantallas; ahora vive aquí y se usa con
 * `binding.appBar.applySystemBarsInsets()`.
 *
 * El relleno inferior **no** se aplica por defecto: en un `AppBarLayout`
 * superior no hace falta y reservaba una franja del color del fondo entre
 * la barra y el contenido (el alto de la barra de navegación). Si alguna
 * vista lo necesitara de verdad, puede pedirse con
 * `applyBottomInset = true`.
 *
 * @receiver Vista a la que se aplican los márgenes.
 * @param applyBottomInset Si es `true` se añade también el espacio de la
 *   barra de navegación como relleno inferior; si es `false` (por
 *   defecto) se conserva el relleno inferior que ya tuviera la vista.
 */
internal fun View.applySystemBarsInsets(applyBottomInset: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            view.paddingLeft,
            systemBars.top,
            view.paddingRight,
            if (applyBottomInset) systemBars.bottom else view.paddingBottom,
        )
        insets
    }
}

/**
 * Reserva en el relleno inferior de la vista el alto del teclado en
 * pantalla (*IME*).
 *
 * ## Por qué hace falta
 *
 * La app dibuja de borde a borde (`enableEdgeToEdge`). En ese modo el
 * sistema ya **no** redimensiona la ventana al abrir el teclado aunque
 * el manifiesto declare `adjustResize`: la app debe leer el inset
 * `WindowInsetsCompat.Type.ime()` y hacer sitio por su cuenta. Sin esto,
 * el teclado tapa la parte baja del contenido (por ejemplo, el campo de
 * comentarios de una tarea).
 *
 * Se reserva solo la parte del teclado que **de verdad** se solapa con
 * la vista (no siempre su alto completo): si la vista termina por encima
 * del borde inferior de la ventana, reservar todo el inset dejaba una
 * franja vacía entre el contenido y el teclado. Se toma además el mayor
 * entre ese solape y el relleno original, no la suma. Al cerrar el
 * teclado, el relleno vuelve a su valor inicial.
 *
 * ## Cómo se usa
 *
 * ```
 * binding.scrollAssign.applyImeBottomInset { keyboardVisible ->
 *     if (keyboardVisible) scrollJustAboveKeyboard()
 * }
 * ```
 *
 * Conviene desplazar el scroll **solo lo necesario** para dejar el campo
 * visible (no con `fullScroll`), o asomará el relleno inferior del
 * contenido como una franja vacía sobre el teclado.
 *
 * @param onImeVisibilityChanged Se invoca cuando el teclado aparece o
 *   desaparece. Sirve, por ejemplo, para desplazar el scroll hasta el
 *   campo que se está editando.
 */
internal fun View.applyImeBottomInset(onImeVisibilityChanged: (Boolean) -> Unit = {}) {
    val initialBottomPadding = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val imeOverlap = if (imeBottom > 0) view.imeOverlap(imeBottom) else 0
        val newBottomPadding = maxOf(initialBottomPadding, imeOverlap)
        if (view.paddingBottom != newBottomPadding) {
            view.updatePadding(bottom = newBottomPadding)
            onImeVisibilityChanged(imeBottom > 0)
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Calcula cuántos píxeles del teclado quedan por debajo del borde
 * inferior de esta vista.
 *
 * El inset del IME se mide desde el borde de la ventana, pero una vista
 * puede terminar antes (por ejemplo, porque debajo tiene una barra de
 * navegación). Reservar el inset completo en ese caso deja un hueco de
 * más; el solape real es lo que hay que despejar.
 *
 * @param imeBottom Alto del teclado en pantalla.
 * @return El alto del teclado que se solapa con la vista, o el alto
 *   completo si todavía no hay medidas (primer *layout*).
 */
private fun View.imeOverlap(imeBottom: Int): Int {
    val root = rootView
    if (root.height == 0 || height == 0) {
        // Todavía no hay medidas (primer layout): recalcular cuando las
        // haya en vez de dejar reservado el alto completo del teclado.
        doOnLayout { ViewCompat.requestApplyInsets(this) }
        return imeBottom
    }
    val rootLocation = IntArray(2).also { root.getLocationInWindow(it) }
    val viewLocation = IntArray(2).also { getLocationInWindow(it) }
    val keyboardTop = rootLocation[1] + root.height - imeBottom
    val viewBottom = viewLocation[1] + height
    return (viewBottom - keyboardTop).coerceAtLeast(0)
}

/**
 * Muestra un aviso breve a partir de un recurso de texto, con un
 * argumento opcional para los textos con `%1$s`.
 *
 * Sustituye a las copias privadas de `showToast` que había en cada
 * fragmento. El nombre se conserva por compatibilidad con las pantallas
 * existentes; internamente se dibuja un `Snackbar` personalizado.
 *
 * @receiver Fragmento desde el que se muestra el mensaje.
 * @param messageResId Recurso del texto.
 * @param formatArg Valor que sustituye a `%1$s`, si el texto lo usa.
 * @param longDuration Si es `true` permanece más tiempo en pantalla.
 */
internal fun Fragment.showToast(
    @StringRes messageResId: Int,
    formatArg: String? = null,
    longDuration: Boolean = false,
) {
    showCustomToast(messageResId, formatArg, longDuration)
}

/**
 * Resuelve un atributo de color del tema actual a un color concreto.
 *
 * ## Por qué hace falta
 *
 * Las paletas de color se aplican mediante atributos de tema
 * (`?attr/appAccent` y similares). Cuando el código Kotlin necesita el
 * valor entero del color (por ejemplo, para teñir un texto), no puede
 * usar `ContextCompat.getColor(..., R.color.xxx)` porque el recurso de
 * color fijo no cambia con la paleta. Esta función resuelve el atributo
 * contra el tema activo y devuelve el color.
 *
 * @receiver Contexto con el tema aplicado.
 * @param attr Atributo de tema (por ejemplo, `R.attr.appAccent`).
 * @return Color resuelto, o `0` si no se pudo resolver.
 */
fun Context.resolveColorAttr(attr: Int): Int {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(attr, typedValue, true)) {
        if (typedValue.resourceId != 0) {
            try {
                getColor(typedValue.resourceId)
            } catch (exception: Resources.NotFoundException) {
                typedValue.data
            }
        } else {
            typedValue.data
        }
    } else {
        0
    }
}
