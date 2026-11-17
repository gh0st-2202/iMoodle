package onion.gh0st.iMoodle.api.moodle.auth

import onion.gh0st.iMoodle.domain.model.auth.AuthException
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.domain.model.session.CasCredentials
import onion.gh0st.iMoodle.api.moodle.network.TokenRefresher
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore

/**
 * Estrategia de refresco del token de Moodle basada en las
 * credenciales guardadas del usuario.
 *
 * ## Qué problema resuelve
 *
 * Los tokens de Moodle caducan al cabo de un tiempo (típicamente unos
 * 30 minutos). Cuando eso pasa, la app tiene dos opciones:
 *
 * 1. **Molesta al usuario**: cerrar la sesión y mandarlo a la
 *    pantalla de login para que vuelva a escribir su contraseña.
 *    Esto es lo que hacen muchas apps, pero es una experiencia
 *    horrible: el usuario está viendo sus tareas y de repente lo
 *    echas.
 * 2. **Refresca el token sin que el usuario se entere**: usar las
 *    credenciales guardadas para pedir un token nuevo de forma
 *    transparente. Esto es lo que hace esta clase.
 *
 * ## Cómo funciona
 *
 * Cuando `MoodleApiClient` detecta que el token ha caducado, llama a
 * [refresh]. Esta clase entonces:
 *
 * 1. Recupera las credenciales cifradas del usuario desde
 *    [credentialsStore].
 * 2. Si no hay credenciales guardadas, devuelve `null` (no se puede
 *    refrescar; el llamador forzará el re-login).
 * 3. Si las hay, ejecuta un login completo CAS + Moodle con ellas,
 *    como si el usuario acabara de escribir su contraseña.
 * 4. Si el login va bien, devuelve el token nuevo.
 * 5. Si el login falla porque el CAS rechaza explícitamente las
 *    credenciales (`401`/`403`, `AuthException.LoginFailed`),
 *    **borra las credenciales guardadas** para no intentar el login
 *    en bucle y devuelve `null`.
 * 6. Si el login falla por un problema transitorio (timeout, sin
 *    conexión, error 5xx de la Junta, un `NoMoodleSession` que puede
 *    deberse a un fallo de Moodle), **no borra** las credenciales
 *    (no tienen la culpa) y **propaga la excepción**. La petición
 *    actual falla, pero la sesión se conserva y el usuario puede
 *    reintentar cuando recupere la conexión.
 *
 * ## Por qué guardamos la contraseña
 *
 * Esta es la parte delicada: para poder refrescar el token sin
 * molestar al usuario, necesitamos su contraseña. La guardamos
 * cifrada con el Android Keystore (`EncryptedCredentialsStore`). Es
 * un compromiso consciente entre seguridad y usabilidad. Si te
 * preocupa, puedes no guardarla, pero entonces tendrás que pedir la
 * contraseña cada media hora.
 *
 * @param provincia Provincia a la que pertenece la sesión actual.
 *   Determina a qué instancia de Moodle hay que pedir el token.
 * @param credentialsStore Almacén cifrado de credenciales de la
 *   Junta.
 * @param httpClient Cliente HTTP base compartido por toda la app.
 *   Se clona internamente para el login, reutilizando el pool de
 *   conexiones.
 */
class CasCredentialsTokenRefresher(
    private val provincia: Provincia,
    private val credentialsStore: CredentialsStore,
    private val httpClient: OkHttpClient,
) : TokenRefresher {

    /**
     * Intenta obtener un token nuevo de Moodle usando las
     * credenciales guardadas del usuario.
     *
     * ## Cómo se ejecuta
     *
     * 1. Recupera las credenciales del almacén cifrado. Si no hay,
     *    devuelve `null` inmediatamente (no hay nada que hacer).
     * 2. Crea un `MoodleAuth` configurado para la provincia y le
     *    pide que ejecute el login completo con esas credenciales.
     * 3. Según el resultado:
     *    - **Login OK**: devuelve el `wstoken` de la sesión
     *      resultante.
     *    - **Credenciales rechazadas** (`LoginFailed`, un `401`/`403`
     *      del CAS): borra las credenciales guardadas y devuelve
     *      `null`. Así no se reintenta un login que sabemos que va a
     *      fallar indefinidamente.
     *    - **Fallo transitorio** (timeout, sin conexión, error 5xx
     *      de la Junta, `429`, página del CAS inesperada o
     *      `NoMoodleSession`): lanza [AuthException] **sin** borrar
     *      las credenciales. El problema es temporal y no debe
     *      costar la sesión.
     *    - **Cancelación** de la corrutina: se propaga la excepción
     *      de cancelación.
     *
     * ## Qué ve el llamador
     *
     * `MoodleApiClient` interpreta el `null` como "no se puede
     * refrescar" y cierra la sesión, forzando el re-login manual (la
     * UI recibe un `SessionEvent.Expired`). Una excepción, en cambio,
     * solo hace fallar la petición en curso.
     *
     * @return El token nuevo, o `null` si las credenciales faltan o
     *   han sido rechazadas.
     * @throws AuthException ante fallos transitorios de red o de
     *   servidor.
     */
    override suspend fun refresh(): String? {
        val credentials = credentialsStore.load() ?: return null

        return try {
            val moodleAuth = MoodleAuth.forProvincia(provincia, httpClient)
            val moodleSession = moodleAuth.login(
                CasCredentials(credentials.username, credentials.password),
            )
            moodleSession.wstoken
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AuthException.LoginFailed) {
            credentialsStore.clear()
            null
        } catch (exception: AuthException) {
            throw exception
        } catch (exception: Exception) {
            throw AuthException.Network(exception)
        }
    }
}
