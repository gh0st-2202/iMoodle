package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository

/**
 * Implementación en memoria de [SessionRepository] para tests.
 *
 * Expone resultados configurables por test para [login] y
 * [tryAutoLogin]. Registra contadores de invocaciones para verificar
 * que la UI dispara las operaciones esperadas.
 *
 * @param initialSession Sesión inicial expuesta por [session].
 */
class FakeSessionRepository(
    initialSession: ActiveSession? = null,
) : SessionRepository {

    /** Estado interno de la sesión. */
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    /** Flujo interno de eventos. */
    private val _events = MutableSharedFlow<SessionEvent>()
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Resultado a devolver desde [login]. Por defecto, fallo "no configurado". */
    var loginResult: Result<ActiveSession> =
        Result.failure(UnsupportedOperationException("login no configurado"))

    /** Resultado a devolver desde [tryAutoLogin]. Por defecto, `null` (sin sesión). */
    var autoLoginResult: Result<ActiveSession?> = Result.success(null)

    /** Resultado a devolver desde [refreshUser]. */
    var refreshUserResult: Result<User?> = Result.success(null)

    /** Número de veces que se ha llamado a [login]. */
    var loginCalls: Int = 0
        private set

    /** Número de veces que se ha llamado a [tryAutoLogin]. */
    var autoLoginCalls: Int = 0
        private set

    /** Número de veces que se ha llamado a [logout]. */
    var logoutCalls: Int = 0
        private set

    /** Número de veces que se ha llamado a [invalidate]. */
    var invalidateCalls: Int = 0
        private set

    /** Número de veces que se ha llamado a [refreshUser]. */
    var refreshUserCalls: Int = 0
        private set

    /**
     * Devuelve [loginResult] y, si es éxito, publica la sesión.
     *
     * @param provincia Provincia (ignorada por el fake).
     * @param credentials Credenciales (ignoradas por el fake).
     * @return El resultado configurado.
     */
    override suspend fun login(
        provincia: Provincia,
        credentials: CasCredentials,
    ): Result<ActiveSession> {
        loginCalls++
        loginResult.onSuccess { _session.value = it }
        return loginResult
    }

    /**
     * Devuelve [autoLoginResult] y, si es éxito con sesión, la publica.
     *
     * @return El resultado configurado.
     */
    override suspend fun tryAutoLogin(): Result<ActiveSession?> {
        autoLoginCalls++
        autoLoginResult.getOrNull()?.let { _session.value = it }
        return autoLoginResult
    }

    /**
     * Limpia la sesión y cuenta la llamada.
     */
    override suspend fun logout() {
        logoutCalls++
        _session.value = null
    }

    /**
     * Limpia la sesión, emite `SessionEvent.Expired` y cuenta la llamada.
     */
    override suspend fun invalidate() {
        invalidateCalls++
        _session.value = null
        _events.tryEmit(SessionEvent.Expired)
    }

    /**
     * Actualiza el `User` en la sesión activa, si la hay.
     *
     * @param user Usuario nuevo.
     */
    override fun updateUser(user: User) {
        _session.update { it?.copy(user = user) }
    }

    /**
     * Aplica [refreshUserResult] al `User` de la sesión y cuenta la llamada.
     */
    override suspend fun refreshUser() {
        refreshUserCalls++
        refreshUserResult.getOrNull()?.let { fresh ->
            _session.update { it?.copy(user = fresh) }
        }
    }
}