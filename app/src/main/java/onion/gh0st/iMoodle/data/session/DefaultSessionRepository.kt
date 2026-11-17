package onion.gh0st.iMoodle.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
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
import onion.gh0st.iMoodle.domain.model.session.CasCredentials
import onion.gh0st.iMoodle.domain.model.auth.AuthException
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionFactory
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.api.moodle.network.MoodleSession
import onion.gh0st.iMoodle.api.moodle.repository.MoodleUserRepository
import onion.gh0st.iMoodle.api.moodle.repository.UserRepositoryFactory
import onion.gh0st.iMoodle.core.Logging
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
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
     * Identidad de la sesión viva actual.
     *
     * ## Qué problema resuelve
     *
     * Un cliente REST de una sesión anterior puede terminar una
     * petición **después** de un logout y un login nuevo. Sus
     * callbacks (`onTokenRefreshed`, `onInvalidToken`) no deben tocar
     * la sesión nueva: ni guardar su token, ni invalidarla.
     *
     * Cada `MoodleSession` recibe un objeto marcador único al crearse.
     * Los callbacks comparan ese marcador con el actual antes de
     * actuar. Es un objeto y no el `MoodleSession` porque los
     * callbacks se pasan a la fábrica **antes** de que la sesión
     * exista.
     *
     * Se marca `@Volatile` porque los callbacks pueden ejecutarse en
     * hilos de OkHttp/Dispatchers.IO.
     */
    @Volatile
    private var currentSessionMarker: Any? = null

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
     * El login, el auto-login, el logout y la invalidación. Garantiza
     * que no se solapen: si el usuario pulsa "Cerrar sesión" mientras
     * se está haciendo un login, el logout espera a que termine el
     * login y después limpia.
     *
     * ## Por qué se usa a través de [withSessionLock]
     *
     * Porque `Mutex` **no es reentrante**. Durante un login, el
     * cliente REST puede detectar un token inválido y llamar a
     * [invalidate] desde la misma operación. Si [invalidate] intentara
     * tomar el cerrojo otra vez, la operación se quedaría bloqueada
     * para siempre.
     */
    private val sessionMutex = Mutex()

    /**
     * Marca que viaja en el contexto de las corrutinas que ya tienen
     * el cerrojo de sesión tomado.
     *
     * ## Por qué es un elemento de contexto y no el `Job`
     *
     * Porque el refresco de token ocurre dentro de
     * `withContext(Dispatchers.IO)`, y `withContext` crea un `Job`
     * hijo distinto. Comparar `currentCoroutineContext()[Job]` con el
     * propietario del cerrojo fallaría justo en el caso que hay que
     * detectar: `invalidate()` llamado desde el cliente REST durante
     * un login, que se quedaría esperando un cerrojo que ya tiene su
     * propia operación.
     *
     * Los elementos de contexto, en cambio, **se heredan** por las
     * corrutinas hijas, incluidos los `withContext`. Así la
     * reentrada se detecta sin depender del hilo ni del `Job`.
     */
    private object SessionLockKey : CoroutineContext.Key<SessionLockElement>

    /** Elemento que marca una corrutina como propietaria del cerrojo. */
    private class SessionLockElement : AbstractCoroutineContextElement(SessionLockKey)

    /**
     * Realiza el login completo.
     *
     * ## Qué hace
     *
     * Delega en [performLogin], que hace todo el trabajo, bajo el
     * [sessionMutex]. Se envuelve en `runCatchingCancellable` para
     * convertir cualquier excepción en un `Result`, respetando la
     * cancelación de corrutinas.
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
    ): Result<ActiveSession> = runCatchingCancellable {
        withSessionLock { performLogin(provincia, credentials) }
    }

    /**
     * Intenta recuperar la sesión guardada.
     *
     * @return `Result` con la `ActiveSession` recuperada (o `null` si
     *   no hay), o con la excepción que haya ocurrido.
     */
    override suspend fun tryAutoLogin(): Result<ActiveSession?> = runCatchingCancellable {
        withSessionLock { performAutoLogin() }
    }

    /**
     * Cierra la sesión y limpia todo.
     *
     * ## Qué hace
     *
     * Toma el cerrojo de sesión y delega en [clearSessionData], que
     * limpia el estado interno, los almacenes y las alarmas.
     */
    override suspend fun logout() = withSessionLock { clearSessionData() }

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
     * ## Robustez
     *
     * El evento se emite **aunque falle la limpieza** de algún
     * almacén. Antes, si `authRepo.logout` o el borrado de la base de
     * datos lanzaban una excepción, la UI no se enteraba y el usuario
     * se quedaba en una pantalla rota sin sesión. El error se registra
     * en el log, pero no impide la redirección.
     *
     * ## Cuándo se llama
     *
     * - Cuando `MoodleApiClient` no puede refrescar el token.
     * - Cuando el usuario cambia la contraseña en otro sitio.
     */
    override suspend fun invalidate() {
        invalidateSession(expectedMarker = null)
    }

    /**
     * Invalida la sesión si sigue siendo la que inició la operación.
     *
     * ## Por qué recibe un marcador
     *
     * Porque el callback de un cliente REST antiguo puede llegar
     * después de un logout y un login nuevo. Sin la comprobación,
     * invalidaría la sesión del usuario nuevo. La comprobación se
     * hace **dentro** del cerrojo para que no haya una ventana entre
     * comprobar y limpiar.
     *
     * @param expectedMarker Marcador de la sesión que provocó la
     *   invalidación, o `null` para invalidar la sesión actual sin
     *   comprobar (logout manual, tests).
     */
    private suspend fun invalidateSession(expectedMarker: Any?) {
        withSessionLock {
            if (expectedMarker != null && currentSessionMarker !== expectedMarker) return@withSessionLock

            val sessionExisted = _session.value != null
            try {
                clearSessionData()
            } catch (cancellation: CancellationException) {
                if (sessionExisted) _events.tryEmit(SessionEvent.Expired)
                throw cancellation
            } catch (failure: Throwable) {
                Logging.logError("DefaultSessionRepository", "invalidate", failure)
            }
            if (sessionExisted) _events.tryEmit(SessionEvent.Expired)
        }
    }

    /**
     * Ejecuta [block] con el cerrojo de sesión tomado.
     *
     * ## Por qué no se usa `sessionMutex.withLock` directamente
     *
     * Porque `Mutex` no es reentrante. Una operación de login puede
     * acabar llamando a [invalidate] (si el token recién obtenido no
     * vale) desde la **misma corrutina**. Sin esta comprobación, esa
     * llamada intentaría tomar un cerrojo que ya tiene y se quedaría
     * esperando indefinidamente.
     *
     * Si la corrutina actual ya es la propietaria, el bloque se
     * ejecuta directamente. En cualquier otro caso se adquiere el
     * cerrojo normal, de modo que las operaciones de corrutinas
     * distintas siguen serializadas.
     *
     * @param block Operación que modifica el estado de la sesión.
     * @return El resultado de [block].
     */
    private suspend fun <T> withSessionLock(block: suspend () -> T): T {
        if (currentCoroutineContext()[SessionLockKey] != null) return block()

        return sessionMutex.withLock {
            withContext(SessionLockElement()) { block() }
        }
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
     * - Si falla, **mantiene el usuario anterior** y registra el error.
     *   Esto es importante para no dejar al usuario sin datos si la red
     *   falla. La cancelación de la corrutina sí se propaga.
     * - Si la sesión se cerró mientras llegaba la respuesta, **descarta
     *   el resultado**: no se debe resucitar una sesión ya cerrada ni
     *   volver a cachear a un usuario que acaba de salir.
     */
    override suspend fun refreshUser() {
        val moodleSession = _moodleSession.value ?: return
        val sessionBefore = _session.value ?: return

        runCatchingCancellable {
            val refreshedUser = loadUser(moodleSession)
            /*
             * Una respuesta tardía no debe pisar una sesión nueva. Si
             * el usuario cerró sesión y volvió a entrar mientras se
             * cargaba el perfil, `_moodleSession` ya es otra o
             * `_session` ha cambiado. `compareAndSet` solo aplica el
             * usuario si la sesión sigue siendo exactamente la misma
             * que inició la carga.
             */
            if (_moodleSession.value === moodleSession &&
                _session.compareAndSet(sessionBefore, sessionBefore.copy(user = refreshedUser))
            ) {
                userCache.set(refreshedUser)
            }
        }.onFailure { exception ->
            Logging.logError("DefaultSessionRepository", "refreshUser", exception)
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
     *
     * ## Robustez
     *
     * Cada paso se ejecuta de forma independiente: si uno falla (por
     * ejemplo, el borrado de la base de datos) **se intentan igualmente
     * todos los demás** y al final se relanza el primer error. Antes, el
     * primer fallo dejaba sin borrar las credenciales o los datos
     * personales. Además, todo el bloque es no cancelable: cerrar sesión
     * debe completarse aunque se destruya la pantalla que lo pidió.
     *
     * @throws Throwable el primer error ocurrido, si lo hubo.
     */
    private suspend fun clearSessionData() {
        withContext(NonCancellable) {
            /*
             * El cambio de estado se hace bajo `persistMutex` para que no
             * pueda intercalarse con `persistIfSessionActive`: si un
             * refresco comprueba que hay sesión y luego el logout la
             * borra, el token nuevo podría volver a guardarse en disco.
             * Con el cerrojo, o el refresco termina antes del borrado (y
             * el borrado limpia después), o ve la sesión ya cerrada y no
             * guarda nada.
             *
             * Todo el método es no cancelable, incluida esta espera: un
             * logout debe vaciar la sesión aunque se destruya la pantalla
             * que lo pidió.
             */
            val currentProvincia = persistMutex.withLock {
                val provincia = _session.value?.context?.provincia
                _session.value = null
                _moodleSession.value = null
                currentSessionMarker = null
                provincia
            }

            var firstFailure: Throwable? = null

            suspend fun runStep(stepName: String, step: suspend () -> Unit) {
                runCatchingCancellable { step() }.onFailure { failure ->
                    Logging.logError("DefaultSessionRepository", "clearSessionData:$stepName", failure)
                    if (firstFailure == null) firstFailure = failure
                }
            }

            runStep("authLogout") { if (currentProvincia != null) authRepo.logout(currentProvincia) }
            runStep("tokenStore") { tokenStore.clearAll() }
            runStep("credentialsStore") { credentialsStore.clear() }
            runStep("alarms") { notificationScheduler.cancelAll() }
            runStep("localData") { localDataCleaner.clear() }
            runStep("userCache") { userCache.clear() }

            firstFailure?.let { failure -> throw failure }
        }
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
     * 7. Si el token se refrescó mientras se cargaba el perfil,
     *    persiste el nuevo token. El callback de refresco no puede
     *    hacerlo antes porque todavía no hay sesión publicada.
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
        val sessionMarker = Any()
        val sessionContext = authRepo.login(
            provincia = provincia,
            credentials = credentials,
            onTokenRefreshed = { token -> persistRefreshedToken(sessionMarker, token) },
            onInvalidToken = { invalidateSession(sessionMarker) },
        )

        val moodleSession = sessionFactory.create(
            context = sessionContext,
            onTokenRefreshed = { token -> persistRefreshedToken(sessionMarker, token) },
            onInvalidToken = { invalidateSession(sessionMarker) },
        )

        val user = try {
            loadUser(moodleSession)
        } catch (exception: Exception) {
            withContext(NonCancellable) { authRepo.logout(provincia) }
            throw exception
        }
        val finalToken = moodleSession.api.token.value
        persistUserId(provincia, user.id)
        userCache.set(user)

        val activeSession = ActiveSession(
            context = sessionContext.copy(userId = user.id, wstoken = finalToken),
            user = user,
        )
        currentSessionMarker = sessionMarker
        _session.value = activeSession
        _moodleSession.value = moodleSession
        persistTokenIfChanged(sessionContext.wstoken, finalToken)
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
     * 8. Si el token se refrescó durante la carga del perfil,
     *    persiste el nuevo token para el próximo arranque.
     *
     * @return La `ActiveSession` recuperada, o `null` si no hay
     *   sesión válida.
     */
    private suspend fun performAutoLogin(): ActiveSession? {
        val sessionContext = authRepo.tryAutoLogin() ?: return null

        val sessionMarker = Any()
        val moodleSession = sessionFactory.create(
            context = sessionContext,
            onTokenRefreshed = { token -> persistRefreshedToken(sessionMarker, token) },
            onInvalidToken = { invalidateSession(sessionMarker) },
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
        val finalToken = moodleSession.api.token.value
        val activeSession = ActiveSession(
            context = sessionContext.copy(wstoken = finalToken),
            user = user,
        )
        currentSessionMarker = sessionMarker
        _session.value = activeSession
        _moodleSession.value = moodleSession
        persistTokenIfChanged(sessionContext.wstoken, finalToken)
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
     * 1. Comprueba que el callback pertenece a la sesión actual. Si no
     *    (el usuario cerró sesión y volvió a entrar), lo ignora.
     * 2. Emite un evento `TokenRefreshed` para que la UI (si le
     *    interesa) se entere.
     * 3. Persiste el token nuevo en el `TokenStore`.
     *
     * @param sessionMarker Marcador de la sesión que refrescó el
     *   token.
     * @param refreshedToken Token nuevo.
     */
    private suspend fun persistRefreshedToken(sessionMarker: Any, refreshedToken: String) =
        persistMutex.withLock {
            if (currentSessionMarker !== sessionMarker) return@withLock

            _events.tryEmit(SessionEvent.TokenRefreshed(refreshedToken))
            persistIfSessionActive(refreshedToken)
        }

    /**
     * Persiste el token nuevo si cambió durante la construcción de la
     * sesión.
     *
     * ## Por qué hace falta
     *
     * Los callbacks de refresco (`persistRefreshedToken`) solo guardan
     * el token si ya hay una sesión publicada. Durante un login o un
     * auto-login, el cliente REST puede refrescar el token **antes** de
     * que la sesión se publique. Sin este paso, en disco quedaría el
     * token viejo y el próximo arranque podría pedir un login manual
     * innecesario.
     *
     * ## Por qué es *best-effort*
     *
     * Porque un fallo de disco no debe convertir en fallido un login
     * que ya ha funcionado en memoria: se registra y se continúa.
     *
     * @param previousToken Token que ya estaba guardado.
     * @param newToken Token vigente al terminar la operación.
     */
    private suspend fun persistTokenIfChanged(previousToken: String, newToken: String) {
        if (previousToken == newToken) return

        runCatchingCancellable {
            persistMutex.withLock { persistIfSessionActive(newToken) }
        }.onFailure { failure ->
            Logging.logError("DefaultSessionRepository", "persistTokenIfChanged", failure)
        }
    }

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