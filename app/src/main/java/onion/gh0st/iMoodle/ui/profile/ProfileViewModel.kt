package onion.gh0st.iMoodle.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import onion.gh0st.iMoodle.domain.usecase.session.GetCurrentUser
import javax.inject.Inject

/**
 * Eventos de un solo uso emitidos por `ProfileViewModel`.
 */
sealed interface ProfileEvent {

    /**
     * La sesión se cerró; la UI debe volver al login.
     */
    data object NavigateToLogin : ProfileEvent
}

/**
 * Estado observable de la pantalla de perfil.
 *
 * @property user Usuario actual, ya enriquecido con los datos
 *   personales de Pasen (si la consulta ha ido bien).
 * @property provincia Nombre legible de la provincia de la sesión.
 * @property isLoading `true` mientras se carga el perfil básico.
 * @property error Mensaje de error del último refresco de identidad,
 *   o `null`.
 */
data class ProfileUiState(
    val user: User? = null,
    val provincia: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * ViewModel de la pantalla de perfil.
 *
 * ## Qué hace
 *
 * Carga el perfil del usuario en dos pasos:
 *
 * 1. **Identidad**: usuario de Moodle vía `GetCurrentUser`.
 * 2. **Perfil extendido**: enriquece el `User` con datos de Pasen
 *    vía `UserProfileRepository.enrich`.
 *
 * ## Orden de los pasos
 *
 * El orden importa: el paso 2 parte del `User` que dejó el paso 1,
 * así que un fallo de Pasen no pisa los datos de identidad. Si se
 * invirtieran, el paso 1 (Moodle) borraría los campos de Pasen que
 * hubiera rellenado el paso 2.
 *
 * ## Caché
 *
 * El paso 2 es cache-first en el repositorio. La primera consulta
 * hace login a Pasen; las siguientes leen de caché (memoria o
 * disco) sin tocar la red.
 *
 * ## Merge de identidad
 *
 * `refreshUserData` preserva los campos de Pasen que ya estaban
 * cargados en el estado, para que un refresco de identidad no borre
 * el perfil extendido.
 *
 * @param sessionRepo Fuente de la sesión activa.
 * @param getCurrentUser Caso de uso para cargar la identidad.
 * @param userProfileRepository Contrato de enriquecimiento del
 *   perfil.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val getCurrentUser: GetCurrentUser,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    /** Estado interno de la pantalla. */
    private val _uiState = MutableStateFlow(ProfileUiState())

    /** Estado observable de la pantalla. */
    val uiState = _uiState.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        seedFromSession()
        refreshUserData()
        enrichWithProfileData()
    }

    /**
     * Refresca identidad y perfil extendido.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario hace pull-to-refresh (cuando se implemente).
     */
    fun refresh() {
        refreshUserData(forceRefresh = true)
        enrichWithProfileData()
    }

    /**
     * Cierra la sesión y avisa a la UI.
     */
    fun logout() {
        viewModelScope.launch {
            sessionRepo.logout()
            _events.send(ProfileEvent.NavigateToLogin)
        }
    }

    // --- Métodos internos ---

    /**
     * Precarga el `User` y la provincia desde la sesión activa, si
     * existe.
     *
     * ## Por qué
     *
     * Para evitar el "parpadeo" del perfil vacío mientras llega la
     * respuesta de Moodle. Si la sesión ya tiene un `User`, se
     * muestra inmediatamente; cuando llegue el `User` completo, se
     * reemplaza.
     */
    private fun seedFromSession() {
        val activeSession = sessionRepo.session.value ?: return
        _uiState.update {
            it.copy(
                user = activeSession.user,
                provincia = activeSession.context.provincia.displayName,
            )
        }
    }

    /**
     * Carga la identidad del usuario desde `GetCurrentUser`.
     *
     * ## Qué hace
     *
     * 1. Llama a `getCurrentUser` con el `forceRefresh` indicado.
     * 2. Si hay usuario, lo "mergea" con los campos de Pasen que ya
     *    estuvieran cargados (para no borrarlos), lo persiste en la
     *    sesión y lo publica en el estado.
     * 3. Si no hay usuario, apaga `isLoading` sin más.
     * 4. Si falla, apaga `isLoading` y expone el error en el estado.
     *
     * @param forceRefresh Si `true`, pide datos frescos a la red.
     */
    private fun refreshUserData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            runCatching { getCurrentUser(forceRefresh) }
                .onSuccess { user ->
                    if (user != null) {
                        val mergedUser = mergePasenFields(user)
                        sessionRepo.updateUser(mergedUser)
                        _uiState.update {
                            it.copy(user = mergedUser, isLoading = false, error = null)
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(isLoading = false, error = exception.message)
                    }
                }
        }
    }

    /**
     * Preserva los campos de Pasen que ya estuvieran cargados.
     *
     * ## Qué hace
     *
     * Toma el `user` de Moodle (que no trae los campos de Pasen) y
     * le copia los campos de Pasen del `User` que ya estaba en el
     * estado o en la sesión, si los tenía.
     *
     * ## Por qué es necesario
     *
     * Porque `refreshUserData` se ejecuta después de
     * `enrichWithProfileData` en algunos flujos (por ejemplo, en
     * `refresh()`). Sin este merge, el `User` de Moodle borraría los
     * campos de Pasen que acababa de rellenar el enrich.
     *
     * @param user Usuario recién cargado desde Moodle.
     * @return Copia con los campos de Pasen preservados.
     */
    private fun mergePasenFields(user: User): User {
        val previousUser = _uiState.value.user
            ?: sessionRepo.session.value?.user
            ?: return user
        return user.copy(
            centerName = user.centerName ?: previousUser.centerName,
            birthDate = user.birthDate ?: previousUser.birthDate,
            age = user.age ?: previousUser.age,
            gender = user.gender ?: previousUser.gender,
        )
    }

    /**
     * Enriquece el `User` actual con los datos personales de Pasen.
     *
     * ## Qué hace
     *
     * 1. Toma el `User` actual del estado o de la sesión.
     * 2. Llama a `userProfileRepository.enrich`.
     * 3. Persiste el `User` enriquecido en la sesión.
     * 4. Lo publica en el estado.
     *
     * ## Manejo de errores
     *
     * Si falla (por ejemplo, porque Pasen no responde), se loguea y
     * se deja el `User` como estaba. La UI muestra el perfil básico
     * de Moodle sin los datos personales. No es un error fatal: la
     * app sigue funcionando.
     */
    private fun enrichWithProfileData() {
        viewModelScope.launch {
            try {
                val currentUser = _uiState.value.user
                    ?: sessionRepo.session.value?.user
                    ?: return@launch
                val enrichedUser = userProfileRepository.enrich(currentUser)
                sessionRepo.updateUser(enrichedUser)
                _uiState.update { it.copy(user = enrichedUser) }
            } catch (exception: Exception) {
                Logging.logError(TAG, "enrichWithProfileData", exception)
            }
        }
    }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "ProfileViewModel"
    }
}