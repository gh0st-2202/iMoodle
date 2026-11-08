package onion.gh0st.iMoodle.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.DeepLinkExtras
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.calendar.CalendarFragment
import onion.gh0st.iMoodle.ui.courses.list.CoursesFragment
import onion.gh0st.iMoodle.ui.profile.ProfileFragment
import onion.gh0st.iMoodle.ui.schedule.ScheduleFragment
import javax.inject.Inject

/**
 * Contenedor principal de la app tras el login.
 *
 * ## Qué hace
 *
 * Es la Activity que alberga las cuatro pantallas principales de la
 * app (Cursos, Agenda, Horario, Perfil), más la navegación entre
 * ellas:
 *
 * - **En portrait**: usa un `BottomNavigationView` (barra inferior).
 * - **En landscape**: usa un `NavigationRailView` (barra lateral
 *   izquierda).
 *
 * ## Responsabilidades
 *
 * 1. Comprobar que hay sesión activa. Si no, redirigir al login.
 * 2. Configurar la navegación (bottom nav o nav rail según
 *    orientación).
 * 3. Observar los eventos de sesión (si la sesión expira,
 *    redirigir al login).
 * 4. Pedir permisos (notificaciones, alarmas exactas).
 * 5. Procesar deep-links desde notificaciones (abrir la tarea).
 * 6. Restaurar la sección activa tras rotar.
 * 7. Lanzar la sincronización de recordatorios al abrir.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** Fuente de la sesión activa. */
    @Inject lateinit var sessionRepo: SessionRepository

    /** ViewModel de la Activity. */
    private val mainViewModel: MainViewModel by viewModels()

    /** Sección activa actual (uno de los IDs de menú). */
    private var currentSectionId: Int = R.id.nav_courses

    /**
     * Launcher del permiso de notificaciones (API 33+).
     *
     * Si el usuario deniega, se le muestra un toast informativo. No
     * se vuelve a pedir automáticamente.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                R.string.toast_no_notifications_permission,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Ciclo de vida de la Activity.
     *
     * ## Comportamiento
     *
     * 1. Si no hay sesión activa, redirige al login reenviando el
     *    deep-link (por si el proceso murió al pulsar una
     *    notificación).
     * 2. Si hay sesión, activa edge-to-edge, monta el layout,
     *    configura la navegación, observa los eventos de sesión,
     *    pide permisos y lanza la sincronización de recordatorios.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionRepo.session.value == null) {
            redirectToLogin(forwardDeepLink = true)
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupNavigation(savedInstanceState)
        observeSessionEvents()
        requestNotificationPermissionIfNeeded(savedInstanceState)
        mainViewModel.syncRemindersOnce()
        observeMainEvents()
    }

    /**
     * Persiste la sección activa antes de una posible recreación.
     *
     * @param outState Bundle donde se guarda el estado.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_SECTION, currentSectionId)
    }

    /**
     * Procesa un `Intent` nuevo (por ejemplo, al pulsar una
     * notificación con la Activity ya viva).
     *
     * @param intent `Intent` recibido.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Observa los eventos de sesión y reacciona a la expiración.
     *
     * Si la sesión expira (token de Moodle inválido sin refresco
     * posible), redirige al login.
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
     * Solicita el permiso `POST_NOTIFICATIONS` en API 33+ si no está
     * concedido.
     *
     * ## Comportamiento
     *
     * - **Recreación** (`savedInstanceState != null`): no se
     *   solicita (ya se pidió antes).
     * - **API < 33**: no se necesita el permiso.
     * - **API 33+ sin permiso**: se solicita con el launcher.
     *
     * @param savedInstanceState Estado previo.
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
     * Observa los eventos del `MainViewModel`.
     *
     * Actualmente solo hay un evento: `NeedExactAlarmPermission`. Al
     * recibirlo, se abren los ajustes del sistema.
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
     * Abre los ajustes del sistema para solicitar el permiso de
     * alarmas exactas.
     *
     * ## Por qué se abre un Intent
     *
     * Porque Android no tiene un diálogo estándar para este
     * permiso. La única forma es abrir la pantalla de ajustes
     * correspondiente y que el usuario lo conceda manualmente.
     *
     * ## Comportamiento en caso de fallo
     *
     * Si el Intent falla (poco probable), se muestra un toast con
     * un mensaje informativo.
     */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(settingsIntent) }
            .onFailure {
                Toast.makeText(
                    this,
                    R.string.toast_exact_alarm_settings,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    /**
     * Configura la navegación según la orientación.
     *
     * ## Comportamiento
     *
     * - **Landscape**: `NavigationRailView` (barra lateral).
     * - **Portrait**: `BottomNavigationView` (barra inferior).
     *
     * @param savedInstanceState Estado previo.
     */
    private fun setupNavigation(savedInstanceState: Bundle?) {
        val isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) setupNavigationRail(savedInstanceState)
        else setupBottomNavigation(savedInstanceState)
    }

    /**
     * Configura el `NavigationRailView` en landscape.
     *
     * ## Qué hace
     *
     * 1. Aplica los insets de barras del sistema.
     * 2. Restaura la sección activa si hay estado previo.
     * 3. Configura el listener de selección.
     * 4. Procesa el `Intent` inicial si es la primera vez.
     *
     * @param savedInstanceState Estado previo.
     */
    private fun setupNavigationRail(savedInstanceState: Bundle?) {
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
            currentSectionId = savedInstanceState.getInt(STATE_CURRENT_SECTION, R.id.nav_courses)
            navigationRail.selectedItemId = currentSectionId
        }
        navigationRail.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /**
     * Configura el `BottomNavigationView` en portrait.
     *
     * ## Qué hace
     *
     * 1. Aplica los insets de barras del sistema.
     * 2. Restaura la sección activa si hay estado previo.
     * 3. Configura el listener de selección.
     * 4. Procesa el `Intent` inicial si es la primera vez.
     *
     * @param savedInstanceState Estado previo.
     */
    private fun setupBottomNavigation(savedInstanceState: Bundle?) {
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
            currentSectionId = savedInstanceState.getInt(STATE_CURRENT_SECTION, R.id.nav_courses)
            bottomNavigation.selectedItemId = currentSectionId
        }
        bottomNavigation.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /**
     * Cambia el `Fragment` al item de menú indicado.
     *
     * @param itemId ID del item de menú.
     * @return `true` si el item se reconoció y se navegó; `false` en
     *   caso contrario.
     */
    private fun navigateToItem(itemId: Int): Boolean {
        val targetFragment = fragmentForItem(itemId) ?: return false
        currentSectionId = itemId
        supportFragmentManager.commit { replace(R.id.fragment_container, targetFragment) }
        return true
    }

    /**
     * Devuelve el `Fragment` asociado a un item de menú.
     *
     * @param itemId ID del item de menú.
     * @return El `Fragment`, o `null` si el item no se reconoce.
     */
    private fun fragmentForItem(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_courses -> CoursesFragment()
        R.id.nav_calendar -> CalendarFragment()
        R.id.nav_schedule -> ScheduleFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }

    /**
     * Procesa el `Intent` recibido.
     *
     * ## Comportamiento
     *
     * - Si trae extras de deep-link (curso + instancia), abre el
     *   detalle de la tarea.
     * - Si no, abre la pantalla por defecto (lista de cursos).
     *
     * @param incomingIntent `Intent` a procesar, o `null`.
     */
    private fun handleIntent(incomingIntent: Intent?) {
        val courseId = incomingIntent?.getIntExtra(DeepLinkExtras.COURSE_ID, 0) ?: 0
        val instanceId = incomingIntent?.getIntExtra(DeepLinkExtras.INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            openAssignmentDetail(courseId, instanceId)
        } else {
            openDefaultScreen()
        }
    }

    /**
     * Abre el detalle de una tarea (deep-link desde notificación).
     *
     * @param courseId ID del curso.
     * @param instanceId ID de la instancia del módulo.
     */
    private fun openAssignmentDetail(courseId: Int, instanceId: Int) {
        supportFragmentManager.commit {
            replace(
                R.id.fragment_container,
                AssignmentDetailFragment.newInstance(courseId, instanceId),
            )
            addToBackStack(null)
        }
    }

    /**
     * Abre la pantalla por defecto (lista de cursos).
     */
    private fun openDefaultScreen() {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, CoursesFragment())
        }
    }

    /**
     * Redirige al login y finaliza esta Activity.
     *
     * @param forwardDeepLink Si `true`, reenvía los extras de
     *   deep-link al `LoginActivity` para que, tras recuperar la
     *   sesión, se abra la tarea original.
     */
    private fun redirectToLogin(forwardDeepLink: Boolean) {
        val loginIntent = Intent(this, onion.gh0st.iMoodle.ui.login.LoginActivity::class.java)
        if (forwardDeepLink) DeepLinkExtras.copy(from = intent, to = loginIntent)
        startActivity(loginIntent)
        finish()
    }

    private companion object {
        /** Clave del `Bundle` para la sección activa. */
        const val STATE_CURRENT_SECTION = "STATE_CURRENT_SECTION"
    }
}