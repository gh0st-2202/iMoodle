package onion.gh0st.iMoodle.ui.web

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.ui.common.WebUrls
import onion.gh0st.iMoodle.ui.common.showCustomToast

/**
 * Ayuda compartida para las toolbars de los navegadores integrados
 * ([WebActivity] y [MoodleWebViewActivity]): subtítulo con la URL actual,
 * icono para abrir en el navegador del sistema y diálogo de confirmación.
 */
internal object WebToolbarHelper {

    /**
     * Configura el subtítulo inicial y el menú de la toolbar.
     *
     * @param activity Activity que aloja la toolbar.
     * @param toolbar Toolbar de la pantalla.
     * @param initialUrl URL inicial que se muestra como subtítulo.
     * @param showUrlSubtitle Si es `false`, no se muestra la URL: se
     *   usa en el WebView de Moodle para que parezca una pantalla más
     *   de la app y no un navegador externo.
     * @param currentUrl Proveedor de la URL actualmente cargada.
     */
    fun setup(
        activity: Activity,
        toolbar: MaterialToolbar,
        initialUrl: String?,
        showUrlSubtitle: Boolean = true,
        currentUrl: () -> String?,
    ) {
        toolbar.subtitle = if (showUrlSubtitle) initialUrl.orEmpty() else null
        toolbar.inflateMenu(R.menu.menu_web_view)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.action_open_in_browser) return@setOnMenuItemClickListener false
            confirmOpenInBrowser(activity, currentUrl())
            true
        }
    }

    /**
     * Actualiza el subtítulo con la URL actualmente cargada.
     */
    fun updateUrl(toolbar: MaterialToolbar, url: String?) {
        toolbar.subtitle = url?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun confirmOpenInBrowser(activity: Activity, url: String?) {
        if (!WebUrls.isSafeWebUrl(url)) {
            activity.showCustomToast(activity.getString(R.string.error_unsafe_url))
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_open_browser_title)
            .setMessage(R.string.dialog_open_browser_message)
            .setPositiveButton(R.string.action_open) { _, _ ->
                openInBrowser(activity, requireNotNull(url))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openInBrowser(activity: Activity, url: String) {
        runCatchingCancellable {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            activity.showCustomToast(activity.getString(R.string.error_no_navigator))
        }
    }
}
