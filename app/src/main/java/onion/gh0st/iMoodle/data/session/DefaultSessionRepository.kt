package onion.gh0st.iMoodle.data.session

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionFactory
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.repository.MoodleUserRepository
import onion.gh0st.iMoodle.api.moodle.repository.UserRepositoryFactory
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.data.local.LocalDataCleaner
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionContext
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación por defecto del gestor del ciclo de vida de la
 * sesión.
 *
 * ## Qué hace esta clase
 *
 * Es el **cerebro** de la sesión del usuario. Coordina todas las
 * piezas que participan en el ciclo de vida:
 *
 * - **`AuthRepository`**: hace el login real contra CAS + Moodle.
 * - **`TokenStore`**: guarda la sesión en disco (cifrada).
 * - **`CredentialsStore`**: guarda las credenciales para el refresco
 *   silencioso.
 * - **`MoodleSessionFactory`**: reconstruye la sesión viva (cliente
 *   HTTP, cookies) a partir de los datos puros.
 * - **`NotificationScheduler`**: cancela las alarmas al hacer logout.
 * - **`LocalDataCleaner`**: borra los datos locales al hacer logout.
 * - **`UserCache`**: mantiene el `User` actual en memoria.
 * - **`UserRepositoryFactory`**: crea el repositorio de usuario
 *   cuando hace falta.
 *
 * El resultado es una única clase que expone:
 *
 * 1. La **sesión activa** como `StateFlow` (la UI la observa).
 * 2. Los **eventos de sesión** como `SharedFlow` (la UI reacciona a
 *    expiraciones y refrescos).
 * 3. Los métodos para **login**, **auto-login**, **logout**,
 *    **invalidación** y **actualización del usuario**.
 *
 * ## Qué es una sesión "activa"
 *
 * Es la combinación de:
 *
 * - Un **`SessionContext`** puro (provincia, usuario, token).
 * - Un **`User`** cargado (nombre, email, avatar).
 * - Una **`MoodleSession`** viva (cliente HTTP, cookies, API REST).
 *
 * La primera y la segunda se exponen a la UI a través de
 * `ActiveSession`. La tercera se guarda internamente y se expone a
 * los repositorios de `api/` a través de `SessionApiProvider` y
 * `MoodleSessionProvider`.
 *
 * ## Por qué implementa tres interfaces
 *
 * Esta clase es la **única** que conoce el ciclo de vida completo de
 * la sesión. Tanto la UI (que necesita `SessionRepository`) como los
 * repositorios de `api/` (que necesitan `SessionApiProvider` y
 * `MoodleSessionProvider`) piden cosas distintas, pero todas las
 * respuestas vienen del mismo sitio. Tener una sola clase que
 * implemente las tres interfaces garantiza que las tres vistas del
 * estado son siempre coherentes entre sí.
 *
 * ## Sobre los dos estados internos
 *
 * Hay dos `MutableStateFlow` privados:
 *
 * - **`_session`**: la sesión "pura" que ve la UI. Contiene
 *   `SessionContext` + `User`. No contiene infraestructura.
 * - **`_moodleSession`**: la sesión "viva" que usan los repositorios
 *   de `api/`. Contiene el cliente HTTP, las cookies y el
 *   `MoodleApiClient`.
 *
 * Se mantienen sincronizados: cuando uno cambia, el otro también.
 * Si se desincronizaran, `requireApi()` fallaría y la app dejaría de
 * funcionar.
 */
