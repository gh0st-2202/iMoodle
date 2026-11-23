package onion.gh0st.iMoodle.ui.web

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ActivityWebBinding
import onion.gh0st.iMoodle.ui.common.ThemedActivity
import onion.gh0st.iMoodle.domain.model.web.WebCookie

/**
 * WebView embebido para contenido de Moodle.
 *
 * ## Qué hace
 *
 * Carga contenido de Moodle dentro de la app, **autenticado**. Es
 * decir, el usuario ve sus tareas, foros, vídeos, etc., sin tener
 * que volver a loguearse.
 *
 * ## Cómo consigue la autenticación
 *
 * El WebView de Android y OkHttp (el cliente HTTP que usa la app)
 * tienen **gestores de cookies distintos**. Si no se sincronizan,
 * el WebView cargaría las páginas como anónimo y Moodle lo
 * redirigiría al login.
 *
 * Para evitarlo, esta Activity:
 *
 * 1. Obtiene la sesión web (host y cookies) del
 *    [MoodleWebViewModel], que la recibe del dominio.
 * 2. Inyecta las cookies en el `CookieManager` del WebView.
 * 3. Carga la URL con las cookies ya presentes.
 *
 * ## Dependencias de capa
 *
 * La Activity no conoce `api/`: todo lo que necesita de la sesión
 * (host permitido y cookies) llega como un modelo de dominio a través
 * del ViewModel. La plataforma (`CookieManager`, `WebView`) se queda
 * aquí, que es donde corresponde.
 *
 * ## Seguridad
 *
 * El WebView está configurado para no salir del host de Moodle:
 *
 * - **URL inicial**: se valida contra el host de Moodle.
 * - **Navegaciones**: `shouldOverrideUrlLoading` bloquea cualquier
 *   navegación a otro host.
 * - **Cookies `HttpOnly`**: se replican en el WebView.
 *
 * ## Limitaciones
 *
 * - **`SameSite` no se replica**: `java.net.HttpCookie` no expone
 *   `SameSite` en API 28. El WebView aplica su política por defecto
 *   (típicamente `Lax` en Chromium). Es un compromiso consciente.
 */
@AndroidEntryPoint
class MoodleWebViewActivity : ThemedActivity() {

    /**
     * ViewModel que expone la sesión web como modelo de dominio.
     *
     * ## Por qué aquí y no el repositorio
     *
     * Porque `ui/` solo debe hablar con `domain/`. El ViewModel pide
     * la sesión al caso de uso y la Activity consume
     * [MoodleWebViewModel.currentSession] sin saber que por debajo hay
     * OkHttp.
     */
    private val viewModel: MoodleWebViewModel by viewModels()

    /** Binding de la vista. */
    private lateinit var binding: ActivityWebBinding

    /**
     * Host permitido para navegación en el WebView.
     *
     * ## Por qué es una propiedad computada
     *
     * Porque la sesión puede cambiar entre llamadas (por ejemplo, tras
     * un refresco de token), y queremos leer siempre el valor actual,
     * no uno cacheado.
     *
     * ## Si no hay sesión
     *
     * Devuelve `null`, y todas las navegaciones se bloquean
     * (fail-safe).
     */
    private val allowedMoodleHost: String?
        get() = viewModel.currentSession?.moodleHost

    /**
     * Ciclo de vida de la Activity.
     *
     * ## Qué hace paso a paso
     *
     * 1. Aplica edge-to-edge.
     * 2. Configura el toolbar.
     * 3. Valida la URL inicial contra el host de Moodle. Si no es
     *    válida, cierra la Activity (fail-safe).
     * 4. Sincroniza las cookies de OkHttp al WebView.
     * 5. Configura el WebView.
     * 6. Carga la URL.
     *
     * ## Sobre `@SuppressLint("SetJavaScriptEnabled")`
     *
     * Es necesario porque el contenido de Moodle requiere JS y lint
     * lo marca como riesgo por defecto. Es un riesgo aceptado: el
     * WebView solo carga contenido de Moodle, y las navegaciones
     * externas se bloquean.
     *
     * @param savedInstanceState Estado previo.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        val url = intent.getStringExtra(WebExtras.URL)
        val title = intent.getStringExtra(WebExtras.TITLE) ?: getString(R.string.title_moodle)

        binding.toolbarWeb.title = title
        binding.toolbarWeb.setNavigationOnClickListener { finish() }

        if (url == null || !isAllowedMoodleUrl(url)) {
            finish()
            return
        }

        syncCookiesFromOkHttpToWebView(url)
        configureWebView()
        setupBackHandler()
        binding.webview.loadUrl(url)
    }

    /**
     * Libera el `WebView` al cerrar la pantalla.
     *
     * Un `WebView` conserva una referencia a la Activity que lo creó;
     * si no se destruye explícitamente, ambos quedan en memoria aunque la
     * pantalla ya se haya cerrado.
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
     * Gestiona el botón "atrás": retrocede por el historial del
     * `WebView` mientras pueda y, cuando ya no puede, cierra la pantalla.
     *
     * El callback solo está habilitado si hay historial al que volver;
     * en caso contrario el sistema cierra la Activity con su
     * comportamiento normal. Antes, "atrás" cerraba siempre la pantalla
     * aunque se hubiera navegado por varias páginas de Moodle.
     */
    private fun setupBackHandler() {
        val webViewBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.webview.goBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, webViewBackCallback)

