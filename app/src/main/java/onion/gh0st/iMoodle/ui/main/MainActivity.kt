package onion.gh0st.iMoodle.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.DeepLinkExtras
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.update.UpdateDialogHelper
import onion.gh0st.iMoodle.ui.update.UpdateViewModel
import javax.inject.Inject

/**
 * Pantalla principal de la app. Es el "cascarón" que alberga las
 * cuatro pestañas (Cursos, Agenda, Horario y Perfil) y que gestiona
 * la navegación entre ellas y hacia las pantallas de detalle.
 *
 * ## ¿Qué hay en esta pantalla?
 * - En vertical: una barra de navegación **abajo** con cuatro
 *   botones.
 * - En horizontal: una barra de navegación **a la izquierda** (tipo
 *   "rail" o carril).
 * - En el centro: un **panel deslizable** (`ViewPager2`) donde se
 *   ven las pestañas.
 * - Encima, superpuesto: un **contenedor de detalles**
 *   (`detail_container`) donde se abren las pantallas hijas
 *   (detalle de curso, detalle de tarea, detalle de foro…).
 *
 * ## El problema del botón "atrás" (y su solución)
 * El botón "atrás" del sistema debería cerrar la pantalla de detalle
 * y volver a la lista. Pero el `ViewPager2` (que gestiona sus
 * propias pantallas por dentro) a veces "se come" el evento y no
 * llega al gestor de pantallas de la Activity. Resultado: pulsas
 * "atrás", la pantalla de detalle no se cierra, se queda tapando la
 * principal y solo ves la barra inferior (pantalla en blanco).
 *
 * Para arreglarlo, esta Activity registra un **"interceptor"**
 * (`OnBackPressedCallback`) que, cada vez que se pulsa "atrás":
 * 1. Mira si de verdad hay un detalle abierto en el contenedor.
 * 2. Si lo hay, lo cierra.
 * 3. Si no, deja que el sistema cierre la app con normalidad.
 *
 * ## Otras responsabilidades
 * - Pedir permiso de notificaciones (Android 13+).
 * - Abrir la pantalla de tarea si el usuario venía de una
 *   notificación.
 * - Comprobar si hay actualizaciones disponibles.
 * - Redirigir al login si la sesión caduca.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /**
     * Repositorio de sesión, inyectado por Hilt. Se usa para saber
     * si hay sesión activa y para enterarse de cuándo caduca.
     */
    @Inject lateinit var sessionRepo: SessionRepository

    /** ViewModel de la pantalla principal. */
    private val mainViewModel: MainViewModel by viewModels()

    /** ViewModel del actualizador de la app. */
    private val updateViewModel: UpdateViewModel by viewModels()

    /** Panel deslizable que alberga las pestañas. */
    private lateinit var viewPager: ViewPager2

    /** Contenedor donde se muestran las pantallas de detalle. */
    private lateinit var detailContainer: androidx.fragment.app.FragmentContainerView

    /**
     * Recuerda la última versión del diálogo de actualización que ya
     * se ha mostrado, para no enseñar el mismo diálogo dos veces
     * seguidas.
     */
    private var lastShownUpdateVersion: String? = null

    /**
     * "Lanzador" del permiso de notificaciones. Cuando el usuario
     * responde (acepta o deniega), se ejecuta este bloque. Si
     * deniega, se le avisa con un mensaje de que no recibirá
     * notificaciones.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.toast_no_notifications_permission, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Punto de entrada de la pantalla principal.
     *
     * ## Qué hace paso a paso
     * 1. **Comprueba la sesión**: si no hay sesión activa, salta
     *    directamente al login (llevándose la "nota" de la
     *    notificación si la había).
     * 2. Prepara la ventana "edge-to-edge" y carga el diseño.
     * 3. Configura el panel deslizable de las pestañas.
     * 4. Configura la barra de navegación (abajo o izquierda, según
     *    orientación).
     * 5. Configura el "interceptor" del botón "atrás".
     * 6. Ajusta la visibilidad del contenedor de detalles según si
     *    hay algo abierto o no.
     * 7. Se pone a escuchar: caducidad de sesión, eventos de la
     *    pantalla, actualizaciones disponibles.
     * 8. Pide permiso de notificaciones (si hace falta).
     * 9. Pone un "vigilante" que actualiza la visibilidad del
     *    contenedor cuando cambia la pila de pantallas.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepo.session.value == null) {
            redirectToLogin(forwardDeepLink = true)
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.view_pager)
        detailContainer = findViewById(R.id.detail_container)

        setupPager()
        setupNavigation(savedInstanceState)
        setupBackHandler()

        updateDetailVisibility()

        observeSessionEvents()
        requestNotificationPermissionIfNeeded(savedInstanceState)
        mainViewModel.syncRemindersOnce()
        observeMainEvents()

        updateViewModel.check()
        observeUpdates()

        supportFragmentManager.addOnBackStackChangedListener {
            updateDetailVisibility()
        }
    }

    /**
     * Configura el botón "atrás" del sistema.
     *
     * ## Por qué es necesario
     * Como se explica en la cabecera de la clase, el `ViewPager2`
     * puede "robar" el evento del botón "atrás" antes de que llegue
     * al gestor de pantallas de la Activity. Este callback actúa
     * como un **guarda de seguridad**: si hay un detalle abierto, lo
     * cierra él mismo; si no, se "aparta" y deja que el sistema
     * haga su trabajo (cerrar la Activity).
     *
     * ## Diferencia con el botón de la flecha del toolbar
     * La flecha del toolbar funciona bien porque llama directamente
     * a `popBackStack()`. El botón "atrás" del sistema (gesto o
     * botón físico) no siempre lo hacía, de ahí este callback.
     */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val detailFragment = supportFragmentManager
                    .findFragmentById(R.id.detail_container)
                if (detailFragment != null) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Muestra u oculta el contenedor de detalles.
     *
     * ## Importante
     * No se fía de un simple contador (`backStackEntryCount`), sino
     * que comprueba si **realmente** hay una pantalla dentro del
     * contenedor con `findFragmentById`. A veces el contador y la
     * realidad se desincronizan, y fiarse del contador provoca el
     * bug de "pantalla en blanco" que se explica en la cabecera.
     *
     * ## Cómo lo hace
     * 1. Busca un Fragment dentro del contenedor de detalles.
     * 2. Si lo encuentra, el contenedor se muestra (`VISIBLE`).
     * 3. Si no, el contenedor se oculta (`GONE`) y deja ver el panel
     *    deslizable que hay debajo.
     */
    private fun updateDetailVisibility() {
        val detailFragment = supportFragmentManager.findFragmentById(R.id.detail_container)
        val isDetailVisible = detailFragment != null
        detailContainer.visibility = if (isDetailVisible) View.VISIBLE else View.GONE
    }

    /**
     * Configura el panel deslizable (`ViewPager2`) de las pestañas.
     *
     * ## Qué hace paso a paso
     * 1. Le dice qué "páginas" tiene (Cursos, Agenda, Horario y
     *    Perfil) a través del adaptador.
     * 2. **Desactiva el deslizamiento en horizontal**: cuando el
     *    móvil está girado, no tiene sentido deslizar; se usa la
     *    barra lateral para navegar. Además, deslizar en horizontal
     *    choca con otros gestos del sistema.
     * 3. Se pone a escuchar el cambio de pestaña: cada vez que el
     *    usuario cambia de pestaña (deslizando o pulsando un botón),
     *    actualiza los botones de la barra de navegación para que
     *    marquen la pestaña correcta.
     */
    private fun setupPager() {
        viewPager.adapter = MainPagerAdapter(this)

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        viewPager.isUserInputEnabled = !isLandscape

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val menuId = when (position) {
                    0 -> R.id.nav_courses
                    1 -> R.id.nav_calendar
                    2 -> R.id.nav_schedule
                    3 -> R.id.nav_profile
                    else -> R.id.nav_courses
                }
                findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = menuId
                findViewById<NavigationRailView>(R.id.navigation_rail)?.selectedItemId = menuId
            }
        })
    }

    /**
     * Navega a una pestaña concreta según el botón pulsado en la
     * barra de navegación.
     *
     * ## Qué hace
     * 1. Traduce el botón pulsado a un número de página (0, 1, 2 o
     *    3). Si el botón no es ninguno de los cuatro, no hace nada.
     * 2. Si había una pantalla de detalle abierta, la cierra (para
     *    que al cambiar de pestaña se vea la lista, no el detalle
     *    anterior).
     * 3. Cambia la pestaña visible.
     *
     * @param itemId El identificador del botón pulsado.
     * @return `true` si la navegación se ha hecho, `false` si el
     *   botón no era ninguno de los cuatro.
     */
    private fun navigateToItem(itemId: Int): Boolean {
        val position = when (itemId) {
            R.id.nav_courses -> 0
            R.id.nav_calendar -> 1
            R.id.nav_schedule -> 2
            R.id.nav_profile -> 3
            else -> return false
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
        }

        viewPager.currentItem = position
        return true
    }

    /**
     * Abre una pantalla de detalle encima de la principal.
     *
     * ## Cómo funciona
     * 1. Coloca el Fragment indicado dentro del contenedor de
     *    detalles.
     * 2. Lo apunta en la "pila de pantallas" para que el botón
     *    "atrás" sepa que hay algo que cerrar.
     *
     * @param fragment La pantalla de detalle que se quiere mostrar.
     */
    private fun openDetail(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.detail_container, fragment)
            addToBackStack(null)
        }
    }

    /**
     * Se ejecuta cuando el sistema va a "matar" la Activity
     * temporalmente (por ejemplo, al girar la pantalla).
     *
     * ## Qué guarda
     * El número de la pestaña que estaba visible, para poder
     * restaurarla cuando se vuelva a crear la pantalla.
     *
     * @param outState El "sobre" donde se guarda el estado.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_SECTION, viewPager.currentItem)
    }

    /**
     * Se ejecuta cuando llega un nuevo `Intent` a una Activity que
     * ya está abierta.
     *
     * ## Cuándo pasa
     * Por ejemplo, si pulsas una notificación y la app ya estaba
     * abierta en segundo plano. En vez de abrir otra pantalla
     * encima, Android reutiliza la que hay y avisa aquí.
     *
     * ## Qué hace
     * Procesa el nuevo `Intent` para ver si lleva la "nota" de una
     * notificación y, si la lleva, abre la tarea correspondiente.
     *
     * @param intent El nuevo `Intent` que llega.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Se pone a escuchar los eventos de la sesión.
     *
     * ## Qué hace
     * Si la sesión caduca (porque el token ha dejado de ser
     * válido), redirige al login. En este caso **no** hace falta
     * reenviar la "nota" de notificación, porque el usuario está
     * dentro de la app y no venía de una notificación.
     */
    private fun observeSessionEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionRepo.events.collect { event ->
                    if (event is SessionEvent.Expired) {
                        redirectToLogin(forwardDeepLink = false)
                    }
                }
            }
        }
    }

    /**
     * Pide permiso para enviar notificaciones (solo en Android 13
     * o superior).
     *
     * ## Cuándo NO pide permiso
     * - Si la pantalla se está recreando (por ejemplo, tras girar
     *   el móvil), porque el permiso ya se pidió antes.
     * - Si el móvil tiene una versión de Android anterior a la 13,
     *   porque en esas versiones el permiso no existe.
     * - Si el permiso ya está concedido.
     *
     * @param savedInstanceState El estado guardado al recrear la
     *   pantalla. Si no es nulo, significa que la pantalla se está
     *   recreando.
     */
    private fun requestNotificationPermissionIfNeeded(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Se pone a escuchar los eventos de la pantalla principal.
     *
     * ## Qué eventos hay
     * - **`NeedExactAlarmPermission`**: el sistema necesita permiso
     *   para programar alarmas exactas (Android 12+). La pantalla
     *   abre los ajustes para que el usuario lo conceda.
     */
    private fun observeMainEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.events.collect { event ->
                    when (event) {
                        MainUiEvent.NeedExactAlarmPermission -> openExactAlarmSettings()
                    }
                }
            }
        }
    }

    /**
     * Se pone a escuchar al actualizador de la app.
     *
     * ## Qué hace
     * Cuando el actualizador detecta una versión nueva, muestra un
     * diálogo con la información del Release de GitHub. Para no
     * enseñar el mismo diálogo varias veces seguidas, compara la
     * versión con la última que ya se mostró.
     */
    private fun observeUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateViewModel.updateAvailable.collect { update ->
                    if (update != null && update.versionName != lastShownUpdateVersion) {
                        lastShownUpdateVersion = update.versionName
                        UpdateDialogHelper.showUpdateDialog(
                            context = this@MainActivity,
                            update = update,
                            coroutineScope = lifecycleScope,
                        )
                    }
                }
            }
        }
    }

    /**
     * Abre los ajustes del sistema para permitir alarmas exactas.
     *
     * ## ¿Por qué hace falta?
     * A partir de Android 12, las alarmas "exactas" (que suenan a
     * una hora concreta, sin margen) requieren un permiso especial.
     * Este permiso no se pide como los demás: hay que llevar al
     * usuario a una pantalla de ajustes del sistema para que lo
     * active él mismo.
     *
     * ## Si algo falla
     * Si el móvil no tiene esa pantalla de ajustes (porque el
     * fabricante la ha quitado), se muestra un toast explicando
     * cómo llegar manualmente.
     */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(settingsIntent) }
            .onFailure {
                Toast.makeText(this, R.string.toast_exact_alarm_settings, Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Configura la barra de navegación (abajo o izquierda, según la
     * orientación del móvil) y decide a qué pantalla ir al arrancar.
     *
     * ## Qué hace paso a paso
     * 1. Mira si el móvil está en horizontal.
     * 2. **En horizontal**: configura la barra lateral, ajusta los
     *    márgenes para que no quede tapada por la barra de estado,
     *    restaura la pestaña que estaba visible (o procesa el
     *    `Intent` inicial si es la primera vez) y conecta los
     *    botones con `navigateToItem`.
     * 3. **En vertical**: hace lo mismo, pero con la barra inferior
     *    y los márgenes correspondientes.
     *
     * @param savedInstanceState El estado guardado al recrear la
     *   pantalla (por ejemplo, al girar el móvil). Si no es nulo,
     *   se restaura la pestaña que estaba visible.
     */
    private fun setupNavigation(savedInstanceState: Bundle?) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            val navigationRail = findViewById<NavigationRailView>(R.id.navigation_rail)
            ViewCompat.setOnApplyWindowInsetsListener(navigationRail) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    view.paddingLeft + systemBars.left,
                    systemBars.top,
                    view.paddingRight,
                    systemBars.bottom,
                )
                insets
            }

            if (savedInstanceState != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(STATE_CURRENT_SECTION, 0), false)
            } else {
                handleIntent(getIntent())
            }
            navigationRail.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        } else {
            val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_nav)
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    systemBars.bottom,
                )
                insets
            }

            if (savedInstanceState != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(STATE_CURRENT_SECTION, 0), false)
            } else {
                handleIntent(getIntent())
            }
            bottomNavigation.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        }
    }

    /**
     * Procesa el `Intent` con el que se ha abierto la pantalla.
     *
     * ## Qué busca
     * La "nota" de deep-link (ver `DeepLinkExtras`). Si el `Intent`
     * lleva un número de curso y un número de tarea válidos, abre
     * directamente el detalle de esa tarea.
     *
     * @param incomingIntent El `Intent` que abrió la pantalla. Puede
     *   ser nulo.
     */
    private fun handleIntent(incomingIntent: Intent?) {
        val courseId = incomingIntent?.getIntExtra(DeepLinkExtras.COURSE_ID, 0) ?: 0
        val instanceId = incomingIntent?.getIntExtra(DeepLinkExtras.INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            openAssignmentDetail(courseId, instanceId)
        }
    }

    /**
     * Abre la pantalla de detalle de una tarea concreta.
     *
     * @param courseId El número del curso al que pertenece la tarea.
     * @param instanceId El número de la tarea dentro del curso.
     */
    private fun openAssignmentDetail(courseId: Int, instanceId: Int) {
        openDetail(AssignmentDetailFragment.newInstance(courseId, instanceId))
    }

    /**
     * Redirige al usuario a la pantalla de login.
     *
     * ## Cuándo se llama
     * - Al arrancar la pantalla principal, si no hay sesión activa.
     * - Cuando la sesión caduca durante el uso de la app.
     *
     * ## Sobre la "nota" de notificación
     * Si el usuario venía de una notificación, hay que llevarse la
     * "nota" de deep-link al login (`forwardDeepLink = true`) para
     * que, después de identificarse, se abra la tarea correcta.
     *
     * @param forwardDeepLink Si es `true`, copia los extras de
     *   deep-link al `Intent` del login.
     */
    private fun redirectToLogin(forwardDeepLink: Boolean) {
        val loginIntent = Intent(this, onion.gh0st.iMoodle.ui.login.LoginActivity::class.java)
        if (forwardDeepLink) {
            DeepLinkExtras.copy(
                from = getIntent(),
                destination = loginIntent,
            )
        }
        startActivity(loginIntent)
        finish()
    }

    /**
     * Constantes internas de la clase.
     *
     * ## ¿Qué contiene?
     * - **`STATE_CURRENT_SECTION`**: la etiqueta con la que se guarda
     *   el número de la pestaña visible en el "sobre" del estado de
     *   la Activity, para poder restaurarla al recrear la pantalla.
     */
    private companion object {
        const val STATE_CURRENT_SECTION = "STATE_CURRENT_SECTION"
    }
}