package onion.gh0st.iMoodle.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import onion.gh0st.iMoodle.domain.model.session.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caché en memoria del usuario actualmente logueado.
 *
 * ## Qué guarda
 *
 * El objeto `User` del usuario que tiene la sesión activa en este
 * momento: nombre, apellidos, email, avatar y (si se ha consultado)
 * los datos personales de Pasen.
 *
 * ## Por qué existe
 *
 * El `User` se necesita en varias pantallas: el perfil, el menú, la
 * cabecera, etc. Sin una caché, cada pantalla tendría que pedirlo a
 * Moodle al abrirse, lo que supondría peticiones redundantes y
 * parpadeos.
 *
 * Esta caché es un `@Singleton`, así que vive mientras viva el
 * proceso de la app. Sobrevive a la recreación de ViewModels y
 * Fragments (por ejemplo, al rotar la pantalla). Se limpia al hacer
 * logout.
 *
 * ## Por qué es un `StateFlow`
 *
 * Porque hay componentes que quieren **observar** el usuario y
 * reaccionar a sus cambios. Por ejemplo, el perfil puede suscribirse
 * a `userCache.user` y actualizar la UI automáticamente cuando el
 * usuario se enriquece con datos de Pasen.
 *
 * ## Qué NO guarda
 *
 * - **No persiste en disco**: al reiniciar la app, la caché se
 *   pierde. El `User` se vuelve a cargar desde Moodle (o desde
 *   `StoredSession` si el auto-login tiene éxito).
 * - **No guarda la sesión completa**: solo el `User`. La sesión
 *   (con el token y las cookies) la gestiona
 *   `DefaultSessionRepository`.
 * - **No guarda credenciales**: eso es cosa de
 *   `EncryptedCredentialsStore`.
 */
@Singleton
class UserCache @Inject constructor() {

    /** Estado interno del usuario cacheado. */
    private val _user = MutableStateFlow<User?>(null)

    /**
     * Usuario cacheado, o `null` si aún no se ha cargado.
     *
     * ## Por qué es `val` público pero `_user` privado
     *
     * `_user` es la fuente de verdad, mutable. `user` es la vista
     * pública, de solo lectura. Así nadie de fuera puede modificar
     * el `User` sin pasar por [set] o [clear], lo que mantiene el
     * estado controlado.
     */
    val user: StateFlow<User?> = _user.asStateFlow()

    /**
     * Devuelve el usuario cacheado, o `null` si no hay.
     *
     * ## Diferencia con [user]
     *
     * [user] es un `StateFlow` que se puede **observar** (reacciona a
     * cambios). [get] es una lectura puntual: devuelve el valor
     * actual y ya. Es más cómodo cuando solo se quiere consultar el
     * valor una vez.
     *
     * @return El usuario cacheado, o `null`.
     */
    fun get(): User? = _user.value

    /**
     * Sobrescribe el usuario cacheado.
     *
     * ## Cuándo se llama
     *
     * - Al hacer login (con el `User` recién cargado de Moodle).
     * - Al hacer auto-login (con el `User` recuperado o
     *   reconstruido).
     * - Al enriquecer con datos de Pasen (con el `User` actualizado).
     *
     * @param user Usuario a cachear.
     */
    fun set(user: User) {
        _user.value = user
    }

    /**
     * Limpia la caché.
     *
     * ## Cuándo se llama
     *
     * - Al hacer logout.
     * - Al invalidar la sesión (token caducado sin refresco).
     * - Al cambiar de cuenta.
     *
     * ## Por qué es importante
     *
     * Si no se limpiara, el siguiente usuario que entrara en el
     * dispositivo podría ver momentáneamente el `User` del anterior
     * hasta que se cargara el suyo. Es un problema de privacidad y de
     * coherencia.
     */
    fun clear() {
        _user.value = null
    }
}