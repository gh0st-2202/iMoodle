package onion.gh0st.iMoodle.domain.model.session

/**
 * Datos puros de una sesión autenticada contra Moodle.
 *
 * ## Qué es un "contexto de sesión"
 *
 * Es la representación mínima de una sesión, **sin infraestructura**.
 * Contiene solo los datos que el dominio necesita saber:
 *
 * - **`provincia`**: contra qué instancia de Moodle se ha
 *   autenticado el usuario.
 * - **`user`**: el nombre de usuario de Moodle.
 * - **`wstoken`**: el token de la API REST de Moodle.
 * - **`userId`**: el ID interno del usuario en Moodle (`0` si no se
 *   conoce aún).
 *
 * ## Qué NO contiene
 *
 * **No contiene `OkHttpClient`, `CookieManager` ni `MoodleApiClient`**.
 * Esos son detalles de infraestructura que el dominio no debe
 * conocer. Si los contuviera, el dominio dependería de OkHttp, y
 * cualquier cambio en OkHttp afectaría al dominio.
 *
 * La representación "viva" de la sesión (con cliente HTTP, cookies y
 * API REST) es `MoodleSession`, que vive en `api/` y se construye a
 * partir de este `SessionContext` con `MoodleSessionFactory`.
 *
 * ## Cuándo se crea
 *
 * - Al hacer login: `AuthRepositoryImpl.login` crea uno tras el
 *   login exitoso.
 * - Al hacer auto-login: `AuthRepositoryImpl.tryAutoLogin` crea uno
 *   tras recuperar la sesión guardada.
 *
 * ## Quién lo usa
 *
 * - `DefaultSessionRepository` lo usa para construir la
 *   `ActiveSession` y para pedir a `MoodleSessionFactory` que
 *   construya la `MoodleSession`.
 * - `ActiveSession` lo contiene.
 * - La UI lo lee a través de `ActiveSession` para saber en qué
 *   provincia está y qué token tiene.
 *
 * @property provincia Provincia de la que se obtuvo el token.
 * @property user Nombre de usuario de Moodle.
 * @property wstoken Token de la API REST de Moodle.
 * @property userId ID interno del usuario en Moodle. `0` si aún no
 *   se conoce (por ejemplo, en auto-login offline con una sesión
 *   antigua que no lo persistió).
 */
data class SessionContext(
    val provincia: Provincia,
    val user: String,
    val wstoken: String,
    val userId: Int = 0,
)