@Singleton
class DefaultSessionRepository @Inject constructor(
    private val authRepo: AuthRepository,
    private val tokenStore: TokenStore,
    private val sessionFactory: MoodleSessionFactory,
    private val notificationScheduler: NotificationScheduler,
    private val credentialsStore: CredentialsStore,
    private val userRepositoryFactory: UserRepositoryFactory = UserRepositoryFactory {
        MoodleUserRepository(it, UserCache())
    },
    private val userCache: UserCache = UserCache(),
    private val localDataCleaner: LocalDataCleaner = LocalDataCleaner { },
) : SessionRepository, SessionApiProvider, MoodleSessionProvider {

    /** Estado interno de la sesión "pura". */
    private val _session = MutableStateFlow<ActiveSession?>(null)
    override val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    /** Flujo interno de eventos puntuales. */
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Estado interno de la sesión "viva". */
    private val _moodleSession = MutableStateFlow<MoodleSession?>(null)

    /**
     * Cerrojo para operaciones que persisten en `TokenStore`.
     *
     * ## Qué protege
     *
     * El guardado del `userId` y el guardado del token refrescado.
     * Sin él, dos operaciones concurrentes podrían pisarse.
     */
    private val persistMutex = Mutex()

    /**
     * Cerrojo para operaciones que modifican la sesión.
     *
     * ## Qué protege
     *
     * El login, el logout y la invalidación. Garantiza que no se
     * solapen: si el usuario pulsa "Cerrar sesión" mientras se está
     * haciendo un login, el logout espera a que termine el login.
     */
    private val sessionMutex = Mutex()

    /**
     * Realiza el login completo.
     *
     * ## Qué hace
     *
     * Delega en [performLogin], que hace todo el trabajo. Se envuelve
     * en `runCatchingCancellable` para convertir cualquier excepción
     * en un `Result`, respetando la cancelación de corrutinas.
     *
     * ## Qué devuelve
     *
     * Un `Result` con la `ActiveSession` creada, o con la excepción
     * que haya ocurrido.
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param credentials Credenciales del usuario.
     * @return `Result` con la sesión o con la excepción.
     */
    override suspend fun login(
        provincia: Provincia,
        credentials: CasCredentials,
    ): Result<ActiveSession> =
        runCatchingCancellable { performLogin(provincia, credentials) }

    /**
     * Intenta recuperar la sesión guardada.
     *
     * @return `Result` con la `ActiveSession` recuperada (o `null` si
     *   no hay), o con la excepción que haya ocurrido.
     */
    override suspend fun tryAutoLogin(): Result<ActiveSession?> =
        runCatchingCancellable { performAutoLogin() }

    /**
     * Cierra la sesión y limpia todo.
     *
     * ## Qué hace
     *
     * Toma el `sessionMutex` y delega en [clearSessionData], que
     * limpia el estado interno, los almacenes y las alarmas.
     */
    override suspend fun logout() = sessionMutex.withLock { clearSessionData() }

    /**
     * Invalida la sesión y emite un evento `Expired`.
     *
     * ## Diferencia con [logout]
     *
     * - **`logout`**: el usuario cierra sesión voluntariamente. No se
     *   emite ningún evento (la UI ya sabe lo que ha pasado).
     * - **`invalidate`**: la sesión se invalida por un motivo ajeno al
     *   usuario (token caducado, por ejemplo). Se emite un evento
     *   `Expired` para que la UI reaccione (redirigiendo al login).
     *
     * ## Cuándo se llama
     *
     * - Cuando `MoodleApiClient` no puede refrescar el token.
     * - Cuando el usuario cambia la contraseña en otro sitio.
     */
    override suspend fun invalidate() {
        val hadSession = sessionMutex.withLock {
            val sessionExisted = _session.value != null
            clearSessionData()
            sessionExisted
        }
        if (hadSession) _events.tryEmit(SessionEvent.Expired)
    }

    /**
     * Actualiza el `User` de la sesión activa.
     *
     * @param user Usuario actualizado.
     */
    override fun updateUser(user: User) {
        _session.update { activeSession -> activeSession?.copy(user = user) }
    }

    /**
     * Refresca el `User` desde red.
     *
     * ## Comportamiento
     *
     * - Si no hay sesión, no hace nada.
     * - Si hay sesión, pide el usuario a Moodle, actualiza el estado
     *   y la caché.
     * - Si falla, **mantiene el usuario anterior**. Esto es importante
     *   para no dejar al usuario sin datos si la red falla.
     */
    override suspend fun refreshUser() {
        val moodleSession = _moodleSession.value ?: return
        val activeSession = _session.value ?: return
        try {
            val refreshedUser = loadUser(moodleSession)
            _session.value = activeSession.copy(user = refreshedUser)
            userCache.set(refreshedUser)
        } catch (exception: Exception) {
        }
    }

    /**
     * Devuelve el `MoodleApiClient` de la sesión activa.
     *
     * @return El cliente REST.
     * @throws IllegalStateException si no hay sesión activa.
     */
    override fun requireApi(): MoodleApiClient =
        _moodleSession.value?.api
            ?: error("SessionApiProvider.requireApi() sin sesión activa")

    /**
     * Devuelve la `MoodleSession` de la sesión activa, o `null`.
     *
     * @return La sesión viva, o `null`.
     */
    override fun currentSession(): MoodleSession? = _moodleSession.value

    // --- Métodos internos ---

    /**
     * Limpia todos los estados y persistencias.
     *
     * ## Qué hace paso a paso
     *
     * 1. Captura la provincia de la sesión actual (para el logout
     *    del `AuthRepository`).
     * 2. Limpia `_session` y `_moodleSession`.
     * 3. Si había provincia, llama a `authRepo.logout` (borra la
     *    sesión del `TokenStore`).
     * 4. Borra todos los tokens del `TokenStore`.
     * 5. Borra las credenciales del `CredentialsStore`.
     * 6. Cancela todas las alarmas programadas.
     * 7. Borra todos los datos locales (Room).
     * 8. Limpia la caché del usuario.
     */
    private suspend fun clearSessionData() {
        val currentProvincia = _session.value?.context?.provincia
        _session.value = null
        _moodleSession.value = null
        if (currentProvincia != null) authRepo.logout(currentProvincia)
        tokenStore.clearAll()
        credentialsStore.clear()
        notificationScheduler.cancelAll()
        localDataCleaner.clear()
        userCache.clear()
    }

    /**
     * Implementación real del login.
     *
     * ## Qué hace paso a paso
     *
     * 1. Llama a `authRepo.login`, que hace el login CAS + Moodle y
     *    persiste la sesión.
     * 2. Construye la `MoodleSession` viva con
     *    `sessionFactory.create`, pasándole los callbacks de refresco
     *    e invalidación.
     * 3. Carga el `User` de Moodle con `loadUser`. Si falla, hace
     *    logout y propaga la excepción (un login sin perfil no
     *    sirve de nada).
     * 4. Persiste el `userId` en el `TokenStore` (para futuros
     *    auto-logins offline).
     * 5. Actualiza la caché del usuario.
     * 6. Publica la sesión activa en los dos `StateFlow`.
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param credentials Credenciales del usuario.
     * @return La `ActiveSession` creada.
     * @throws Exception si algo falla durante el proceso.
     */
    private suspend fun performLogin(
        provincia: Provincia,
        credentials: CasCredentials,
    ): ActiveSession {
        val sessionContext = authRepo.login(
            provincia = provincia,
            credentials = credentials,
            onTokenRefreshed = ::persistRefreshedToken,
            onInvalidToken = ::handleInvalidToken,
        )

        val moodleSession = sessionFactory.create(
            context = sessionContext,
            onTokenRefreshed = ::persistRefreshedToken,
            onInvalidToken = ::handleInvalidToken,
        )

        val user = try {
            loadUser(moodleSession)
        } catch (exception: Exception) {
            withContext(NonCancellable) { authRepo.logout(provincia) }
            throw exception
        }
        persistUserId(provincia, user.id)
        userCache.set(user)

        val activeSession = ActiveSession(
            context = sessionContext.copy(userId = user.id),
            user = user,
        )
        _session.value = activeSession
        _moodleSession.value = moodleSession
        return activeSession
    }

    /**
     * Implementación real del auto-login.
     *
     * ## Qué hace paso a paso
     *
     * 1. Llama a `authRepo.tryAutoLogin`, que recupera la sesión
     *    guardada y valida el token.
     * 2. Si no hay sesión, devuelve `null`.
     * 3. Construye la `MoodleSession` viva.
     * 4. Intenta cargar el `User` desde Moodle. Si falla por token
     *    inválido, devuelve `null` (la sesión guardada ya no sirve).
     * 5. Si falla por otra cosa (red), reconstruye un `User` básico
     *    con el `userId` guardado y el `username` de la sesión.
     * 6. Persiste el `userId` (por si la sesión guardada no lo
     *    tenía).
     * 7. Publica la sesión activa.
     *
     * @return La `ActiveSession` recuperada, o `null` si no hay
     *   sesión válida.
     */
    private suspend fun performAutoLogin(): ActiveSession? {
        val sessionContext = authRepo.tryAutoLogin() ?: return null

        val moodleSession = sessionFactory.create(
            context = sessionContext,
            onTokenRefreshed = ::persistRefreshedToken,
            onInvalidToken = ::handleInvalidToken,
        )

        val fetchedUser: User? = try {
            fetchUserOrNull(moodleSession)
        } catch (exception: AuthException.InvalidToken) {
            return null
        }
        fetchedUser?.let { user ->
            persistUserId(sessionContext.provincia, user.id)
            userCache.set(user)
        }
        val user = fetchedUser ?: offlineUser(sessionContext)
        val activeSession = ActiveSession(context = sessionContext, user = user)
        _session.value = activeSession
        _moodleSession.value = moodleSession
        return activeSession
    }

    /**
     * Carga el `User` de Moodle con `forceRefresh = true`.
     *
     * @param moodleSession Sesión viva de la que obtener el cliente
     *   REST.
     * @return El `User` cargado.
     */
    private suspend fun loadUser(moodleSession: MoodleSession): User =
        userRepositoryFactory.create(moodleSession.api).getUser(forceRefresh = true)

    /**
     * Intenta cargar el `User` y devuelve `null` si falla por algo
     * distinto de un token inválido.
     *
     * ## Manejo de errores
     *
     * - **Token inválido**: se propaga (el llamador decidirá).
     * - **Cualquier otro error**: se devuelve `null` (el llamador
     *   reconstruirá un `User` básico).
     *
     * @param moodleSession Sesión viva.
     * @return El `User` cargado, o `null` si falló por un error no
     *   crítico.
     * @throws AuthException.InvalidToken si el token es inválido.
     */
    private suspend fun fetchUserOrNull(moodleSession: MoodleSession): User? =
        runCatchingCancellable { loadUser(moodleSession) }.getOrElse { exception ->
            if (exception is AuthException.InvalidToken) throw exception
            null
        }

    /**
     * Construye un `User` "básico" a partir de los datos de la
     * sesión, para casos en los que no se ha podido cargar el
     * perfil completo desde Moodle.
     *
     * ## Cuándo se usa
     *
     * En auto-login offline: hay sesión guardada, pero no hay red
     * para cargar el perfil. Se rellena lo que se puede con los datos
     * de la sesión (id y username), y el resto con cadenas vacías.
     *
     * ## Cuándo devuelve `null`
     *
     * Si el `userId` de la sesión es 0 (no se conoce). Sin `userId`,
     * el `User` no tiene sentido: muchas pantallas lo necesitan para
     * hacer peticiones.
     *
     * @param sessionContext Contexto de la sesión.
     * @return El `User` básico, o `null` si no hay `userId`.
     */
    private fun offlineUser(sessionContext: SessionContext): User? {
        if (sessionContext.userId <= 0) return null
        return User(
            id = sessionContext.userId,
            username = sessionContext.user,
            firstname = "",
            lastname = "",
            fullname = sessionContext.user,
            email = "",
        )
    }

    /**
     * Persiste el `userId` en el `TokenStore`.
     *
     * ## Por qué
     *
     * El `userId` se usa para identificar al usuario en la base de
     * datos local. Si no se guarda, un auto-login offline no podría
     * consultar los cursos cacheados (porque no sabría qué `userId`
     * filtrar).
     *
     * ## Cuándo se llama
     *
     * - Tras un login exitoso.
     * - Tras un auto-login que descubre el `userId`.
     *
     * @param provincia Provincia de la sesión.
     * @param userId ID del usuario en Moodle.
     */
    private suspend fun persistUserId(provincia: Provincia, userId: Int) {
        persistMutex.withLock {
            val storedSession = tokenStore.load(provincia.slug)
            if (storedSession != null && storedSession.userId != userId) {
                tokenStore.save(storedSession.copy(userId = userId))
            }
        }
    }

    /**
     * Callback que se invoca cuando `MoodleApiClient` refresca el
     * token con éxito.
     *
     * ## Qué hace
     *
     * 1. Emite un evento `TokenRefreshed` para que la UI (si le
     *    interesa) se entere.
     * 2. Persiste el token nuevo en el `TokenStore`.
     *
     * @param refreshedToken Token nuevo.
     */
    private suspend fun persistRefreshedToken(refreshedToken: String) =
        persistMutex.withLock {
            _events.tryEmit(SessionEvent.TokenRefreshed(refreshedToken))
            persistIfSessionActive(refreshedToken)
        }

    /**
     * Callback que se invoca cuando `MoodleApiClient` no puede
     * refrescar el token.
     *
     * ## Qué hace
     *
     * Invalida la sesión, lo que emite un `SessionEvent.Expired` y
     * redirige al login.
     */
    private suspend fun handleInvalidToken() = invalidate()

    /**
     * Persiste el token refrescado si hay una sesión activa.
     *
     * ## Por qué comprueba la sesión
     *
     * Porque el refresco puede ocurrir justo cuando el usuario está
     * cerrando sesión. En ese caso, `_session.value` será `null` y no
     * hay que persistir nada.
     *
     * @param refreshedToken Token refrescado.
     */
    private suspend fun persistIfSessionActive(refreshedToken: String) {
        val provincia = _session.value?.context?.provincia ?: return
        val storedSession = tokenStore.load(provincia.slug) ?: return
        tokenStore.save(
            storedSession.copy(
                wstoken = refreshedToken,
                lastUsedAt = System.currentTimeMillis(),
            ),
        )
    }
}