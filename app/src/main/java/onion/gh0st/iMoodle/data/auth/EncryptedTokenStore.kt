package onion.gh0st.iMoodle.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén cifrado de las sesiones de Moodle guardadas.
 *
 * ## Qué guarda
 *
 * Por cada provincia con sesión iniciada: el usuario, el token de Moodle
 * (`wstoken`), el identificador numérico de usuario y cuándo se usó por
 * última vez. Así no hay que iniciar sesión cada vez que se abre la app.
 *
 * Todos los valores se cifran con una clave del Android Keystore (ver
 * [KeystoreCipher] y [SecureValueStore]). Las claves del fichero tienen
 * la forma `<provincia>.<campo>` (por ejemplo `sevilla.wstoken`).
 *
 * ## Migración
 *
 * Sustituye a la versión basada en `EncryptedSharedPreferences`
 * (obsoleta). Los datos antiguos se descartan al arrancar, por lo que
 * hay que iniciar sesión una vez tras actualizar.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 * @param cipher Cifrador respaldado por el Keystore.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
    cipher: KeystoreCipher,
) : TokenStore {

    private val store = SecureValueStore(context, PREFERENCES_FILE_NAME, cipher)

    /**
     * Guarda (o sustituye) la sesión de una provincia de forma atómica.
     *
     * @param session Sesión a guardar.
     */
    override suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        store.putAll(
            mapOf(
                preferenceKey(session.provinciaSlug, KEY_USER) to session.user,
                preferenceKey(session.provinciaSlug, KEY_WSTOKEN) to session.wstoken,
                preferenceKey(session.provinciaSlug, KEY_LAST_USED_AT) to session.lastUsedAt.toString(),
                preferenceKey(session.provinciaSlug, KEY_USER_ID) to session.userId.toString(),
            ),
        )
    }

    /**
     * Recupera la sesión guardada de una provincia.
     *
     * @param provinciaSlug Identificador de la provincia.
     * @return La sesión, o `null` si no hay o no se puede descifrar.
     */
    override suspend fun load(provinciaSlug: String): StoredSession? =
        withContext(Dispatchers.IO) { loadSync(provinciaSlug) }

    /**
     * Recupera la sesión usada más recientemente, de cualquier provincia.
     *
     * @return La sesión más reciente, o `null` si no hay ninguna.
     */
    override suspend fun mostRecent(): StoredSession? =
        withContext(Dispatchers.IO) { findMostRecent() }

    /**
     * Borra la sesión guardada de una provincia.
     *
     * @param provinciaSlug Identificador de la provincia.
     */
    override suspend fun clear(provinciaSlug: String) = withContext(Dispatchers.IO) {
        store.remove(
            listOf(KEY_USER, KEY_WSTOKEN, KEY_LAST_USED_AT, KEY_USER_ID)
                .map { fieldName -> preferenceKey(provinciaSlug, fieldName) },
        )
    }

    /** Borra todas las sesiones guardadas, de todas las provincias. */
    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        store.clear()
    }

    /**
     * Busca entre todas las provincias guardadas la usada más
     * recientemente.
     *
     * @return La sesión con `lastUsedAt` más alto, o `null` si no hay ninguna.
     */
    private fun findMostRecent(): StoredSession? = store.keys()
        .filter { preferenceKey -> preferenceKey.endsWith(".$KEY_USER") }
        .mapNotNull { preferenceKey -> loadSync(preferenceKey.removeSuffix(".$KEY_USER")) }
        .maxByOrNull { storedSession -> storedSession.lastUsedAt }

    /**
     * Lee una sesión de forma síncrona (hay que llamar desde un hilo de E/S).
     *
     * @param provinciaSlug Identificador de la provincia.
     * @return La sesión, o `null` si falta el usuario o el token.
     */
    private fun loadSync(provinciaSlug: String): StoredSession? {
        val storedUser = store.getString(preferenceKey(provinciaSlug, KEY_USER)) ?: return null
        val storedToken = store.getString(preferenceKey(provinciaSlug, KEY_WSTOKEN)) ?: return null
        return StoredSession(
            provinciaSlug = provinciaSlug,
            user = storedUser,
            wstoken = storedToken,
            lastUsedAt = store.getLong(preferenceKey(provinciaSlug, KEY_LAST_USED_AT), 0L),
            userId = store.getInt(preferenceKey(provinciaSlug, KEY_USER_ID), 0),
        )
    }

    /**
     * Compone la clave de un campo de una provincia.
     *
     * @param provinciaSlug Identificador de la provincia.
     * @param fieldName Nombre del campo.
     * @return La clave, con la forma `<provincia>.<campo>`.
     */
    private fun preferenceKey(provinciaSlug: String, fieldName: String) = "$provinciaSlug.$fieldName"

    private companion object {
        /** Fichero de preferencias (distinto del antiguo para no mezclar formatos). */
        const val PREFERENCES_FILE_NAME = "imoodle_tokens_v2"
        const val KEY_USER = "user"
        const val KEY_WSTOKEN = "wstoken"
        const val KEY_LAST_USED_AT = "lastUsedAt"
        const val KEY_USER_ID = "userId"
    }
}
