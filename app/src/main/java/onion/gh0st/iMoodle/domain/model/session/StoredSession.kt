package onion.gh0st.iMoodle.domain.model.session

/**
 * Representación persistible de una sesión de Moodle.
 *
 * ## Qué es esto
 *
 * Es lo que se guarda en disco (cifrado) para poder recuperar la
 * sesión al volver a abrir la app. Es similar a `SessionContext`,
 * pero con diferencias:
 *
 * - Añade `lastUsedAt`: un timestamp que indica cuándo se usó la
 *   sesión por última vez. Sirve para ordenar las sesiones y elegir
 *   la más reciente al arrancar la app.
 * - `provincia` es un `String` (el slug) en lugar de un enum
 *   `Provincia`. Esto es porque el enum es un detalle de dominio que
 *   se puede reconstruir a partir del slug, y así lo que se persiste
 *   es solo el dato estable.
 *
 * ## Qué NO contiene
 *
 * **No contiene la contraseña**. Solo el `wstoken`, que es un token
 * revocable y de duración limitada. Si alguien accediera al fichero
 * de la sesión guardada, no podría usarlo para autenticarse de
 * nuevo: solo tendría un token que caduca y que se puede revocar.
 *
 * La contraseña se guarda en otro almacén (`EncryptedCredentialsStore`),
 * separada de las sesiones.
 *
 * ## Quién lo usa
 *
 * - `EncryptedTokenStore` lo persiste y lo recupera.
 * - `AuthRepositoryImpl` lo crea a partir de un `SessionContext` y lo
 *   lee para reconstruir un `SessionContext`.
 *
 * @property provinciaSlug Slug de la provincia a la que pertenece la
 *   sesión.
 * @property user Nombre de usuario de Moodle.
 * @property wstoken Token de la API REST de Moodle.
 * @property lastUsedAt Timestamp del último uso, en milisegundos.
 *   Sirve para elegir la sesión más reciente cuando hay varias.
 * @property userId ID interno de Moodle del usuario, o `0` si aún no
 *   se conoce. Se guarda para poder abrir la app sin red y consultar
 *   los datos cacheados en Room (que están indexados por usuario).
 */
data class StoredSession(
    val provinciaSlug: String,
    val user: String,
    val wstoken: String,
    val lastUsedAt: Long,
    val userId: Int = 0,
)