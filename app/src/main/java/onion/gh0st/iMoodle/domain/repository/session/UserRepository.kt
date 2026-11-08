package onion.gh0st.iMoodle.domain.repository.session

import onion.gh0st.iMoodle.domain.model.session.User

/**
 * Contrato de acceso al usuario logueado.
 *
 * ## Qué hace
 *
 * Obtiene los datos del usuario desde Moodle (nombre, email, avatar).
 * Es la fuente primaria de identidad del usuario: los datos de Pasen
 * se añaden después con `UserProfileRepository`.
 *
 * ## Caché
 *
 * Los métodos aceptan un parámetro `forceRefresh` para que la UI
 * pueda decidir si usar la caché o pedir datos frescos:
 *
 * - **`forceRefresh = false`**: si hay caché, se usa. Es lo normal
 *   al abrir el perfil (los datos no cambian a cada segundo).
 * - **`forceRefresh = true`**: se ignora la caché y se piden datos
 *   frescos a Moodle. Es lo normal al hacer login (queremos el
 *   perfil actualizado) o al hacer pull-to-refresh.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `MoodleUserRepository` (con caché).
 * - **Tests**: `FakeUserRepository` (devuelve datos controlados).
 *
 * ## Quién la consume
 *
 * - `UserRepositoryFactory` (crea una instancia por sesión).
 * - `DefaultSessionRepository` (al cargar el perfil tras login o
 *   auto-login).
 * - `GetCurrentUser` (caso de uso, indirectamente).
 */
interface UserRepository {

    /**
     * Devuelve el usuario actual.
     *
     * ## Comportamiento
     *
     * - Si `forceRefresh = false` y hay caché, la usa.
     * - En caso contrario, consulta la API y actualiza la caché.
     *
     * @param forceRefresh Si `true`, ignora la caché.
     * @return El usuario cargado.
     */
    suspend fun getUser(forceRefresh: Boolean = false): User
}