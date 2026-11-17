package onion.gh0st.iMoodle.ui.web

import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ActivityWebBinding
import onion.gh0st.iMoodle.ui.common.WebUrls

/**
 * WebView genérico para abrir enlaces externos dentro de la app.
 *
 * ## Qué es un WebView
 *
 * Es un componente de Android que permite mostrar páginas web dentro
 * de la app, como si fuera un mini-navegador. Se usa para cargar
 * contenido externo sin salir de la app.
 *
 * ## Qué hace esta Activity
 *
 * 1. Carga la URL indicada en un `WebView`.
 * 2. Muestra un toolbar con el título y botón atrás.
 * 3. Actualiza el subtítulo del toolbar con la URL cargada.
 * 4. Gestiona el botón atrás: si el WebView puede retroceder, va
 *    atrás; si no, cierra la Activity.
 *
 * ## Nota de seguridad
 *
 * Solo carga URLs `http://` o `https://` ([WebUrls.isSafeWebUrl]); con
 * cualquier otro esquema (`file://`, `javascript:`…) se cierra sin
 * cargar nada. No está exportada, así que solo la propia app puede
 * lanzarla. Sin embargo **no sobrescribe** `shouldOverrideUrlLoading`:
 * una vez cargada la primera página, la navegación continúa por
 * cualquier enlace. Para enlaces no confiables (por ejemplo, de
 * profesores) se prefiere `CourseDetailFragment.openUrl`, que los abre
 * en el navegador del sistema.
 */
class WebActivity : AppCompatActivity() {

    /** Binding de la vista. */
    private lateinit var binding: ActivityWebBinding

    /** Callback de "atrás", habilitado solo si el `WebView` puede retroceder. */
    private var backCallback: OnBackPressedCallback? = null

    /**
     * Ciclo de vida de la Activity.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }

        val url = intent.getStringExtra(WebExtras.URL)
        val title = intent.getStringExtra(WebExtras.TITLE) ?: getString(R.string.title_web_link)

        binding.toolbarWeb.title = title
        binding.toolbarWeb.setNavigationOnClickListener { finish() }

        if (!WebUrls.isSafeWebUrl(url)) {
            finish()
            return
        }

        setupBackHandler()
        setupWebView(requireNotNull(url))
    }

    /**
     * Libera el `WebView` al cerrar la pantalla, para que no mantenga
     * viva la Activity en memoria.
     */
    override fun onDestroy() {
        binding.webview.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    /**
     * Configura el WebView con la URL indicada.
     *
     * ## Qué configura
     *
     * - **JavaScript habilitado**: necesario para muchas webs
     *   modernas.
     * - **DOM Storage habilitado**: necesario para webs que usan
     *   `localStorage` o `sessionStorage`.
     * - **Viewport ajustado**: para que el contenido sea responsive.
     * - **WebViewClient**: actualiza el subtítulo del toolbar con la
     *   URL cargada.
     *
     * ## Qué NO hace
     *
     * No sobrescribe `shouldOverrideUrlLoading`. Cualquier navegación
     * se queda dentro del WebView.
     *
     * @param url URL inicial a cargar.
     */
    private fun setupWebView(url: String) = binding.webview.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.toolbarWeb.subtitle = url
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                backCallback?.isEnabled = view?.canGoBack() == true
            }
        }
        loadUrl(url)
    }

    /**
     * Gestiona el botón "atrás".
     *
     * ## Comportamiento
     *
     * - Mientras el `WebView` tenga historial al que volver, "atrás"
     *   retrocede una página.
     * - Cuando no lo tiene, el callback se deshabilita y el sistema
     *   aplica su comportamiento normal (cerrar la Activity).
     *
     * El estado del callback se actualiza cada vez que cambia el
     * historial ([WebViewClient.doUpdateVisitedHistory]) en lugar de
     * deshabilitarlo de forma permanente la primera vez que no hay
     * historial.
     */
    private fun setupBackHandler() {
        val webViewBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.webview.goBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, webViewBackCallback)
        backCallback = webViewBackCallback
    }
}
