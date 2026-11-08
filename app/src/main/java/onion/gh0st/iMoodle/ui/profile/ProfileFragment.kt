package onion.gh0st.iMoodle.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentProfileBinding
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.ui.login.LoginActivity

/**
 * Pantalla de perfil del usuario.
 *
 * ## Qué muestra
 *
 * - **Identidad**: avatar, nombre completo y email (vienen de
 *   Moodle).
 * - **Datos personales**: centro, fecha de nacimiento, edad y sexo
 *   (vienen de Pasen, embebidos en el mismo `User`).
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `ProfileViewModel`.
 *
 * ## Sobre los IDs de las vistas
 *
 * Los IDs del bloque de datos personales conservan el prefijo
 * `txtPasen*` en el XML por compatibilidad con layouts existentes.
 * En una futura PR se pueden renombrar a `txtProfile*`.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    /** ViewModel de la pantalla. */
    private val viewModel: ProfileViewModel by viewModels()

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentProfileBinding.bind(view)
        setupWindowInsets(binding)
        observeUiState(binding)
        observeEvents()
        setupLogoutButton(binding)
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentProfileBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarProfile) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }
    }

    /**
     * Observa el estado del ViewModel y lo pinta.
     *
     * @param binding Binding de la vista.
     */
    private fun observeUiState(binding: FragmentProfileBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUser(binding, state.user)
                    renderProfileDetails(binding, state.user)
                }
            }
        }
    }

    /**
     * Renderiza avatar, nombre completo y email.
     *
     * ## Comportamiento
     *
     * - **Nombre a mostrar**: se compone de `firstname` +
     *   `lastname`. Si ambos están vacíos, se usa el `username`.
     * - **Email**: se oculta si está vacío.
     * - **Iniciales**: se usan como placeholder mientras carga el
     *   avatar.
     * - **Avatar**: se carga con Coil si hay URL.
     *
     * @param binding Binding de la vista.
     * @param user Usuario a mostrar, o `null`.
     */
    private fun renderUser(binding: FragmentProfileBinding, user: User?) {
        if (user == null) return

        val displayName = listOfNotNull(
            user.firstname.takeIf { it.isNotBlank() },
            user.lastname.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { user.username }

        binding.txtProfileName.text = displayName
        binding.txtProfileEmail.isVisible = user.email.isNotBlank()
        binding.txtProfileEmail.text = user.email
        binding.txtAvatarInitials.text = initialsOf(user)
        loadAvatar(binding, user)
    }

    /**
     * Renderiza el bloque de datos personales.
     *
     * ## Comportamiento
     *
     * - Si el `User` es `null` o no tiene los cuatro campos de Pasen
     *   (porque Pasen no ha respondido o ha fallado), se muestran
     *   los valores "vacíos".
     * - Si tiene los cuatro campos, se muestran.
     *
     * ## Por qué se usa `early return`
     *
     * Para que el `if` final no necesite comprobar dos veces la
     * nulabilidad, y el código sea más lineal.
     *
     * @param binding Binding de la vista.
     * @param user Usuario a mostrar, o `null`.
     */
    private fun renderProfileDetails(binding: FragmentProfileBinding, user: User?) {
        if (user == null) {
            showEmptyProfileDetails(binding)
            return
        }
        val hasDetails = user.centerName != null ||
                user.birthDate != null ||
                user.age != null ||
                user.gender != null
        if (!hasDetails) {
            showEmptyProfileDetails(binding)
            return
        }
        binding.txtPasenCenter.text = user.centerName ?: getString(R.string.profile_no_info)
        binding.txtPasenBirthdate.text = user.birthDate ?: "-"
        binding.txtPasenAge.text = user.age ?: "-"
        binding.txtPasenGender.text = user.gender ?: "-"
    }

    /**
     * Pinta los valores "vacíos" del bloque de datos personales.
     *
     * @param binding Binding de la vista.
     */
    private fun showEmptyProfileDetails(binding: FragmentProfileBinding) {
        binding.txtPasenCenter.text = getString(R.string.profile_no_info)
        binding.txtPasenBirthdate.text = "-"
        binding.txtPasenAge.text = "-"
        binding.txtPasenGender.text = "-"
    }

    /**
     * Calcula las iniciales del usuario para el placeholder del
     * avatar.
     *
     * @param user Usuario.
     * @return Las iniciales (hasta 2 caracteres), o `"?"` si no
     *   tiene nombre.
     */
    private fun initialsOf(user: User): String = buildString {
        if (user.firstname.isNotBlank()) append(user.firstname.first().uppercase())
        if (user.lastname.isNotBlank()) append(user.lastname.first().uppercase())
    }.ifBlank { "?" }

    /**
     * Carga el avatar real si lo hay.
     *
     * @param binding Binding de la vista.
     * @param user Usuario.
     */
    private fun loadAvatar(binding: FragmentProfileBinding, user: User) {
        val avatarUrl = user.profileImageUrl
        if (avatarUrl.isNullOrBlank()) return
        binding.imgAvatarPhoto.load(avatarUrl) {
            memoryCacheKey("avatar_${user.id}")
            diskCacheKey("avatar_${user.id}")
            crossfade(true)
            listener(onSuccess = { _, _ -> binding.txtAvatarInitials.isVisible = false })
        }
    }

    /**
     * Observa los eventos de un solo uso.
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        ProfileEvent.NavigateToLogin -> navigateToLogin()
                    }
                }
            }
        }
    }

    /**
     * Vuelve al login limpiando la pila de navegación.
     */
    private fun navigateToLogin() {
        val loginIntent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(loginIntent)
        requireActivity().finish()
    }

    /**
     * Configura el botón de logout con diálogo de confirmación.
     *
     * @param binding Binding de la vista.
     */
    private fun setupLogoutButton(binding: FragmentProfileBinding) {
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_logout)
                .setMessage(R.string.dialog_logout_message)
                .setPositiveButton(R.string.action_logout) { _, _ -> viewModel.logout() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }
}