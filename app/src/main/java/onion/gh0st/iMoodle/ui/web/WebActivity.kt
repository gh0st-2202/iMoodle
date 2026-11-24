package onion.gh0st.iMoodle.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.ui.common.ThemedActivity
import onion.gh0st.iMoodle.databinding.ActivityWebBinding
import onion.gh0st.iMoodle.ui.common.WebUrls

/**
 * WebView genérico para abrir enlaces externos dentro de la app.
 *
 * ## Qué hace esta Activity
 *
 * 1. Carga la URL indicada en un `WebView`.
 * 2. Muestra un toolbar con el título, la URL actual como subtítulo
 *    y un botón atrás.
 * 3. Permite abrir el enlace en el navegador del sistema tras una
 *    confirmación.
 * 4. Gestiona el botón atrás: si el WebView puede retroceder, va
 *    atrás; si no, cierra la Activity.
 *
 * ## Seguridad
 *
 * Solo carga URLs `http://` o `https://` ([WebUrls.isSafeWebUrl]); con
 * cualquier otro esquema (`file://`, `javascript:`…) se cierra sin
 * cargar nada. La navegación dentro del WebView se limita también a
 * enlaces web seguros; los demás se bloquean.
 */
class WebActivity : ThemedActivity() {

    /** Binding de la vista. */
    private lateinit var binding: ActivityWebBinding

    /** URL con la que se abrió la pantalla; respaldo si la carga falla. */
    private var initialUrl: String? = null

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

        initialUrl = url
        WebToolbarHelper.setup(
            activity = this,
            toolbar = binding.toolbarWeb,
            initialUrl = url,
        ) { binding.webview.url ?: initialUrl }

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
     * @param url URL inicial a cargar.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) = binding.webview.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                WebToolbarHelper.updateUrl(binding.toolbarWeb, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return true
                return !WebUrls.isSafeWebUrl(targetUrl)
            }

            override fun doUpdateVisitedHistory(
                view: WebView?,
                url: String?,
                isReload: Boolean,
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                backCallback?.isEnabled = view?.canGoBack() == true
            }
        }
        loadUrl(url)
    }

    /**
     * Gestiona el botón "atrás".
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
