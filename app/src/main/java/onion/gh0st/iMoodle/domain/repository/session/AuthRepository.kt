package onion.gh0st.iMoodle.domain.repository.session

import onion.gh0st.iMoodle.api.cas.CasCredentials
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.model.session.SessionContext

/**
 * Contrato de acceso de bajo nivel a la autenticación CAS + Moodle.
 *
 * ## Qué es un "contrato de bajo nivel"
 *
 * Este contrato describe las operaciones **mínimas** para
 * autenticarse: hacer login, intentar recuperar una sesión guardada,
 * y cerrar sesión. No se preocupa del estado reactivo, ni de la UI,
 * ni de la gestión del ciclo de vida completo. Solo de la
 * autenticación.
 *
 * La gestión completa del ciclo de vida (estado reactivo, eventos,
 * cancelación de alarmas, etc.) la hace `SessionRepository`, que usa
 * este contrato internamente.
 *
 * ## Qué devuelve
 *
 * **Solo datos puros** (`SessionContext`). La reconstrucción de la
 * sesión "viva" (con cliente HTTP y cookies) es responsabilidad de
 * otras capas (`MoodleSessionFactory` + `DefaultSessionRepository`).
 * Este contrato no sabe nada de infraestructura.
 *
 * ## Excepción arquitectónica
 *
 * Este contrato importa `CasCredentials` desde `api/cas/`. Es la
 * única excepción a la regla "`domain/` no depende de `api/`", y está
 * documentada como aceptable: `CasCredentials` es un modelo de datos
 * puro (usuario + contraseña) sin ninguna dependencia de
 * infraestructura. Si en el futuro se quisiera eliminar la
 * excepción, se movería `CasCredentials` a `domain/`.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `AuthRepositoryImpl` (hace el login real).
 * - **Tests**: `FakeAuthRepository` (o `mockk` en los tests
 *   existentes).
 *
 * ## Quién la consume
 *
 * - `DefaultSessionRepository`.
 */
interface AuthRepository {

    /**
     * Realiza el login completo contra CAS + Moodle y persiste la
     * sesión.
     *
     * ## Qué hace
     *
     * 1. Hace login contra el CAS (identificación de la Junta).
     * 2. Obtiene el token de Moodle usando las cookies del CAS.
     * 3. Persiste la sesión en disco (cifrada).
     * 4. Persiste las credenciales en disco (cifradas) para el
     *    refresco silencioso.
     *
     * ## Qué devuelve
     *
     * Un `SessionContext` con los datos puros de la sesión. La sesión
     * "viva" la construye después `DefaultSessionRepository` con
     * `MoodleSessionFactory`.
     *
     * ## Manejo de errores
     *
     * Si el login falla, la excepción sube tal cual. El
     * `LoginViewModel` la traduce a un mensaje para el usuario.
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param credentials Credenciales del usuario.
     * @param onTokenRefreshed Callback cuando Moodle emite un token
     *   nuevo de forma transparente.
     * @param onInvalidToken Callback cuando el token caduca y no se
     *   puede refrescar.
     * @return La sesión autenticada.
     * @throws onion.gh0st.iMoodle.api.moodle.auth.AuthException si el
     *   login falla.
     */
    suspend fun login(
        provincia: Provincia,
        credentials: CasCredentials,
        onTokenRefreshed: suspend (String) -> Unit = {},
        onInvalidToken: suspend () -> Unit = {},
    ): SessionContext

    /**
     * Intenta recuperar la sesión guardada más reciente.
     *
     * ## Qué hace
     *
     * 1. Lee la sesión más reciente del disco.
     * 2. Comprueba si su token sigue siendo válido haciendo una
     *    petición ligera a Moodle.
     * 3. Según el resultado:
     *    - **Válido**: devuelve el `SessionContext`.
     *    - **Inválido**: borra la sesión y devuelve `null`.
     *    - **No comprobable** (sin red): devuelve el `SessionContext`
     *      asumiendo que puede seguir siendo válido.
     *
     * ## Cuándo se llama
     *
     * Al arrancar la app, para decidir si hay que mostrar el login o
     * entrar directamente.
     *
     * @param onInvalidToken Callback si el token se descubre inválido
     *   durante la comprobación.
     * @return La sesión recuperada, o `null` si no hay ninguna válida.
     */
    suspend fun tryAutoLogin(
        onInvalidToken: suspend () -> Unit = {},
    ): SessionContext?

    /**
     * Borra la sesión guardada de una provincia.
     *
     * ## Qué NO hace
     *
     * No borra las credenciales guardadas. Eso lo hace
     * `DefaultSessionRepository` cuando corresponde.
     *
     * @param provincia Provincia cuya sesión hay que olvidar.
     */
    suspend fun logout(provincia: Provincia)
}