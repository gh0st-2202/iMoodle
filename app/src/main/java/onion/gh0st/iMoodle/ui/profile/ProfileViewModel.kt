package onion.gh0st.iMoodle.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.usecase.session.EnrichUserProfile
import onion.gh0st.iMoodle.domain.usecase.session.GetCurrentUser
import onion.gh0st.iMoodle.domain.usecase.session.Logout
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.domain.usecase.session.UpdateCurrentUser
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
 * @property isRefreshing `true` mientras dura un refresco manual
 *   (*pull-to-refresh*). Es independiente de [isLoading] para que la
 *   carga inicial no muestre el indicador de refresco.
 * @property error Mensaje de error del último refresco de identidad,
 *   o `null`.
 */
data class ProfileUiState(
    val user: User? = null,
    val provincia: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
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
 *    vía [EnrichUserProfile].
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
 * `loadUserIdentity` preserva los campos de Pasen que ya estaban
 * cargados en el estado, para que un refresco de identidad no borre
 * el perfil extendido.
 *
 * @param observeSession Lectura de la sesión activa (usuario y
 *   provincia).
 * @param getCurrentUser Caso de uso para cargar la identidad.
 * @param enrichUserProfile Caso de uso para completar el perfil con
 *   Pasen.
 * @param updateCurrentUser Caso de uso para publicar el usuario
 *   enriquecido en la sesión.
 * @param performLogout Caso de uso de cierre de sesión.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeSession: ObserveSession,
    private val getCurrentUser: GetCurrentUser,
    private val enrichUserProfile: EnrichUserProfile,
    private val updateCurrentUser: UpdateCurrentUser,
    private val performLogout: Logout,
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
        viewModelScope.launch {
            loadUserIdentity(forceRefresh = false)
            enrichWithProfileData(forceRefresh = false)
        }
    }

    /**
     * Cierra la sesión y avisa a la UI para que vuelva al login.
     *
     * ## Qué hace
     *
     * Delega en [Logout], que vacía la sesión en
     * memoria, borra las credenciales, los tokens, los datos locales, las
     * alarmas y las cachés de horario y perfil. Después emite
     * [ProfileEvent.NavigateToLogin] y la pantalla navega al login
     * limpiando la pila de navegación.
     *
     * ## Si falla una limpieza
     *
     * [Logout] ya vacía la sesión y ejecuta todos los
     * pasos antes de relanzar el primer error, así que el usuario está
     * desconectado aunque algo (por ejemplo, la base de datos) falle. Por
     * eso el error solo se registra y **siempre** se navega al login: no
     * hacerlo dejaría al usuario en una pantalla de perfil sin sesión.
     */
    fun logout() {
        viewModelScope.launch {
            runCatchingCancellable { performLogout() }
                .onFailure { exception -> Logging.logError(TAG, "logout", exception) }
            _events.send(ProfileEvent.NavigateToLogin)
        }
    }

    /**
     * Refresca identidad y perfil extendido, ignorando cachés.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario desliza hacia abajo en el perfil
     * (*pull-to-refresh*). Pide datos frescos a Moodle y, **cuando
     * termina**, a Pasen; mantiene [ProfileUiState.isRefreshing]
     * activo hasta que ambas peticiones terminan, tengan éxito o no.
     * Se ignora si ya hay un refresco en curso.
     *
     * ## Por qué es secuencial y no paralelo
     *
     * Porque ambas peticiones escriben el mismo `User`. Si se lanzaran
     * a la vez, Pasen podría terminar con una copia antigua del
     * usuario (capturada antes de que Moodle devolviera la identidad)
     * y sobrescribir nombre, correo o avatar frescos. Encadenarlas
     * garantiza que el enriquecimiento parte siempre de la última
     * identidad.
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                loadUserIdentity(forceRefresh = true)
                enrichWithProfileData(forceRefresh = true)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
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
        val activeSession = observeSession.currentSession ?: return
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
     * Es `suspend`: quien la llama decide en qué corrutina ejecutarla (al
     * crear el ViewModel, en una propia; al refrescar, dentro de un
     * `coroutineScope` que espera a que termine).
     *
     * @param forceRefresh Si `true`, pide datos frescos a la red.
     */
    private suspend fun loadUserIdentity(forceRefresh: Boolean) {
        runCatchingCancellable { getCurrentUser(forceRefresh) }
            .onSuccess { user ->
                if (user != null) {
                    val mergedUser = mergePasenFields(user)
                    updateCurrentUser(mergedUser)
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
     * Porque `loadUserIdentity` se ejecuta después de
     * `enrichWithProfileData` en algunos flujos (por ejemplo, en
     * `refresh()`). Sin este merge, el `User` de Moodle borraría los
     * campos de Pasen que acababa de rellenar el enrich.
     *
     * @param user Usuario recién cargado desde Moodle.
     * @return Copia con los campos de Pasen preservados.
     */
    private fun mergePasenFields(user: User): User {
        val previousUser = _uiState.value.user
            ?: observeSession.currentSession?.user
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
     * 2. Llama a [EnrichUserProfile].
     * 3. Combina los campos de Pasen del resultado con el `User` más
     *    reciente del estado. Es importante releerlo: mientras Pasen
     *    responde, la identidad de Moodle puede haber cambiado (por
     *    ejemplo, si el usuario acaba de editar su nombre en Moodle).
     *    Así el enriquecimiento solo aporta sus campos y no pisa datos
     *    más frescos.
     * 4. Persiste el `User` resultante en la sesión.
     * 5. Lo publica en el estado.
     *
     * ## Manejo de errores
     *
     * Si falla (por ejemplo, porque Pasen no responde), se loguea y
     * se deja el `User` como estaba. La UI muestra el perfil básico
     * de Moodle sin los datos personales. No es un error fatal: la
     * app sigue funcionando. La cancelación de la corrutina sí se propaga.
     *
     * @param forceRefresh Si `true`, ignora la caché y consulta Pasen.
     */
    private suspend fun enrichWithProfileData(forceRefresh: Boolean) {
        val currentUser = _uiState.value.user
            ?: observeSession.currentSession?.user
            ?: return
        try {
            val enrichedUser = enrichUserProfile(currentUser, forceRefresh)
            val latestUser = _uiState.value.user ?: currentUser
            val mergedUser = latestUser.copy(
                centerName = enrichedUser.centerName ?: latestUser.centerName,
                birthDate = enrichedUser.birthDate ?: latestUser.birthDate,
                age = enrichedUser.age ?: latestUser.age,
                gender = enrichedUser.gender ?: latestUser.gender,
            )
            updateCurrentUser(mergedUser)
            _uiState.update { it.copy(user = mergedUser) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Logging.logError(TAG, "enrichWithProfileData", exception)
        }
    }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "ProfileViewModel"
    }
}