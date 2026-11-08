package onion.gh0st.iMoodle.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Estado observable de la pantalla de login.
 *
 * ## Qué contiene
 *
 * Todo lo que la UI necesita para pintar el formulario de login:
 *
 * - Si está cargando (login en curso).
 * - Si el botón de login debe estar habilitado.
 * - El alpha del botón (para el efecto visual de "deshabilitado").
 * - El texto del botón (normal o "Conectando…").
 * - La provincia seleccionada en el spinner.
 *
 * ## Por qué la provincia está en el estado
 *
 * Porque el usuario puede cambiarla antes de hacer login. El estado
 * debe reflejarlo para que, si la UI se recrea (por ejemplo, al
 * rotar), el spinner mantenga la selección.
 *
 * @property isLoading `true` mientras hay un intento de login o
 *   auto-login en curso.
 * @property isButtonEnabled `true` si el botón debe poder pulsarse
 *   (hay texto en ambos campos y no hay login en curso).
 * @property buttonAlpha Alpha del botón (0.5 deshabilitado, 1.0
 *   habilitado).
 * @property buttonTextResId ID del recurso de string del texto del
 *   botón.
 * @property selectedCentroIndex Posición del spinner de provincia
 *   (0 = "Selecciona provincia").
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val isButtonEnabled: Boolean = false,
    val buttonAlpha: Float = 0.5f,
    val buttonTextResId: Int = R.string.action_login,
    val selectedCentroIndex: Int = 0,
)

/**
 * Eventos de un solo uso consumidos por `LoginActivity`.
 *
 * ## Por qué son eventos y no parte del estado
 *
 * Porque un toast o una navegación no deben repetirse al rotar la
 * pantalla ni al volver del segundo plano. Los eventos se emiten
 * una vez y se consumen una vez.
 */
sealed interface LoginEvent {

    /**
     * Muestra un toast con el mensaje dado.
     *
     * @property message Texto ya resuelto a mostrar.
     */
    data class ShowToast(val message: String) : LoginEvent

    /**
     * Navega a `MainActivity` cerrando la pila de login.
     */
    data object NavigateToMain : LoginEvent
}

