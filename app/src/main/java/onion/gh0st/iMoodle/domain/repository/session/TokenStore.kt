package onion.gh0st.iMoodle.domain.repository.session

import onion.gh0st.iMoodle.domain.model.session.StoredSession

/**
 * Contrato de persistencia de las sesiones de Moodle.
 *
 * ## Qué guarda
 *
 * Las **sesiones de Moodle** (usuario + token + fecha + userId),
 * indexadas por provincia. Lo hace de forma **cifrada** en el
 * dispositivo (la implementación real usa el Android Keystore).
 *
 * ## Qué NO guarda
 *
 * **No guarda la contraseña**. Solo el token de Moodle, que es
 * revocable y de duración limitada. La contraseña se guarda en otro
 * almacén (`CredentialsStore`), separada por dominios.
 *
 * ## Por qué indexa por provincia
 *
 * La app permite tener sesiones en varias provincias a la vez (por
 * ejemplo, si el alumno estudia en una y hace un curso en otra). Cada
 * provincia tiene su propia entrada. Al arrancar, se usa la sesión
 * con `lastUsedAt` más alto.
 *
 * ## Seguridad
 *
 * - **Cifrado**: el token se cifra con una clave que vive en el
 *   Android Keystore.
 * - **Borrado al logout**: cuando el usuario cierra sesión, todas
 *   las sesiones se borran.
 * - **Revocable**: si el token se filtra, se puede revocar (o
 *   esperar a que caduque). La contraseña no: por eso no se guarda
 *   aquí.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `EncryptedTokenStore` (con Keystore).
 * - **Tests**: `FakeTokenStore` (en memoria).
 *
 * ## Quién la consume
 *
 * - `AuthRepositoryImpl` (al hacer login y auto-login).
 * - `DefaultSessionRepository` (al hacer logout y al refrescar el
 *   token).
 */
interface TokenStore {

    /**
     * Guarda o sobreescribe la sesión de una provincia.
     *
     * ## Qué pasa si ya había una sesión para esa provincia
     *
     * Se sobrescribe. Esto es lo correcto: el usuario ha vuelto a
     * iniciar sesión, así que la sesión nueva es la que vale.
     *
     * @param session Sesión a persistir.
     */
    suspend fun save(session: StoredSession)

    /**
     * Recupera la sesión de una provincia.
     *
     * @param provinciaSlug Slug de la provincia.
     * @return La sesión guardada, o `null` si no existe.
     */
    suspend fun load(provinciaSlug: String): StoredSession?

    /**
     * Devuelve la sesión más reciente de cualquier provincia.
     *
     * ## Cómo lo calcula
     *
     * Ordena las sesiones por `lastUsedAt` descendente y devuelve la
     * primera. Es decir, la que se usó por última vez en este
     * dispositivo.
     *
     * ## Cuándo se llama
     *
     * Al arrancar la app, para decidir si hacer auto-login.
     *
     * @return La sesión más reciente, o `null` si no hay ninguna.
     */
    suspend fun mostRecent(): StoredSession?

    /**
     * Borra la sesión de una provincia.
     *
     * ## Qué pasa si no existe
     *
     * No falla. Es una operación idempotente: borrar algo que no
     * existe es un no-op.
     *
     * @param provinciaSlug Slug de la provincia a eliminar.
     */
    suspend fun clear(provinciaSlug: String)

    /**
     * Borra todas las sesiones persistidas.
     *
     * ## Cuándo se llama
     *
     * Al hacer logout global. A diferencia de [clear], que borra
     * una sola provincia, este borra todas de golpe.
     */
    suspend fun clearAll()
}