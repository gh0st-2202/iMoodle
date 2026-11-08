package onion.gh0st.iMoodle.domain.repository.session

import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials

/**
 * Contrato de persistencia de las credenciales de la Junta.
 *
 * ## Qué guarda
 *
 * El par **usuario y contraseña** de la Junta de Andalucía. Lo
 * guarda de forma **cifrada** en el dispositivo (la implementación
 * real usa el Android Keystore).
 *
 * ## Por qué se guarda una contraseña
 *
 * Porque los tokens de la Junta caducan rápido (unos 30 minutos), y
 * sin las credenciales habría que pedirle la contraseña al usuario
 * cada media hora. Guardándolas, la app puede refrescar los tokens
 * de forma transparente, sin molestar al usuario.
 *
 * ## Seguridad
 *
 * - **Cifrado**: la contraseña se cifra con una clave que vive en el
 *   Android Keystore. Sin esa clave, el fichero es ilegible.
 * - **Borrado al logout**: cuando el usuario cierra sesión, las
 *   credenciales se borran.
 * - **Borrado al fallo**: si la Junta rechaza las credenciales
 *   (contraseña cambiada, cuenta bloqueada), también se borran para
 *   no reintentar en bucle.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `EncryptedCredentialsStore` (con Keystore).
 * - **Tests**: `FakeCredentialsStore` (en memoria).
 *
 * ## Quién la consume
 *
 * - `AuthRepositoryImpl` (al hacer login, para guardar).
 * - `DefaultSessionRepository` (al hacer logout, para borrar).
 * - `CasCredentialsTokenRefresher` (al refrescar el token de Moodle).
 * - `PasenScheduleRepository` y `PasenUserProfileRepository` (al
 *   hacer login a Pasen).
 */
interface CredentialsStore {

    /**
     * Guarda o sobreescribe las credenciales.
     *
     * ## Qué pasa si ya había credenciales
     *
     * Se sobrescriben. Esto es lo correcto: el usuario ha vuelto a
     * iniciar sesión, así que las nuevas son las que valen.
     *
     * @param credentials Credenciales a persistir.
     */
    suspend fun save(credentials: JuntaCredentials)

    /**
     * Recupera las credenciales guardadas.
     *
     * @return Las credenciales, o `null` si no hay ninguna guardada.
     */
    suspend fun load(): JuntaCredentials?

    /**
     * Borra las credenciales guardadas.
     *
     * ## Cuándo se llama
     *
     * - Al hacer logout.
     * - Cuando la Junta rechaza las credenciales (contraseña
     *   cambiada).
     */
    suspend fun clear()
}