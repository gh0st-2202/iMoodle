package onion.gh0st.iMoodle.api.moodle.repository

import onion.gh0st.iMoodle.api.moodle.network.MoodleApiClient
import onion.gh0st.iMoodle.domain.repository.session.UserRepository

/**
 * Fábrica de [UserRepository] a partir de un [MoodleApiClient].
 *
 * ## Qué problema resuelve
 *
 * `MoodleUserRepository` necesita el `MoodleApiClient` de la sesión
 * activa para hacer peticiones. Pero el `MoodleApiClient` no existe
 * hasta que el usuario se ha logueado (o se ha recuperado una sesión
 * guardada). Por eso no se puede inyectar directamente en el
 * constructor de `MoodleUserRepository` en tiempo de compilación.
 *
 * Esta fábrica es la que crea el repositorio **en el momento
 * adecuado**, cuando ya hay un `MoodleApiClient` disponible.
 *
 * ## Quién la usa
 *
 * `DefaultSessionRepository`. Tras un login o un auto-login, cuando
 * ya tiene el `MoodleApiClient`, llama a `create(api)` para obtener un
 * `UserRepository` con el que cargar el perfil del usuario.
 *
 * ## Por qué es una `fun interface`
 *
 * Kotlin permite que una interfaz con **un solo método** se pueda
 * implementar con una lambda. Eso hace que en tests sea muy cómodo
 * escribir:
 *
 * ```
 * val factory = UserRepositoryFactory { api -> FakeUserRepository(...) }
 * ```
 *
 * en lugar de tener que crear una clase anónima entera.
 */
fun interface UserRepositoryFactory {

    /**
     * Crea el repositorio de usuario para el cliente REST dado.
     *
     * ## Qué debe devolver
     *
     * Un `UserRepository` listo para pedir el usuario actual. En
     * producción, siempre `MoodleUserRepository(api, cache)`. En
     * tests, un fake.
     *
     * ## Cuándo se llama
     *
     * - Justo después de un login exitoso.
     * - Justo después de recuperar una sesión guardada.
     *
     * @param api Cliente REST de la sesión activa.
     * @return El repositorio listo para pedir usuarios.
     */
    fun create(api: MoodleApiClient): UserRepository
}