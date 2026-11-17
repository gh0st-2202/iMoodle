package onion.gh0st.iMoodle.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.domain.model.session.JuntaCredentials
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén cifrado de las credenciales de la Junta (usuario y contraseña).
 *
 * ## Qué guarda y para qué
 *
 * Guarda el usuario y la contraseña de IdEA **cifrados** con una clave
 * del Android Keystore (ver [KeystoreCipher] y [SecureValueStore]). Se
 * necesitan para renovar el token de Moodle en silencio cuando caduca,
 * sin pedirte la contraseña cada media hora.
 *
 * ## Qué pasa si no se puede leer
 *
 * Si el Keystore ha perdido la clave (por ejemplo, tras restaurar una
 * copia de seguridad en otro teléfono) [load] devuelve `null`, igual que
 * si nunca se hubieran guardado: la app pedirá iniciar sesión de nuevo.
 *
 * ## Migración
 *
 * Esta clase sustituye a la versión basada en `EncryptedSharedPreferences`
 * (obsoleta). Los datos antiguos se descartan al arrancar, por lo que
 * hay que iniciar sesión una vez tras actualizar.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 * @param cipher Cifrador respaldado por el Keystore.
 */
@Singleton
class EncryptedCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
    cipher: KeystoreCipher,
) : CredentialsStore {

    private val store = SecureValueStore(context, PREFERENCES_FILE_NAME, cipher)

    /**
     * Guarda las credenciales, sustituyendo las anteriores.
     *
     * @param credentials Usuario y contraseña de la Junta.
     */
    override suspend fun save(credentials: JuntaCredentials) = withContext(Dispatchers.IO) {
        store.putAll(
            mapOf(
                KEY_USERNAME to credentials.username,
                KEY_PASSWORD to credentials.password,
            ),
        )
    }

    /**
     * Recupera las credenciales guardadas.
     *
     * @return Las credenciales, o `null` si no hay o no se pueden descifrar.
     */
    override suspend fun load(): JuntaCredentials? = withContext(Dispatchers.IO) {
        val storedUsername = store.getString(KEY_USERNAME) ?: return@withContext null
        val storedPassword = store.getString(KEY_PASSWORD) ?: return@withContext null
        JuntaCredentials(username = storedUsername, password = storedPassword)
    }

    /** Borra las credenciales guardadas. */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        store.clear()
    }

    private companion object {
        /** Fichero de preferencias (distinto del antiguo para no mezclar formatos). */
        const val PREFERENCES_FILE_NAME = "imoodle_credentials_v2"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
