package onion.gh0st.iMoodle.ui.web

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ActivityWebBinding

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
 * Esta Activity **no valida** la URL recibida y **no sobrescribe**
 * `shouldOverrideUrlLoading`. Para URLs no confiables (por ejemplo,
 * enlaces de profesores), usar `CourseDetailFragment.openUrl`, que
 * sí aplica `WebUrls.isSafeWebUrl` antes de invocar `ACTION_VIEW`.
 */
class WebActivity : AppCompatActivity() {

    /** Binding de la vista. */
    private lateinit var binding: ActivityWebBinding

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

        val url = intent.getStringExtra("URL")
        val title = intent.getStringExtra("TITLE") ?: getString(R.string.title_web_link)

        binding.toolbarWeb.title = title
        binding.toolbarWeb.setNavigationOnClickListener { finish() }

        if (url.isNullOrBlank()) {
            finish()
            return
        }

        setupWebView(url)
        setupBackHandler()
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
        }
        loadUrl(url)
    }

    /**
     * Gestiona el botón atrás.
     *
     * ## Comportamiento
     *
     * - Si el WebView puede retroceder, va atrás.
     * - Si no, deshabilita el callback y llama al dispatcher del
     *   sistema (que cierra la Activity).
     *
     * ## Por qué se deshabilita el callback
     *
     * Para no interceptar el siguiente "atrás". Si no se
     * deshabilitara, el usuario tendría que pulsar "atrás" dos
     * veces: una para que el callback se deshabilite, y otra para
     * que el sistema cierre la Activity.
     */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}