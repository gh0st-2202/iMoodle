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
     * - **`null`** únicamente cuando se sabe **de forma definitiva**
     *   que no se puede refrescar: no hay credenciales guardadas o
     *   el servidor las ha rechazado. El cliente interpretará el
     *   `null` como "hace falta un login manual" y cerrará la sesión.
     *
     * ## Manejo de excepciones
     *
     * Los fallos **transitorios** (sin conexión, timeout, error 5xx
     * del servidor) **no deben devolverse como `null`**: se propagan
     * como excepción. Así el cliente se limita a fallar la petición
     * actual y el usuario conserva su sesión; basta con que reintente
     * cuando vuelva la conexión. Devolver `null` ante un simple corte
     * de red expulsaría al usuario y borraría sus datos locales.
     *
     * La excepción de cancelación de corrutinas se propaga siempre
     * tal cual.
     *
     * @return El token nuevo, o `null` si no se ha podido obtener.
     */
    suspend fun refresh(): String?
}