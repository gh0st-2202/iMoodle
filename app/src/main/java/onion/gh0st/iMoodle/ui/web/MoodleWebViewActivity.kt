package onion.gh0st.iMoodle.ui.web

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ActivityWebBinding
import onion.gh0st.iMoodle.ui.common.ThemedActivity
import onion.gh0st.iMoodle.ui.common.WebUrls
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
 * Cuando la URL es la pantalla intermedia de un SCORM
 * (`/mod/scorm/view.php`), pulsa automáticamente el botón "Entrar"
 * del formulario de Moodle para ir directo al reproductor. Solo lo
 * hace una vez por apertura, para no atrapar al usuario en un bucle
 * si sale del paquete o vuelve atrás.
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
 * - **Navegaciones**: `shouldOverrideUrlLoading` solo permite el host
 *   de Moodle. Si el usuario pulsa un enlace externo, se abre en
 *   [WebActivity] (WebView genérico) y Atrás vuelve al punto exacto;
 *   las redirecciones automáticas a otros hosts se bloquean.
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

    /** Callback de "atrás", habilitado solo si el `WebView` puede retroceder. */
    private var backCallback: OnBackPressedCallback? = null

    /**
     * Evita volver a auto-entrar en el SCORM si el usuario sale de la
     * actividad o pulsa "atrás" hasta `view.php`. Solo se entra una vez
     * por apertura de pantalla: así puede consultar su nota o el resumen
     * del intento sin que lo metamos de nuevo en el reproductor.
     */
    private var hasAutoEnteredScorm = false

    /** URL inicial validada, necesaria para reintentar tras un redirect al login. */
    private var initialUrl: String? = null

    /** Evita reintentar el login más de una vez por apertura de pantalla. */
    private var hasRetriedAuth = false

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
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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
        initialUrl = url

        WebToolbarHelper.setup(
            activity = this,
            toolbar = binding.toolbarWeb,
            initialUrl = url,
            showUrlSubtitle = false,
        ) { binding.webview.url ?: url }

        setupBackHandler()
        configureWebView()

        lifecycleScope.launch {
            loadAuthenticatedUrl(url)
        }
    }

    /**
     * Renueva la sesión, sincroniza las cookies y carga la URL.
     *
     * Si el refresco falla y además no queda ninguna cookie en memoria,
     * cargar la URL solo conseguiría que Moodle mostrara su pantalla de
     * login dentro de la app. En ese caso se muestra un aviso con un
     * botón de reintento en lugar de ese login.
     *
     * @param url URL validada de Moodle.
     * @param force Fuerza un nuevo login CAS, usado cuando Moodle ha
     *   redirigido a `/login/index.php` aunque las cookies locales
     *   parecieran vigentes.
     */
    private suspend fun loadAuthenticatedUrl(url: String, force: Boolean = false) {
        val refreshed = viewModel.refreshCookies(force = force)
        syncCookiesFromOkHttpToWebView(url)

        if (isFinishing || isDestroyed) return
        val hasCookies = viewModel.currentSession?.cookies?.isNotEmpty() == true
        if (!refreshed && !hasCookies) {
            showAuthError(url)
            return
        }
        hideAuthError()
        binding.webview.loadUrl(url)
    }

    /** Muestra el aviso de sesión no renovada en lugar del WebView. */
    private fun showAuthError(url: String) {
        binding.webview.visibility = View.GONE
        binding.webAuthError.visibility = View.VISIBLE
        binding.webAuthRetry.setOnClickListener {
            binding.webAuthError.visibility = View.GONE
            binding.webview.visibility = View.VISIBLE
            lifecycleScope.launch { loadAuthenticatedUrl(url) }
        }
    }

    /** Vuelve a mostrar el WebView tras un refresco correcto. */
    private fun hideAuthError() {
        binding.webAuthError.visibility = View.GONE
        binding.webview.visibility = View.VISIBLE
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

        // El callback se guarda en la propiedad para que configureWebView()
        // actualice su estado en doUpdateVisitedHistory.
        backCallback = webViewBackCallback
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
        applyWebViewTheme()
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                /*
                 * Moodle muestra primero la pantalla intermedia del
                 * SCORM. La ocultamos mientras se dispara la entrada
                 * automática para no ver un parpadeo de `view.php`.
                 * Si ya se entró una vez, se deja visible: el usuario
                 * puede volver aquí a ver su nota.
                 */
                if (!hasAutoEnteredScorm && url != null && isScormViewUrl(url)) {
                    binding.webview.visibility = View.INVISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null && url != null) {
                    if (!isScormViewUrl(url)) binding.webview.visibility = View.VISIBLE
                    cleanUpMoodleChrome(view)
                    tryAutoEnterScorm(view, url)
                    cleanUpScormPlayer(view, url)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return true
                if (isMoodleLoginUrl(targetUrl) && !hasRetriedAuth) {
                    hasRetriedAuth = true
                    val reloadUrl = initialUrl ?: return true
                    lifecycleScope.launch { loadAuthenticatedUrl(reloadUrl, force = true) }
                    return true
                }
                if (isAllowedMoodleUrl(targetUrl)) return false

                /*
                 * Solo abrimos la Activity web genérica cuando el
                 * usuario ha pulsado el enlace. Así un recurso
                 * embebido o una redirección automática no saca al
                 * usuario del SCORM sin que haya tocado nada. Al
                 * pulsar Atrás se vuelve al punto exacto del SCORM.
                 */
                if (request.hasGesture() && WebUrls.isSafeWebUrl(targetUrl)) {
                    WebLinks.openExternal(this@MoodleWebViewActivity, targetUrl)
                }
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                backCallback?.isEnabled = view?.canGoBack() == true
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
    private suspend fun syncCookiesFromOkHttpToWebView(fallbackUrl: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        enableThirdPartyCookies(cookieManager)

        val sessionCookies = viewModel.currentSession?.cookies.orEmpty()
        if (sessionCookies.isEmpty()) return

        /*
         * Chromium gestiona estas operaciones de forma asíncrona: sin
         * esperar al callback, `loadUrl` puede partir antes de que las
         * cookies estén realmente escritas y Moodle no las reciba.
         */
        @Suppress("DEPRECATION")
        cookieManager.removeExpiredCookie()
        cookieManager.removeSessionCookiesAwait()
        sessionCookies.forEach { cookie ->
            val cookieBaseUrl = cookie.domain
                ?.trimStart('.')
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://$it" }
                ?: fallbackUrl
            cookieManager.setCookieAwait(cookieBaseUrl, cookie.toHeaderString())
        }
        cookieManager.flush()
    }

    /** Versión suspendida de `removeSessionCookies`, espera al callback. */
    private suspend fun CookieManager.removeSessionCookiesAwait(): Boolean =
        suspendCancellableCoroutine { continuation ->
            removeSessionCookies { removed ->
                if (continuation.isActive) continuation.resume(removed)
            }
        }

    /** Versión suspendida de `setCookie`, espera al callback. */
    private suspend fun CookieManager.setCookieAwait(url: String, value: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            setCookie(url, value) { set ->
                if (continuation.isActive) continuation.resume(set)
            }
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
     * Serializa una [WebCookie] al formato de cabecera `Cookie:`.
     *
     * ## Qué incluye
     *
     * - Nombre y valor.
     * - Dominio **solo si es de dominio** (empieza por `.`). Una
     *   cookie *host-only* no debe declarar `Domain`: Chromium la
     *   asociaría al dominio completo y dejaría de enviarla.
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
        if (domain?.startsWith(".") == true) append("; Domain=$domain")
        path?.let { cookiePath -> append("; Path=$cookiePath") }
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        append("; SameSite=Lax")
    }

    /**
     * Entra automáticamente en el reproductor SCORM pulsando el botón
     * del formulario de Moodle.
     *
     * No se construye la URL de `player.php` a mano: Moodle resuelve
     * `scoid`, `currentorg`, `mode`, `newattempt` y `sesskey`, y aplica
     * sus propias restricciones de acceso. Usar el botón respeta además
     * sus validadores y manejadores JS.
     *
     * @param webView WebView donde se cargó `view.php`.
     * @param url URL que acaba de terminar de cargar.
     */
    private fun tryAutoEnterScorm(webView: WebView, url: String) {
        if (hasAutoEnteredScorm || !isScormViewUrl(url)) return

        webView.evaluateJavascript(AUTO_ENTER_SCORM_JS) { result ->
            if (isFinishing || isDestroyed) return@evaluateJavascript
            if (result == "true") {
                hasAutoEnteredScorm = true
                /*
                 * Si el botón no llega a navegar (un JS de Moodle lo
                 * bloquea, por ejemplo), no dejamos la pantalla en
                 * blanco de forma permanente.
                 */
                lifecycleScope.launch {
                    delay(SCORM_AUTO_ENTER_FALLBACK_MS)
                    if (!isFinishing && !isDestroyed &&
                        isScormViewUrl(binding.webview.url.orEmpty())
                    ) {
                        binding.webview.visibility = View.VISIBLE
                    }
                }
            } else {
                // No hay formulario: el SCORM está cerrado, sin intentos
                // o Moodle muestra un aviso que el usuario debe leer.
                binding.webview.visibility = View.VISIBLE
            }
        }
    }

    /** `true` si la URL es la pantalla intermedia de un SCORM. */
    private fun isScormViewUrl(url: String): Boolean {
        val path = runCatching { url.toUri().path?.lowercase() }.getOrNull() ?: return false
        return path.endsWith("/mod/scorm/view.php")
    }

    /** `true` si Moodle ha redirigido a su pantalla de login. */
    private fun isMoodleLoginUrl(url: String): Boolean {
        val path = runCatching { url.toUri().path?.lowercase() }.getOrNull() ?: return false
        return path.endsWith("/login/index.php")
    }

    /**
     * Aplica el modo claro/oscuro antes de cargar la primera página.
     *
     * `ALGORITHMIC_DARKENING` evita el pantallazo blanco al abrir el
     * WebView en modo oscuro; en versiones antiguas se usa el
     * `FORCE_DARK` heredado. `uiMode` se deja fuera de `configChanges`
     * para que un cambio del modo del sistema recree la Activity y
     * vuelva a aplicar el tema.
     */
    private fun applyWebViewTheme() {
        val surfaceColor = MaterialColors.getColor(
            binding.webview,
            com.google.android.material.R.attr.colorSurface,
        )
        binding.webview.setBackgroundColor(surfaceColor)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) -> {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                    binding.webview.settings,
                    isNightMode,
                )
            }
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) -> {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    binding.webview.settings,
                    if (isNightMode) WebSettingsCompat.FORCE_DARK_ON
                    else WebSettingsCompat.FORCE_DARK_OFF,
                )
            }
        }
    }

    /**
     * Oculta elementos globales del "chrome" de Moodle.
     *
     * Se aplica en todas las páginas, no solo en el reproductor: por
     * ejemplo `.usertour` es un bloque del pie de página que Moodle
     * muestra casi siempre y que no aporta nada dentro de la app.
     * Si en el futuro hay que esconder el footer completo o el botón
     * de ayuda, basta con añadir sus selectores al JS.
     *
     * @param webView WebView donde se cargó la página.
     */
    private fun cleanUpMoodleChrome(webView: WebView) {
        webView.evaluateJavascript(CLEANUP_MOODLE_CHROME_JS, null)
    }

    /**
     * Oculta la cabecera y el interruptor de índice del reproductor.
     *
     * Se usa CSS en lugar de quitar los elementos del DOM porque los
     * scripts YUI3 de Moodle los buscan para dimensionar el `iframe` y
     * enganchar eventos: borrarlos provocaría errores `null` y un
     * contenido mal dimensionado.
     *
     * @param webView WebView donde se cargó `player.php`.
     * @param url URL que acaba de terminar de cargar.
     */
    private fun cleanUpScormPlayer(webView: WebView, url: String) {
        if (!isScormPlayerUrl(url)) return
        webView.evaluateJavascript(CLEANUP_SCORM_PLAYER_JS, null)
    }

    /** `true` si la URL es el reproductor de un SCORM. */
    private fun isScormPlayerUrl(url: String): Boolean {
        val path = runCatching { url.toUri().path?.lowercase() }.getOrNull() ?: return false
        return path.endsWith("/mod/scorm/player.php")
    }

    private companion object {

        /** Pulsa el botón "Entrar" del formulario `view.php` de Moodle. */
        const val AUTO_ENTER_SCORM_JS = """
            (function() {
                var form = document.getElementById('scormviewform');
                if (!form) return false;
                var btn = form.querySelector('input[type="submit"], button[type="submit"]');
                if (btn && !btn.disabled) {
                    btn.click();
                    return true;
                }
                return false;
            })();
        """

        /** Plazo para mostrar `view.php` si el click no navegó. */
        const val SCORM_AUTO_ENTER_FALLBACK_MS = 1500L

        /** Oculta los elementos globales de Moodle en cada página. */
        const val CLEANUP_MOODLE_CHROME_JS = """
            (function() {
                if (document.getElementById('imoodle-chrome-cleanup')) return;
                var style = document.createElement('style');
                style.id = 'imoodle-chrome-cleanup';
                style.textContent = '.usertour { display: none !important; }';
                (document.head || document.documentElement).appendChild(style);
            })();
        """

        /**
         * Inyecta estilos en `player.php` para ocultar la cabecera y el
         * botón del índice, y avisa a Moodle de que recalcule el tamaño.
         * El `id` evita duplicar el `<style>` si `onPageFinished` se
         * dispara más de una vez.
         */
        const val CLEANUP_SCORM_PLAYER_JS = """
            (function() {
                if (document.getElementById('imoodle-scorm-cleanup')) return;
                var style = document.createElement('style');
                style.id = 'imoodle-scorm-cleanup';
                style.textContent =
                    '#scormtop, #scorm_toc_toggle, #scorm_toc_toggle_btn { display: none !important; }' +
                    '#scorm_content { width: 100% !important; }';
                (document.head || document.documentElement).appendChild(style);
                window.dispatchEvent(new Event('resize'));
            })();
        """
    }
}