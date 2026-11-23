package onion.gh0st.iMoodle.ui.common

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.R as MaterialR
import com.google.android.material.snackbar.Snackbar
import onion.gh0st.iMoodle.R

/**
 * Muestra un aviso flotante con el estilo de la app.
 *
 * Se usa `Snackbar` en lugar de `Toast`: se dibuja dentro de la pantalla,
 * por lo que respeta el tema (borde y logo del color de la paleta activa)
 * y no está limitado por las restricciones de Android 11+ a Toasts
 * personalizados.
 *
 * ## Detalle de implementación
 *
 * El layout del `Snackbar` se trata como un [ViewGroup] genérico porque
 * su tipo concreto (`SnackbarLayout`) es API restringida de Material. Se
 * retira el fondo y el texto por defecto y se sustituyen por la tarjeta
 * redondeada de `layout_custom_snackbar`, que lleva el logo y usa
 * `?attr/loginAccentPrimary` para el borde.
 *
 * @receiver Vista desde la que se ancla el aviso (normalmente `binding.root`).
 * @param message Texto a mostrar.
 * @param longDuration Si es `true` permanece más tiempo en pantalla.
 */
internal fun View.showCustomToast(message: String, longDuration: Boolean = false) {
    val snackbar = Snackbar.make(
        this,
        "",
        if (longDuration) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT,
    )
    val layout = snackbar.view as ViewGroup

    layout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    layout.setPadding(0, 0, 0, 32)
    layout.findViewById<TextView>(MaterialR.id.snackbar_text)?.visibility = View.GONE

    val customView = LayoutInflater.from(context)
        .inflate(R.layout.layout_custom_snackbar, layout, false)
    customView.findViewById<TextView>(R.id.tvSnackbarMessage).text = message
    customView.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER_HORIZONTAL,
    )
    layout.addView(customView, 0)
    snackbar.show()
}

/**
 * Igual que [showCustomToast], pero para una Activity.
 *
 * @receiver Activity desde la que se muestra el aviso.
 * @param message Texto a mostrar.
 * @param longDuration Si es `true` permanece más tiempo en pantalla.
 */
internal fun Activity.showCustomToast(message: String, longDuration: Boolean = false) =
    findViewById<View>(android.R.id.content).showCustomToast(message, longDuration)

/**
 * Igual que [showCustomToast], pero a partir de un recurso de texto.
 *
 * @receiver Fragmento desde el que se muestra el aviso.
 * @param messageResId Recurso del texto.
 * @param formatArg Valor para `%1$s`, si el texto lo usa.
 * @param longDuration Si es `true` permanece más tiempo en pantalla.
 */
internal fun Fragment.showCustomToast(
    @StringRes messageResId: Int,
    formatArg: String? = null,
    longDuration: Boolean = false,
) {
    val message = if (formatArg != null) {
        getString(messageResId, formatArg)
    } else {
        getString(messageResId)
    }
    view?.showCustomToast(message, longDuration)
}
