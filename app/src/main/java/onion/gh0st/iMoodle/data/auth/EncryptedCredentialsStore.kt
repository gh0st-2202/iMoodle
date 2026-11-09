@file:Suppress("DEPRECATION")

package onion.gh0st.iMoodle.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén cifrado de las credenciales del usuario de la Junta.
 *
 * ## ¿Qué guarda y por qué?
 * Guarda el usuario y la contraseña de la Junta de Andalucía. Los guarda cifrados
 * para que la app pueda pedir tokens nuevos sin molestar al usuario cada 30 minutos.
 *
 * ## Sobre el `@file:Suppress("DEPRECATION")`
 * La librería `androidx.security:security-crypto` (que proporciona `MasterKey` y
 * `EncryptedSharedPreferences`) ha sido marcada como obsoleta por Google. Sin embargo,
 * sigue siendo segura y funcional. Al poner el `@file:Suppress` al principio del archivo,
 * le decimos al compilador que ignore los avisos de esta librería en todo el fichero.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 */
@Singleton
class EncryptedCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialsStore {

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

    override suspend fun save(credentials: JuntaCredentials) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    override suspend fun load(): JuntaCredentials? = withContext(Dispatchers.IO) {
        val storedUsername = encryptedPreferences.getString(KEY_USERNAME, null)
            ?: return@withContext null
        val storedPassword = encryptedPreferences.getString(KEY_PASSWORD, null)
            ?: return@withContext null
        JuntaCredentials(username = storedUsername, password = storedPassword)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        encryptedPreferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_FILE_NAME = "imoodle_junta_credentials"
        const val KEYSET_PREFERENCES_FILE_NAME = "__androidx_security_crypto_encrypted_prefs__"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}