package onion.gh0st.iMoodle.api.moodle.network

/**
 * Estrategia de refresco del token de la API REST de Moodle.
 *
 * ## Qué es un `TokenRefresher`
 *
 * Es una pieza muy pequeña con una única responsabilidad: **pedir un
 * token nuevo a Moodle**. No sabe cómo se usa el token, ni cuándo
 * caduca, ni qué hacer si falla. Solo sabe obtener uno.
 *
 * ## Por qué existe como interfaz separada
 *
 * Porque hay varias formas de obtener un token de Moodle y queremos
 * poder intercambiarlas sin tocar el resto de la app:
 *
 * - **En producción**: se usa `CookiesBasedTokenRefresher`, que
 *   reutiliza las cookies del CAS para pedir un token nuevo sin
 *   volver a pedir credenciales.
 * - **En tests**: se puede sustituir por una implementación trivial
 *   que devuelve un token fijo, o por una lambda que simula un fallo.
 *
 * Si el `MoodleApiClient` tuviera la lógica de refresco cableada
 * dentro, sería imposible testearlo sin hacer peticiones de red
 * reales. Con esta interfaz, se le puede inyectar cualquier
 * implementación.
 *
 * ## Por qué es una `fun interface`
 *
 * Kotlin permite que una interfaz con **un solo método** se pueda
 * implementar con una lambda. Eso hace que en tests sea muy cómodo
 * escribir:
 *
 * ```
 * val refresher = TokenRefresher { "nuevo-token" }
 * ```
 *
 * en lugar de tener que crear una clase anónima entera.
 *
 * ## Contrato de hilos
 *
 * El `MoodleApiClient` garantiza que `refresh()` **nunca** se llama
 * dos veces en paralelo para el mismo token caducado. Serializa las
 * llamadas con un `Mutex`. Por eso las implementaciones **no
 * necesitan ser thread-safe**: no van a recibir llamadas concurrentes.
 */
fun interface TokenRefresher {

    /**
     * Intenta obtener un token nuevo de Moodle.
     *
     * ## Cuándo se llama
     *
     * El `MoodleApiClient` invoca este método cuando una petición a
     * Moodle devuelve un error de token inválido. En lugar de
     * fallar inmediatamente y echar al usuario, intenta refrescar el
     * token y reintentar la petición original.
     *
     * ## Qué debe devolver
     *
     * - **El token nuevo** si se ha podido obtener. El cliente
     *   actualizará su token interno, notificará al llamador y
     *   reintentará la petición original.
     * - **`null`** si no se ha podido obtener. Puede ser porque las
     *   credenciales ya no valen, porque las cookies del CAS han
     *   caducado, porque no hay conexión, etc. El cliente
     *   interpretará el `null` como "no se puede refrescar" y
     *   cerrará la sesión.
     *
     * ## Manejo de excepciones
     *
     * La implementación **no debe lanzar excepciones** salvo la
     * excepción de cancelación de corrutinas (que se propaga tal
     * cual). Cualquier otro problema debe devolverse como `null`.
     * Así el `MoodleApiClient` tiene un único camino para gestionar
     * el fallo.
     *
     * @return El token nuevo, o `null` si no se ha podido obtener.
     */
    suspend fun refresh(): String?
}