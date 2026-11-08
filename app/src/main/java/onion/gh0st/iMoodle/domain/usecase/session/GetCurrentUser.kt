package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener los datos del usuario actualmente logueado.
 *
 * ## Qué hace
 *
 * Devuelve el `User` de la sesión activa, opcionalmente refrescándolo
 * desde la red. Es el punto de entrada del dominio para obtener
 * información del usuario.
 *
 * ## Contrato
 *
 * - **`null` significa "no hay sesión activa"**. No es un error: es
 *   un estado válido (el usuario no está logueado).
 * - **`forceRefresh = true`**: delega en
 *   `SessionRepository.refreshUser` para pedir datos frescos a
 *   Moodle. El repositorio es el único que conoce el cliente REST.
 * - **Si el refresco falla**, se mantiene el usuario anterior (el
 *   repositorio no borra el estado). La UI no ve excepciones aquí.
 *
 * ## Por qué es un caso de uso y no una lectura directa de
 * `SessionRepository.session.value`
 *
 * Porque el `refreshUser` es una operación de negocio (pedir datos
 * frescos) que tiene efectos secundarios (una petición de red). Un
 * ViewModel no debería tener que saber si el refresco es síncrono o
 * asíncrono, si falla o no, etc. Con este caso de uso, solo llama a
 * `GetCurrentUser(forceRefresh)` y obtiene el resultado.
 *
 * ## Sin dependencias de `api`
 *
 * Este caso de uso no toca `UserRepositoryFactory` ni
 * `MoodleApiClient`. Solo depende de `SessionRepository`, que a su
 * vez delega en las capas de infraestructura. El dominio queda
 * limpio.
 *
 * ## Quién lo usa
 *
 * - `ProfileViewModel` (al cargar el perfil).
 * - Cualquier pantalla que necesite mostrar información del usuario.
 *
 * @param sessionRepo Fuente de la sesión activa.
 */
class GetCurrentUser @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Ejecuta el caso de uso.
     *
     * ## Qué hace paso a paso
     *
     * 1. Si `forceRefresh = true`, pide a la sesión que refresque el
     *    usuario desde la red.
     * 2. Lee el `User` actual de la sesión.
     * 3. Lo devuelve, o `null` si no hay sesión.
     *
     * ## Manejo de errores
     *
     * No lanza excepciones. Si el refresco falla, el repositorio
     * mantiene el usuario anterior y este caso de uso devuelve el
     * usuario (posiblemente obsoleto). Es una decisión de diseño:
     * para el usuario es mejor ver datos un poco antiguos que ver un
     * error.
     *
     * @param forceRefresh Si `true`, pide datos frescos a la red
     *   antes de devolver el usuario.
     * @return El `User` actual, o `null` si no hay sesión.
     */
    suspend operator fun invoke(forceRefresh: Boolean = false): User? {
        if (forceRefresh) sessionRepo.refreshUser()
        return sessionRepo.session.value?.user
    }
}