        val previousClient = binding.webview.webViewClient
        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                previousClient.shouldOverrideUrlLoading(view, request)

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                webViewBackCallback.isEnabled = view?.canGoBack() == true
            }
        }
    }

    /**
     * Configura el WebView para contenido Moodle.
     *
     * ## Qué configura
     *
     * - **JavaScript habilitado**: necesario para el player de vídeo
     *   y algunos módulos interactivos.
     * - **DOM Storage habilitado**: necesario para la sesión de
     *   Moodle.
     * - **Viewport ajustado**: para que el contenido sea responsive.
     * - **WebViewClient**: valida cada navegación de nivel superior
     *   contra el host de Moodle.
     *
     * ## Sobre la validación de navegaciones
     *
     * `shouldOverrideUrlLoading` se llama cada vez que el WebView
     * intenta navegar a una nueva URL. Si la URL no pertenece al
     * host de Moodle, se bloquea (devuelve `true`).
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() = binding.webview.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return true
                return !isAllowedMoodleUrl(targetUrl)
            }
        }
    }

    /**
     * Comprueba si una URL es del host de Moodle.
     *
     * ## Qué hace
     *
     * Compara el **authority** completo (host + puerto, si lo hay)
     * contra [allowedMoodleHost]. Usar `authority` en lugar de `host`
     * evita problemas con instalaciones de Moodle que usen puerto
     * explícito (por ejemplo, `host:8443`).
     *
     * ## Qué se permite
     *
     * Solo esquemas `http` y `https`. Cualquier otro (`intent:`,
     * `file:`, `javascript:`, `data:`…) se rechaza.
     *
     * ## Si no hay sesión
     *
     * Se rechaza todo (fail-safe).
     *
     * @param url URL a validar.
     * @return `true` si la URL pertenece al host de Moodle.
     */
    private fun isAllowedMoodleUrl(url: String): Boolean {
        val allowedHost = allowedMoodleHost?.lowercase() ?: return false
        return runCatching {
            val uri = url.toUri()
            val scheme = uri.scheme?.lowercase()
            val authority = uri.authority?.lowercase()
            (scheme == "http" || scheme == "https") && authority == allowedHost
        }.getOrDefault(false)
    }

    /**
     * Copia las cookies de la sesión de OkHttp al `CookieManager`
     * del WebView.
     *
     * ## Por qué
     *
     * OkHttp y WebView usan gestores de cookies distintos. Sin esta
     * sincronización, el WebView cargaría las páginas como anónimo
     * y Moodle lo redirigiría al login.
     *
     * ## Si no hay sesión
     *
     * No inyecta nada. El WebView cargará como anónimo y Moodle
     * mostrará el login.
     *
     * @param fallbackUrl URL de respaldo para las cookies sin
     *   dominio explícito.
     */
    private fun syncCookiesFromOkHttpToWebView(fallbackUrl: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        enableThirdPartyCookies(cookieManager)
        val sessionCookies = viewModel.currentSession?.cookies ?: return
        sessionCookies.forEach { cookie -> setWebViewCookie(cookieManager, cookie, fallbackUrl) }
        cookieManager.flush()
    }

    /**
     * Habilita cookies de terceros para el WebView.
     *
     * ## Por qué
     *
     * Porque el contenido embebido de otros dominios de la Junta
     * (por ejemplo, el reproductor de vídeo) necesita cookies para
     * funcionar con la sesión activa.
     *
     * @param cookieManager `CookieManager` del WebView.
     */
    private fun enableThirdPartyCookies(cookieManager: CookieManager) {
        cookieManager.setAcceptThirdPartyCookies(binding.webview, true)
    }

    /**
     * Inyecta una cookie de OkHttp en el `CookieManager` del
     * WebView.
     *
     * ## Qué hace
     *
     * - Si la cookie tiene dominio, se usa como base
     *   (`https://<domain>`).
     * - Si no, se usa la URL de la página como fallback.
     *
     * @param cookieManager `CookieManager` del WebView.
     * @param cookie Cookie de la sesión, ya como modelo de dominio.
     * @param fallbackUrl URL de la página para cookies sin dominio.
     */
    private fun setWebViewCookie(
        cookieManager: CookieManager,
        cookie: WebCookie,
        fallbackUrl: String,
    ) {
        val cookieBaseUrl = cookie.domain?.let { domain -> "https://$domain" } ?: fallbackUrl
        cookieManager.setCookie(cookieBaseUrl, cookie.toHeaderString())
    }

    /**
     * Serializa una [WebCookie] al formato de cabecera `Cookie:`.
     *
     * ## Qué incluye
     *
     * - Nombre y valor.
     * - Dominio (si lo tiene).
     * - Path (si lo tiene).
     * - `Secure` (si lo tiene).
     * - `HttpOnly` (si lo tiene).
     *
     * - `SameSite=Lax`, siempre.
     *
     * ## Sobre `SameSite`
     *
     * El modelo de dominio no expone este atributo, pero no hace falta:
     * la cadena se construye a mano y se la entrega a `CookieManager`
     * (Chromium), que sí lo interpreta. `Lax` es además el valor que
     * Chromium aplica por defecto a las cookies que no lo indican, así
     * que declararlo explícitamente no cambia el comportamiento y deja
     * la política a la vista. La navegación de esta pantalla es siempre
     * dentro del propio dominio de Moodle, donde `Lax` envía la cookie.
     *
     * @receiver Cookie de la sesión.
     * @return Representación en formato cabecera HTTP.
     */
    private fun WebCookie.toHeaderString(): String = buildString {
        append("$name=$value")
        domain?.let { cookieDomain -> append("; Domain=$cookieDomain") }
        path?.let { cookiePath -> append("; Path=$cookiePath") }
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        append("; SameSite=Lax")
    }
}