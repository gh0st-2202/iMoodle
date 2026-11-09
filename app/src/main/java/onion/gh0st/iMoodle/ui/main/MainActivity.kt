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
 * Pantalla principal que alberga las cuatro pestañas y gestiona la
 * navegación.
 *
 * ## Cómo funciona
 * Usa un "ViewPager2" (un carrusel de pantallas) para las pestañas
 * principales. Cuando el usuario desliza el dedo horizontalmente
 * (en vertical), el ViewPager cambia de pestaña. Además, tiene un
 * contenedor extra para abrir pantallas de detalle (tareas, foros)
 * sin perder el estado de las pestañas.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var sessionRepo: SessionRepository

    private val mainViewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var detailContainer: androidx.fragment.app.FragmentContainerView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.toast_no_notifications_permission, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Inicia la pantalla principal.
     *
     * Su propósito es comprobar si hay sesión, preparar la estructura
     * visual (pestañas, barras laterales o inferiores), configurar la
     * navegación y empezar a escuchar eventos (como actualizaciones).
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

        observeSessionEvents()
        requestNotificationPermissionIfNeeded(savedInstanceState)
        mainViewModel.syncRemindersOnce()
        observeMainEvents()

        updateViewModel.check()
        observeUpdates()

        supportFragmentManager.addOnBackStackChangedListener {
            val isDetailVisible = supportFragmentManager.backStackEntryCount > 0
            detailContainer.visibility = if (isDetailVisible) android.view.View.VISIBLE else android.view.View.GONE
            viewPager.visibility = if (isDetailVisible) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    /**
     * Configura el carrusel de pestañas.
     *
     * Su propósito es enlazar el adaptador con el carrusel, decidir
     * si se permite deslizar con el dedo (solo en vertical) y
     * sincronizar el menú de abajo con la pestaña que se está viendo.
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
     * Cambia a una pestaña específica.
     *
     * Su propósito es responder cuando el usuario toca la barra de
     * navegación inferior o lateral, cerrando cualquier detalle abierto.
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
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        viewPager.currentItem = position
        return true
    }

    /**
     * Abre una pantalla de detalle por encima de las pestañas.
     *
     * Su propósito es mostrar información específica (como los
     * archivos de una tarea) sin perder la pestaña en la que
     * estaba el usuario.
     */
    private fun openDetail(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.detail_container, fragment)
            addToBackStack(null)
        }
    }

    /**
     * Guarda qué pestaña estaba viendo el usuario.
     *
     * Su propósito es recordar la posición si el teléfono gira o
     * se queda sin memoria, para no devolverlo a la primera pestaña.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_SECTION, viewPager.currentItem)
    }

    /**
     * Maneja nuevos enlaces o notificaciones.
     *
     * Su propósito es interceptar si el usuario llega desde una
     * notificación y llevarlo a la pantalla correcta.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Escucha si la sesión ha caducado.
     *
     * Su propósito es vigilar la sesión del usuario y, si ya no es
     * válida, mandarlo a la pantalla de login.
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
     * Pide permiso para enviar notificaciones.
     *
     * Su propósito es asegurarse de que el sistema operativo permite
     * mostrar avisos de tareas, solicitando el permiso si aún no lo
     * tiene.
     */
    private fun requestNotificationPermissionIfNeeded(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Escucha eventos relacionados con las alarmas de recordatorio.
     *
     * Su propósito es abrir la configuración del teléfono si el
     * sistema operativo bloquea las alarmas.
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
     * Escucha si hay una nueva actualización de la aplicación.
     *
     * Su propósito es mostrar un diálogo flotante al usuario cuando
     * hay una versión más nueva disponible en internet.
     */
    private fun observeUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateViewModel.updateAvailable.collect { update ->
                    if (update != null) {
                        UpdateDialogHelper.showUpdateDialog(this@MainActivity, update)
                    }
                }
            }
        }
    }

    /**
     * Abre los ajustes del sistema para alarmas exactas.
     *
     * Su propósito es llevar al usuario a la pantalla de Android
     * donde puede permitir que la app programe alarmas a la hora exacta.
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
     * Prepara la barra de navegación.
     *
     * Su propósito es configurar los botones (inferiores en vertical,
     * laterales en horizontal), ajustar sus márgenes y conectar el
     * clic de cada botón con el cambio de pestaña.
     */
    private fun setupNavigation(savedInstanceState: Bundle?) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            val navigationRail = findViewById<NavigationRailView>(R.id.navigation_rail)
            ViewCompat.setOnApplyWindowInsetsListener(navigationRail) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(view.paddingLeft + systemBars.left, systemBars.top, view.paddingRight, systemBars.bottom)
                insets
            }

            if (savedInstanceState != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(STATE_CURRENT_SECTION, 0), false)
            } else {
                handleIntent(intent)
            }
            navigationRail.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        } else {
            val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_nav)
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
                insets
            }

            if (savedInstanceState != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(STATE_CURRENT_SECTION, 0), false)
            } else {
                handleIntent(intent)
            }
            bottomNavigation.setOnItemSelectedListener { menuItem -> navigateToItem(menuItem.itemId) }
        }
    }

    /**
     * Revisa si la app se abrió desde un enlace especial.
     *
     * Su propósito es leer los datos del enlace y abrir la tarea
     * que corresponda automáticamente.
     */
    private fun handleIntent(incomingIntent: Intent?) {
        val courseId = incomingIntent?.getIntExtra(DeepLinkExtras.COURSE_ID, 0) ?: 0
        val instanceId = incomingIntent?.getIntExtra(DeepLinkExtras.INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            openAssignmentDetail(courseId, instanceId)
        }
    }

    /**
     * Abre el detalle de una tarea concreta.
     *
     * Su propósito es mostrar la pantalla de una tarea específica
     * usando el contenedor de detalles.
     */
    private fun openAssignmentDetail(courseId: Int, instanceId: Int) {
        openDetail(AssignmentDetailFragment.newInstance(courseId, instanceId))
    }

    /**
     * Redirige al usuario a la pantalla de inicio de sesión.
     *
     * Su propósito es cerrar esta pantalla y abrir la de login,
     * pasándole los datos necesarios para que el usuario pueda
     * volver a entrar si venía de una notificación.
     */
    private fun redirectToLogin(forwardDeepLink: Boolean) {
        val loginIntent = Intent(this, onion.gh0st.iMoodle.ui.login.LoginActivity::class.java)
        if (forwardDeepLink) DeepLinkExtras.copy(from = intent, to = loginIntent)
        startActivity(loginIntent)
        finish()
    }

    private companion object {
        const val STATE_CURRENT_SECTION = "STATE_CURRENT_SECTION"
    }
}