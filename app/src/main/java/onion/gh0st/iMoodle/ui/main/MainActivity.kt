package onion.gh0st.iMoodle.ui.main

import android.Manifest
import android.app.AlarmManager
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.DeepLinkExtras
import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.login.LoginActivity
import onion.gh0st.iMoodle.ui.update.UpdateDialogHelper
import onion.gh0st.iMoodle.ui.update.UpdateEvent
import onion.gh0st.iMoodle.ui.update.UpdateViewModel

/**
 * Pantalla principal de la app: contiene las cuatro secciones (Cursos,
 * Agenda, Horario y Perfil) y aloja las pantallas de detalle.
 *
 * ## Estructura
 *
 * - Un [ViewPager2] con las cuatro secciones, entre las que se puede
 *   navegar **deslizando** (solo en vertical) o con la barra de
 *   navegación: inferior en vertical ([R.id.bottom_nav]) y lateral en
 *   horizontal ([R.id.navigation_rail]). Ambas son [NavigationBarView],
 *   así que se manejan con el mismo código.
 * - Un contenedor de detalle ([R.id.detail_container]) que se superpone al
 *   pager cuando hay una pantalla de detalle abierta (curso, tarea, foro…),
 *   gestionado con el *back stack* del `FragmentManager`.
 *
 * ## Botón "atrás"
 *
 * [detailBackCallback] está **habilitado solo mientras hay un detalle
 * abierto**; entonces "atrás" cierra el detalle. En cualquier otro caso
 * está deshabilitado y el sistema aplica su comportamiento normal (salir
 * de la app). Mantener el callback sincronizado con el estado, en lugar de
 * deshabilitarlo "para siempre" la primera vez, permite que "atrás" siga
 * funcionando tras abrir otro detalle, y que las animaciones de *atrás
 * predictivo* de Android sepan si la app va a manejar el gesto.
 *
 * ## Qué más gestiona
 *
 * - Redirige al login si no hay sesión o si caduca.
 * - Abre directamente el detalle de una tarea cuando se llega desde una
 *   notificación ([handleIntent]).
 * - Pide el permiso de notificaciones y programa los recordatorios.
 * - Muestra el aviso de nueva versión y lanza el instalador cuando la
 *   descarga termina (ver [UpdateViewModel]).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var detailContainer: FragmentContainerView
    private lateinit var navigationView: NavigationBarView

    /** Diálogo de actualización visible, para cerrarlo si la Activity se destruye. */
    private var updateDialog: Dialog? = null

    /**
     * `true` cuando ya se ha lanzado la redirección al login.
     *
     * Evita abrir dos `LoginActivity` si coinciden, por ejemplo, el
     * evento `Expired` y la observación de que la sesión ha pasado a
     * `null` (ambos ocurren durante una invalidación).
     */
    private var isRedirectingToLogin = false

    /**
     * Identificadores del menú de navegación, en el mismo orden que las
     * páginas del [ViewPager2] (la posición en el array es la página).
     */
    private val sectionMenuIds = intArrayOf(
        R.id.nav_courses,
        R.id.nav_calendar,
        R.id.nav_schedule,
        R.id.nav_profile,
    )

    /** Cierra el detalle abierto al pulsar "atrás". Solo está habilitado si hay un detalle. */
    private val detailBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            supportFragmentManager.popBackStack()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.toast_no_notifications_permission, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Lanza los ajustes de alarmas exactas y reprograma al volver.
     *
     * ## Por qué es un `ActivityResultLauncher`
     *
     * Porque el permiso se concede en otra pantalla del sistema. Al
     * volver hay que reprogramar las alarmas que
     * `DefaultNotificationScheduler` no pudo crear mientras faltaba el
     * permiso; si no, los recordatorios no se activarían hasta el
     * siguiente arranque del proceso.
     *
     * Se llama con `promptIfDenied = false`: si el usuario vuelve sin
     * conceder el permiso, no se reabren los ajustes en bucle.
     */
    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        mainViewModel.rescheduleReminders(promptIfDenied = false)
    }

    /**
     * Prepara la pantalla, o redirige al login si no hay sesión.
     *
     * @param savedInstanceState Estado guardado (la sección actual) tras
     *   una rotación, o `null` en un arranque nuevo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!mainViewModel.isSessionActive.value) {
            redirectToLogin(forwardDeepLink = true)
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.view_pager)
        detailContainer = findViewById(R.id.detail_container)

        onBackPressedDispatcher.addCallback(this, detailBackCallback)
        supportFragmentManager.addOnBackStackChangedListener { updateDetailState() }

        setupPager()
        setupNavigation(savedInstanceState)
        updateDetailState()

        observeSessionEvents()
        requestNotificationPermissionIfNeeded(savedInstanceState)
        mainViewModel.syncRemindersOnce()
        observeMainEvents()

        updateViewModel.check()
        observeUpdates()
    }

    /** Cierra el diálogo de actualización para no filtrar su ventana. */
    override fun onDestroy() {
        updateDialog?.dismiss()
        updateDialog = null
        super.onDestroy()
    }

    /**
     * Guarda la sección visible para restaurarla tras una rotación.
     *
     * @param outState Contenedor del estado a guardar.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::viewPager.isInitialized) {
            outState.putInt(STATE_CURRENT_SECTION, viewPager.currentItem)
        }
    }

    /**
     * Atiende un `Intent` recibido con la Activity ya abierta (por ejemplo,
     * al tocar una notificación de tarea).
     *
     * @param intent Nuevo `Intent`.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Sincroniza la visibilidad del contenedor de detalle y el estado del
     * callback de "atrás" con la existencia de una pantalla de detalle.
     *
     * Se decide con `findFragmentById` y no con `backStackEntryCount`:
     * el contador cambia antes de que el fragmento termine de añadirse o
     * quitarse, lo que dejaba la pantalla en blanco.
     */
    private fun updateDetailState() {
        val isDetailVisible = supportFragmentManager.findFragmentById(R.id.detail_container) != null
        detailContainer.visibility = if (isDetailVisible) View.VISIBLE else View.GONE
        detailBackCallback.isEnabled = isDetailVisible
    }

    /**
     * Configura el [ViewPager2]: adaptador, deslizamiento (desactivado en
     * horizontal) y sincronización de la pestaña seleccionada con la página.
     */
    private fun setupPager() {
        viewPager.adapter = MainPagerAdapter(this)
        viewPager.isUserInputEnabled = !isLandscape()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (::navigationView.isInitialized) {
                    navigationView.selectedItemId = sectionMenuIds.getOrElse(position) { R.id.nav_courses }
                }
            }
        })
    }

    /**
     * Configura la barra de navegación (inferior en vertical, lateral en
     * horizontal), sus márgenes del sistema y la sección inicial.
     *
     * @param savedInstanceState Estado guardado, o `null` en un arranque nuevo.
     */
    private fun setupNavigation(savedInstanceState: Bundle?) {
        navigationView = if (isLandscape()) {
            findViewById(R.id.navigation_rail)
        } else {
            findViewById(R.id.bottom_nav)
        }
        applyNavigationInsets(navigationView, isLandscape())

        navigationView.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        navigationView.setOnItemReselectedListener { closeAllDetails() }

        if (savedInstanceState != null) {
            viewPager.setCurrentItem(savedInstanceState.getInt(STATE_CURRENT_SECTION, 0), false)
        } else {
            handleIntent(intent)
        }
    }

    /**
     * Añade a la barra de navegación el espacio que ocupan las barras del
     * sistema (modo de borde a borde).
     *
     * El relleno lateral se calcula a partir del valor **original** de la
     * vista, capturado una sola vez. Sumarlo al valor actual en cada
     * aplicación de insets (que Android repite, por ejemplo, al abrir el
     * teclado o el panel de notificaciones) iría ensanchando la barra.
     *
     * @param navigationBar Barra a la que aplicar los márgenes.
     * @param isRail `true` para el rail lateral (horizontal), `false` para
     *   la barra inferior (vertical).
     */
    private fun applyNavigationInsets(navigationBar: NavigationBarView, isRail: Boolean) {
        val initialPaddingLeft = navigationBar.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(navigationBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (isRail) {
                view.setPadding(
                    initialPaddingLeft + systemBars.left,
                    systemBars.top,
                    view.paddingRight,
                    systemBars.bottom,
                )
            } else {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            }
            insets
        }
    }

    /**
     * Cambia a la sección elegida en la barra de navegación, cerrando antes
     * cualquier pantalla de detalle abierta.
     *
     * @param itemId Identificador del elemento del menú pulsado.
     * @return `true` si el elemento corresponde a una sección conocida.
     */
    private fun navigateToItem(itemId: Int): Boolean {
        val position = sectionMenuIds.indexOf(itemId)
        if (position < 0) return false

        closeAllDetails()
        viewPager.currentItem = position
        return true
    }

    /** Cierra todas las pantallas de detalle abiertas y vuelve a la sección visible. */
    private fun closeAllDetails() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    /**
     * Abre una pantalla de detalle sobre la sección actual, añadiéndola al
     * *back stack* para que "atrás" la cierre.
     *
     * @param fragment Fragmento de detalle a mostrar.
     */
    private fun openDetail(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.detail_container, fragment)
            addToBackStack(null)
        }
    }

    /**
     * Abre el detalle de una tarea si el `Intent` lleva los datos de una
     * notificación (`courseId` y `instanceId`).
     *
     * @param incomingIntent `Intent` recibido, o `null`.
     */
    private fun handleIntent(incomingIntent: Intent?) {
        val courseId = incomingIntent?.getIntExtra(DeepLinkExtras.COURSE_ID, 0) ?: 0
        val instanceId = incomingIntent?.getIntExtra(DeepLinkExtras.INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            openDetail(AssignmentDetailFragment.newInstance(courseId, instanceId))
        }
    }

    /**
     * Vuelve al login cuando la sesión deja de ser válida.
     *
     * ## Por qué se observan estado y evento
     *
     * El aviso inmediato llega como [MainUiEvent.SessionExpired] (lo
     * consume [observeMainEvents]). Pero si la sesión expira mientras
     * la Activity está en segundo plano, ese evento de un solo uso se
     * pierde; el `StateFlow` [MainViewModel.isSessionActive] conserva
     * el último valor y, al reanudar, sigue valiendo `false`. Por eso
     * también se colecciona aquí. El guard [isRedirectingToLogin]
     * evita navegaciones duplicadas cuando ambas señales coinciden.
     *
     * ## Dónde vive la sesión
     *
     * La Activity ya no inyecta `SessionRepository`: toda la
     * información llega a través de [MainViewModel], que es su única
     * puerta a la capa de dominio.
     */
    private fun observeSessionEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.isSessionActive.collect { isActive ->
                    if (!isActive) redirectToLogin(forwardDeepLink = false)
                }
            }
        }
    }

    /**
     * Pide el permiso de notificaciones (Android 13+) si aún no está
     * concedido. Solo se pide en un arranque nuevo, no tras una rotación.
     *
     * @param savedInstanceState Estado guardado, o `null` en un arranque nuevo.
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

    /** Observa los eventos del [MainViewModel] (alarmas exactas y sesión caducada). */
    private fun observeMainEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.events.collect { event ->
                    when (event) {
                        MainUiEvent.NeedExactAlarmPermission -> openExactAlarmSettings()
                        MainUiEvent.SessionExpired -> redirectToLogin(forwardDeepLink = false)
                    }
                }
            }
        }
    }

    /**
     * Observa el estado y los eventos del [UpdateViewModel]: muestra el
     * aviso de nueva versión mientras esté pendiente y reacciona a la
     * descarga.
     */
    private fun observeUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    updateViewModel.updateAvailable.collect { update ->
                        if (update != null) showUpdateDialog(update)
                    }
                }
                launch {
                    updateViewModel.events.collect { event -> handleUpdateEvent(event) }
                }
            }
        }
    }

    /**
     * Muestra el diálogo de actualización si no hay ya uno visible.
     *
     * @param update Nueva versión disponible.
     */
    private fun showUpdateDialog(update: AppUpdate) {
        if (updateDialog?.isShowing == true) return

        updateDialog = UpdateDialogHelper.showUpdateDialog(
            context = this,
            update = update,
            onDownload = { updateViewModel.startDownload(update) },
            onLater = { updateViewModel.dismissUpdate() },
        )
    }

    /**
     * Reacciona a los eventos de la actualización: avisa al empezar y
     * muestra un mensaje si falla la descarga o la apertura del
     * instalador. Abrir el instalador ya no es cosa de la Activity:
     * lo hace `UpdateViewModel` a través del dominio.
     *
     * @param event Evento emitido por el [UpdateViewModel].
     */
    private fun handleUpdateEvent(event: UpdateEvent) {
        when (event) {
            UpdateEvent.DownloadStarted ->
                Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show()

            UpdateEvent.InstallFailed ->
                Toast.makeText(this, R.string.update_install_failed, Toast.LENGTH_LONG).show()

            UpdateEvent.DownloadFailed ->
                Toast.makeText(this, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Abre la pantalla del sistema donde el usuario puede conceder el
     * permiso de alarmas exactas (Android 12+).
     *
     * ## Si el permiso ya está concedido
     *
     * No se abre nada: se reprograman los recordatorios directamente.
     * Esto cubre el caso de un evento de permiso encolado que se
     * consume después de que el usuario lo haya concedido en otra
     * pantalla.
     *
     * ## Si el dispositivo no tiene esa pantalla
     *
     * Se muestra un aviso.
     */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        if (canScheduleExactAlarms()) {
            mainViewModel.rescheduleReminders(promptIfDenied = false)
            return
        }

        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:$packageName".toUri()
        }
        runCatching { exactAlarmSettingsLauncher.launch(settingsIntent) }
            .onFailure {
                Toast.makeText(this, R.string.toast_exact_alarm_settings, Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Indica si la app puede programar alarmas exactas.
     *
     * En Android 11 y anteriores no se necesita permiso; desde Android
     * 12 hay que consultarlo porque el usuario puede revocarlo en
     * cualquier momento.
     *
     * @return `true` si se pueden programar alarmas exactas.
     */
    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Lleva al usuario a la pantalla de login y cierra esta Activity.
     *
     * Solo se ejecuta una vez por instancia: si el evento `Expired` y
     * la observación de la sesión nula coinciden, no se abren dos
     * pantallas de login.
     *
     * @param forwardDeepLink Si es `true`, reenvía al login los datos de
     *   la notificación que abrió la app, para retomarlos tras identificarse.
     */
    private fun redirectToLogin(forwardDeepLink: Boolean) {
        if (isRedirectingToLogin) return
        isRedirectingToLogin = true

        val loginIntent = Intent(this, LoginActivity::class.java)
        if (forwardDeepLink) {
            DeepLinkExtras.copy(from = intent, destination = loginIntent)
        }
        startActivity(loginIntent)
        finish()
    }

    /**
     * Indica si el dispositivo está en orientación horizontal.
     *
     * @return `true` en horizontal (rail lateral y sin deslizamiento).
     */
    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private companion object {
        const val STATE_CURRENT_SECTION = "STATE_CURRENT_SECTION"
    }
}