/**
 * ViewModel de la pantalla de login, con auto-login integrado.
 *
 * ## Qué hace
 *
 * 1. Al construirse, intenta un auto-login
 *    (`SessionRepository.tryAutoLogin`). Si hay una sesión guardada
 *    válida, navega directamente a `MainActivity`.
 * 2. Si no hay sesión guardada (o el auto-login falla), deja el
 *    formulario visible para que el usuario introduzca sus
 *    credenciales.
 * 3. Cuando el usuario pulsa "Iniciar sesión", hace el login real
 *    (`SessionRepository.login`) y navega a `MainActivity`.
 * 4. Traduce los errores de autenticación a mensajes genéricos
 *    para el usuario (el detalle técnico va al log).
 *
 * ## Por qué extiende `AndroidViewModel` y no `ViewModel`
 *
 * Porque necesita `Application` para resolver strings con
 * `getString`. Un `ViewModel` normal no debería usar `Context`
 * directamente (podría causar fugas de memoria), pero
 * `AndroidViewModel` está diseñado específicamente para esto: el
 * `Application` es un `Context` seguro (vive lo mismo que la app).
 *
 * ## Sobre los errores de autenticación
 *
 * `AuthException` es una clase `sealed` con varios subtipos. Este
 * ViewModel traduce cada subtipo a un recurso de string genérico:
 *
 * - **Credenciales incorrectas** (`NoMoodleSession`, `LoginFailed`):
 *   `error_bad_credentials`.
 * - **Error de red** (`Network`): `error_network`.
 * - **Cualquier otro**: `error_unexpected`.
 *
 * El detalle técnico de la excepción se registra en el log con
 * `Logging.logError`, pero nunca llega al usuario.
 *
 * @param application Contexto de aplicación (para `getString`).
 * @param sessionRepo Fachada de sesión (login + auto-login).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val sessionRepo: SessionRepository,
) : AndroidViewModel(application) {

    /** Estado interno de la pantalla. */
    private val _uiState = MutableStateFlow(LoginUiState())

    /** Estado observable de la pantalla. */
    val uiState = _uiState.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    /**
     * `true` cuando ambos campos tienen texto.
     *
     * ## Por qué es un campo privado y no parte del estado
     *
     * Porque solo se usa internamente para calcular
     * `isButtonEnabled` y `buttonAlpha`. Exponerlo en el estado
     * público obligaría a la UI a mirarlo, y no aportaría nada (la
     * UI ya tiene `isButtonEnabled`).
     */
    private var hasInput = false

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * Lanza un auto-login para recuperar la sesión guardada si
     * existe.
     */
    init {
        attemptAutoLogin()
    }

    /**
     * Actualiza el estado del botón cuando cambian los inputs.
     *
     * ## Cuándo se llama
     *
     * Desde el `TextWatcher` de la Activity en cada pulsación de
     * tecla.
     *
     * ## Por qué no es `debounce`
     *
     * Porque el estado del botón debe reaccionar en tiempo real. Un
     * `debounce` retrasaría la actualización y el usuario notaría
     * un lag al escribir.
     *
     * @param user Texto actual del campo usuario.
     * @param pass Texto actual del campo contraseña.
     */
    fun onInputChanged(user: String, pass: String) {
        hasInput = user.isNotBlank() && pass.isNotBlank()
        _uiState.update { currentState ->
            currentState.copy(
                isButtonEnabled = hasInput && !currentState.isLoading,
                buttonAlpha = if (hasInput) 1.0f else 0.5f,
            )
        }
    }

    /**
     * Actualiza la provincia seleccionada en el spinner.
     *
     * ## Por qué se normaliza a 0 si es negativo
     *
     * Porque el spinner no debería tener índices negativos, pero
     * si por algún motivo llegara uno, es mejor tratarlo como 0
     * (la opción "Selecciona provincia") que dejarlo pasar.
     *
     * @param index Posición del spinner (0 = "Selecciona
     *   provincia").
     */
    fun onProvinceSelected(index: Int) {
        _uiState.update { currentState ->
            currentState.copy(selectedCentroIndex = index.coerceAtLeast(0))
        }
    }

    /**
     * Intenta recuperar la sesión guardada.
     *
     * ## Comportamiento según resultado
     *
     * - **Éxito con sesión**: navega a `MainActivity` (con toast de
     *   bienvenida).
     * - **Éxito sin sesión**: deja el formulario visible y listo.
     * - **Fallo**: muestra un toast de "recuperación fallida" y deja
     *   el formulario visible.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init` (primera vez).
     * - Desde la UI, si en el futuro se añade un botón de
     *   "reintentar".
     */
    fun attemptAutoLogin() {
        viewModelScope.launch {
            setLoading()
            sessionRepo.tryAutoLogin()
                .onSuccess { activeSession -> handleAutoLoginResult(activeSession) }
                .onFailure { exception ->
                    Logging.logError(TAG, "tryAutoLogin", exception)
                    emitToast(R.string.toast_session_recovery_failed)
                }
        }
    }

    /**
     * Ejecuta el login con las credenciales introducidas.
     *
     * ## Comportamiento
     *
     * - **Éxito**: emite un toast de bienvenida y navega a
     *   `MainActivity`.
     * - **Fallo**: traduce el error a un mensaje genérico y emite un
     *   toast.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa "Iniciar sesión".
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param user Usuario de la Junta (NIF/NIE/IdEA).
     * @param pass Contraseña del usuario.
     */
    fun login(provincia: Provincia, user: String, pass: String) {
        hasInput = true
        viewModelScope.launch {
            setLoading()
            sessionRepo.login(provincia, CasCredentials(user, pass))
                .onSuccess { activeSession -> onSessionReady(activeSession) }
                .onFailure { exception -> emitError(exception) }
        }
    }

    // --- Métodos internos ---

    /**
     * Pone el estado en modo "cargando".
     *
     * ## Qué hace
     *
     * - Deshabilita el botón (`isButtonEnabled = false`).
     * - Pone el alpha a 0.5.
     * - Cambia el texto a "Conectando…".
     * - Marca `isLoading = true`.
     */
    private fun setLoading() = _uiState.update {
        it.copy(
            isLoading = true,
            isButtonEnabled = false,
            buttonAlpha = 0.5f,
            buttonTextResId = R.string.loading_connecting,
        )
    }

    /**
     * Vuelve al estado "reposo".
     *
     * ## Qué hace
     *
     * - Desmarca `isLoading`.
     * - Reactiva el botón si había texto (`hasInput`).
     * - Restaura el texto original del botón.
     */
    private fun stopLoading() = _uiState.update {
        it.copy(
            isLoading = false,
            isButtonEnabled = hasInput,
            buttonAlpha = if (hasInput) 1.0f else 0.5f,
            buttonTextResId = R.string.action_login,
        )
    }

    /**
     * Maneja el resultado del auto-login.
     *
     * ## Comportamiento
     *
     * - **Sesión recuperada**: navega a `MainActivity`.
     * - **Sin sesión**: deja el formulario listo.
     *
     * @param activeSession Sesión recuperada, o `null` si no había.
     */
    private suspend fun handleAutoLoginResult(activeSession: ActiveSession?) {
        if (activeSession != null) onSessionReady(activeSession) else stopLoading()
    }

    /**
     * Emite toast de bienvenida y navega a `MainActivity`.
     *
     * ## Sobre el nombre a mostrar
     *
     * Se prioriza `user.firstname` (el nombre de pila). Si está
     * vacío (por ejemplo, en auto-login offline en el que
     * `firstname` no se pudo cargar), se cae al `username` de la
     * sesión.
     *
     * @param activeSession Sesión autenticada.
     */
    private suspend fun onSessionReady(activeSession: ActiveSession) {
        val displayName = activeSession.user?.firstname?.ifBlank { activeSession.context.user }
            ?: activeSession.context.user
        emitToastWithArg(R.string.toast_welcome_back, displayName)
        _events.send(LoginEvent.NavigateToMain)
    }

    /**
     * Convierte una excepción de login en toast de error.
     *
     * ## Qué hace
     *
     * 1. Loguea el detalle técnico con `Logging.logError`.
     * 2. Apaga `isLoading` para que el usuario pueda reintentar.
     * 3. Traduce la excepción a un recurso de string genérico con
     *    `toMessageRes`.
     * 4. Emite un toast con ese string.
     *
     * @param exception Excepción producida.
     */
    private suspend fun emitError(exception: Throwable) {
        Logging.logError(TAG, "login", exception)
        stopLoading()
        emitToast(exception.toMessageRes())
    }

    /**
     * Emite un toast simple sin argumentos de formato.
     *
     * @param resId ID del recurso de string.
     */
    private suspend fun emitToast(resId: Int) {
        stopLoading()
        _events.send(LoginEvent.ShowToast(getApplication<Application>().getString(resId)))
    }

    /**
     * Emite un toast con formato (`%s`).
     *
     * @param resId ID del recurso de string (debe contener `%s`).
     * @param arg Argumento a inyectar.
     */
    private suspend fun emitToastWithArg(resId: Int, arg: String) {
        _events.send(LoginEvent.ShowToast(getApplication<Application>().getString(resId, arg)))
    }

    /**
     * Traduce una excepción de autenticación a un recurso de string.
     *
     * ## Mapeo
     *
     * - **Credenciales incorrectas** (`NoMoodleSession`,
     *   `LoginFailed`): `error_bad_credentials`.
     * - **Error de red** (`Network`): `error_network`.
     * - **Cualquier otra** (`NoExecutionToken`, `NoRedirectToken`,
     *   `BadTokenFormat`, `InvalidToken`, etc.): `error_unexpected`.
     *
     * ## Por qué los mensajes son genéricos
     *
     * Porque el detalle técnico no aporta al usuario y podría
     * filtrar información sensible. El detalle va al log.
     *
     * @receiver Excepción producida.
     * @return ID del recurso de string correspondiente.
     */
    private fun Throwable.toMessageRes(): Int = when (this) {
        is AuthException.NoMoodleSession,
        is AuthException.LoginFailed -> R.string.error_bad_credentials
        is AuthException.Network -> R.string.error_network
        else -> R.string.error_unexpected
    }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "LoginViewModel"
    }
}