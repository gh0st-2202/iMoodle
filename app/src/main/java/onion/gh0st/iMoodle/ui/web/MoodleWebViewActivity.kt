package onion.gh0st.iMoodle.ui.web

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.databinding.ActivityWebBinding
import javax.inject.Inject

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
 * 1. Obtiene las cookies de la sesión activa desde
 *    `MoodleSessionProvider`.
 * 2. Las inyecta en el `CookieManager` del WebView.
 * 3. Carga la URL con las cookies ya presentes.
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
class MoodleWebViewActivity : AppCompatActivity() {

    /**
     * Fuente de la `MoodleSession` viva (con cookies y cliente
     * HTTP).
     */
    @Inject lateinit var moodleSessionProvider: MoodleSessionProvider

    /** Binding de la vista. */
    private lateinit var binding: ActivityWebBinding

    /**
     * Host permitido para navegación en el WebView.
     *
     * ## Por qué es una propiedad computada
     *
     * Porque el `MoodleSession` puede cambiar entre llamadas (por
     * ejemplo, tras un refresco de token), y queremos leer siempre
     * el valor actual, no uno cacheado.
     *
     * ## Si no hay sesión
     *
     * Devuelve `null`, y todas las navegaciones se bloquean
     * (fail-safe).
     */
    private val allowedMoodleHost: String?
        get() = moodleSessionProvider.currentSession()?.api?.config?.moodleHost

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

        val url = intent.getStringExtra("URL") ?: return
        val title = intent.getStringExtra("TITLE") ?: getString(R.string.title_moodle)

        binding.toolbarWeb.title = title
        binding.toolbarWeb.setNavigationOnClickListener { finish() }

        if (!isAllowedMoodleUrl(url)) {
            finish()
            return
        }

        syncCookiesFromOkHttpToWebView(url)
        configureWebView()
        binding.webview.loadUrl(url)
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
            val uri = Uri.parse(url)
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
        val sessionCookies = moodleSessionProvider.currentSession()
            ?.cookieManager?.cookieStore?.cookies ?: return
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
     * @param cookie Cookie de OkHttp.
     * @param fallbackUrl URL de la página para cookies sin dominio.
     */
    private fun setWebViewCookie(
        cookieManager: CookieManager,
        cookie: java.net.HttpCookie,
        fallbackUrl: String,
    ) {
        val cookieBaseUrl = cookie.domain?.let { domain -> "https://$domain" } ?: fallbackUrl
        cookieManager.setCookie(cookieBaseUrl, cookie.toHeaderString())
    }

    /**
     * Serializa una `HttpCookie` al formato de cabecera `Cookie:`.
     *
     * ## Qué incluye
     *
     * - Nombre y valor.
     * - Dominio (si lo tiene).
     * - Path (si lo tiene).
     * - `Secure` (si lo tiene).
     * - `HttpOnly` (si lo tiene).
     *
     * ## Qué NO incluye
     *
     * - **`SameSite`**: limitación de API 28. `HttpCookie` no lo
     *   expone. El WebView aplica su política por defecto.
     *
     * @receiver Cookie de OkHttp.
     * @return Representación en formato cabecera HTTP.
     */
    private fun java.net.HttpCookie.toHeaderString(): String = buildString {
        append("$name=$value")
        domain?.let { cookieDomain -> append("; Domain=$cookieDomain") }
        path?.let { cookiePath -> append("; Path=$cookiePath") }
        if (secure) append("; Secure")
        if (isHttpOnly) append("; HttpOnly")
    }
}