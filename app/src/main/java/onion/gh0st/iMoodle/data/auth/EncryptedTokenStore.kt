@file:Suppress("DEPRECATION")

package onion.gh0st.iMoodle.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.session.StoredSession
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén cifrado de las sesiones de Moodle guardadas.
 *
 * ## ¿Qué guarda?
 * Guarda el token de Moodle (`wstoken`) y tu nombre de usuario para que no tengas
 * que volver a iniciar sesión cada vez que abras la app.
 *
 * ## Sobre el `@file:Suppress("DEPRECATION")`
 * Igual que en `EncryptedCredentialsStore`, silenciamos los avisos de la librería
 * `security-crypto` de Google, que está obsoleta pero sigue siendo la forma segura
 * de hacer esto en Android.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {

    private val encryptedPreferences: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences =
        try {
            buildEncryptedPreferences(context)
        } catch (exception: Exception) {
            deletePreferencesFiles(context)
            buildEncryptedPreferences(context)
        }

    private fun buildEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun deletePreferencesFiles(context: Context) {
        val sharedPreferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(PREFERENCES_FILE_NAME, KEYSET_PREFERENCES_FILE_NAME).forEach { preferencesName ->
            File(sharedPreferencesDirectory, "$preferencesName.xml").delete()
            File(sharedPreferencesDirectory, "$preferencesName.xml.bak").delete()
        }
    }

    override suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(preferenceKey(session.provinciaSlug, KEY_USER), session.user)
            .putString(preferenceKey(session.provinciaSlug, KEY_WSTOKEN), session.wstoken)
            .putLong(preferenceKey(session.provinciaSlug, KEY_LAST_USED_AT), session.lastUsedAt)
            .putInt(preferenceKey(session.provinciaSlug, KEY_USER_ID), session.userId)
            .apply()
    }

    override suspend fun load(provinciaSlug: String): StoredSession? =
        withContext(Dispatchers.IO) { loadSync(provinciaSlug) }

    override suspend fun mostRecent(): StoredSession? =
        withContext(Dispatchers.IO) { peekMostRecentSync() }

    override suspend fun clear(provinciaSlug: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .remove(preferenceKey(provinciaSlug, KEY_USER))
            .remove(preferenceKey(provinciaSlug, KEY_WSTOKEN))
            .remove(preferenceKey(provinciaSlug, KEY_LAST_USED_AT))
            .remove(preferenceKey(provinciaSlug, KEY_USER_ID))
            .apply()
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        encryptedPreferences.edit().clear().apply()
    }

    fun peekMostRecentSync(): StoredSession? = encryptedPreferences.all.keys
        .filter { preferenceKey -> preferenceKey.endsWith(".$KEY_USER") }
        .mapNotNull { preferenceKey ->
            loadSync(preferenceKey.removeSuffix(".$KEY_USER"))
        }
        .maxByOrNull { storedSession -> storedSession.lastUsedAt }

    private fun loadSync(provinciaSlug: String): StoredSession? {
        val storedUser = encryptedPreferences.getString(preferenceKey(provinciaSlug, KEY_USER), null) ?: return null
        val storedToken = encryptedPreferences.getString(preferenceKey(provinciaSlug, KEY_WSTOKEN), null) ?: return null
        return StoredSession(
            provinciaSlug = provinciaSlug,
            user = storedUser,
            wstoken = storedToken,
            lastUsedAt = encryptedPreferences.getLong(preferenceKey(provinciaSlug, KEY_LAST_USED_AT), 0L),
            userId = encryptedPreferences.getInt(preferenceKey(provinciaSlug, KEY_USER_ID), 0),
        )
    }

    private fun preferenceKey(provinciaSlug: String, fieldName: String) = "$provinciaSlug.$fieldName"

    private companion object {
        const val PREFERENCES_FILE_NAME = "imoodle_auth_secure"
        const val KEYSET_PREFERENCES_FILE_NAME = "__androidx_security_crypto_encrypted_prefs__"
        const val KEY_USER = "user"
        const val KEY_WSTOKEN = "wstoken"
        const val KEY_LAST_USED_AT = "lastUsedAt"
        const val KEY_USER_ID = "userId"
    }
}