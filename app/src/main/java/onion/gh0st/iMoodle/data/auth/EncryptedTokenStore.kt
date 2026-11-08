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
 * Almacén cifrado de las sesiones de Moodle guardadas en el
 * dispositivo.
 *
 * ## Qué guarda y por qué
 *
 * Guarda, para cada provincia, la **sesión de Moodle** del usuario:
 * el nombre de usuario, el token de la API REST (`wstoken`), la fecha
 * del último uso y el ID interno del usuario. Lo hace de forma
 * **cifrada**.
 *
 * La idea es que, al abrir la app, el usuario no tenga que volver a
 * escribir su contraseña. La app lee la última sesión guardada, la
 * valida contra Moodle y, si sigue siendo válida, entra directamente.
 *
 * ## Qué NO guarda
 *
 * **No guarda la contraseña**. Solo el `wstoken`, que es un token
 * revocable y de duración limitada. Si alguien lo robara, el daño
 * sería mucho menor que si robara la contraseña: bastaría con
 * revocarlo (o esperar a que caduque) y el atacante no podría hacer
 * nada.
 *
 * La contraseña se guarda en otro almacén (`EncryptedCredentialsStore`)
 * y solo se usa para el refresco silencioso de tokens.
 *
 * ## Cómo se cifran
 *
 * Igual que `EncryptedCredentialsStore`: **Android Keystore** +
 * **EncryptedSharedPreferences**. La clave maestra vive en una zona
 * protegida del dispositivo y nunca se expone a la app. Cada valor se
 * cifra antes de escribirse en disco.
 *
 * ## Soporte multi-provincia
 *
 * La app permite al usuario tener sesiones en varias provincias a la
 * vez (por ejemplo, si estudia en Córdoba pero hace un curso en
 * Sevilla). Para soportarlo, las claves se prefijan con el slug de la
 * provincia:
 *
 * - `cordoba.user`
 * - `cordoba.wstoken`
 * - `cordoba.lastUsedAt`
 * - `cordoba.userId`
 * - `sevilla.user`
 * - `sevilla.wstoken`
 * - ...
 *
 * Así cada provincia tiene su "espacio" independiente dentro del
 * mismo fichero.
 *
 * ## Cuál es la sesión "más reciente"
 *
 * Cuando el usuario abre la app, esta clase busca la sesión con el
 * `lastUsedAt` más alto. Es decir, la última que se usó en este
 * dispositivo. Si el usuario solo ha entrado en Córdoba, esa será la
 * única. Si ha entrado en varias, la última que usó.
 *
 * ## Recuperación ante ficheros ilegibles
 *
 * Igual que en `EncryptedCredentialsStore`: si el fichero cifrado se
 * corrompe o la clave del Keystore desaparece, esta clase lo detecta,
 * lo borra y empieza de cero. El usuario tendrá que volver a
 * loguearse, pero la app no crashea.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TokenStore {

    /** Preferencias cifradas, listas para leer y escribir. */
    private val encryptedPreferences: SharedPreferences = createPrefs(context)

    /**
     * Crea las preferencias cifradas con recuperación ante ficheros
     * ilegibles.
     *
     * @param context Contexto de aplicación.
     * @return Las preferencias cifradas.
     */
    private fun createPrefs(context: Context): SharedPreferences =
        try {
            buildEncryptedPreferences(context)
        } catch (exception: Exception) {
            deletePreferencesFiles(context)
            buildEncryptedPreferences(context)
        }

    /**
     * Construye las preferencias cifradas usando el Keystore.
     *
     * @param context Contexto de aplicación.
     * @return Las preferencias cifradas.
     * @throws Exception si el fichero existe pero no se puede
     *   descifrar (capturado en [createPrefs]).
     */
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

    /**
     * Borra los ficheros de preferencias cifradas del disco.
     *
     * @param context Contexto de aplicación.
     */
    private fun deletePreferencesFiles(context: Context) {
        val sharedPreferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        listOf(PREFERENCES_FILE_NAME, KEYSET_PREFERENCES_FILE_NAME).forEach { preferencesName ->
            File(sharedPreferencesDirectory, "$preferencesName.xml").delete()
            File(sharedPreferencesDirectory, "$preferencesName.xml.bak").delete()
        }
    }

    /**
     * Guarda la sesión cifrada en disco.
     *
     * ## Qué hace
     *
     * Escribe cuatro claves (con el slug de la provincia como
     * prefijo):
     *
     * - `user`: nombre de usuario de Moodle.
     * - `wstoken`: token de la API REST.
     * - `lastUsedAt`: timestamp del último uso.
     * - `userId`: ID interno del usuario en Moodle.
     *
     * Todos los valores se cifran antes de escribirse.
     *
     * ## Cuándo se llama
     *
     * - Tras un login exitoso (para guardar la sesión).
     * - Tras un refresco de token exitoso (para actualizar el
     *   token guardado).
     * - Tras un auto-login que descubre el `userId` (para
     *   persistirlo).
     *
     * @param session Sesión a persistir.
     */
    override suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(preferenceKey(session.provinciaSlug, KEY_USER), session.user)
            .putString(preferenceKey(session.provinciaSlug, KEY_WSTOKEN), session.wstoken)
            .putLong(preferenceKey(session.provinciaSlug, KEY_LAST_USED_AT), session.lastUsedAt)
            .putInt(preferenceKey(session.provinciaSlug, KEY_USER_ID), session.userId)
            .apply()
    }

    /**
     * Carga la sesión de una provincia concreta.
     *
     * @param provinciaSlug Slug de la provincia.
     * @return La sesión persistida, o `null` si no existe.
     */
    override suspend fun load(provinciaSlug: String): StoredSession? =
        withContext(Dispatchers.IO) { loadSync(provinciaSlug) }

    /**
     * Devuelve la sesión usada más recientemente.
     *
     * ## Cómo lo calcula
     *
     * Lee todas las claves que terminan en `.user` (una por
     * provincia), carga la sesión de cada una y elige la que tenga
     * `lastUsedAt` más alto.
     *
     * ## Cuándo se llama
     *
     * Al arrancar la app, para decidir si hay que hacer auto-login.
     *
     * @return La sesión más reciente, o `null` si no hay ninguna.
     */
    override suspend fun mostRecent(): StoredSession? =
        withContext(Dispatchers.IO) { peekMostRecentSync() }

    /**
     * Elimina la sesión de una provincia.
     *
     * ## Qué hace
     *
     * Borra las cuatro claves asociadas a esa provincia. Las claves
     * de otras provincias se quedan intactas.
     *
     * ## Cuándo se llama
     *
     * Al hacer logout de una provincia concreta.
     *
     * @param provinciaSlug Slug de la provincia a eliminar.
     */
    override suspend fun clear(provinciaSlug: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .remove(preferenceKey(provinciaSlug, KEY_USER))
            .remove(preferenceKey(provinciaSlug, KEY_WSTOKEN))
            .remove(preferenceKey(provinciaSlug, KEY_LAST_USED_AT))
            .remove(preferenceKey(provinciaSlug, KEY_USER_ID))
            .apply()
    }

    /**
     * Elimina todas las sesiones persistidas.
     *
     * ## Cuándo se llama
     *
     * Al hacer logout global (por ejemplo, desde el perfil, donde no
     * se elige provincia: se cierra la sesión de la que haya).
     */
    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        encryptedPreferences.edit().clear().apply()
    }

    /**
     * Variante **síncrona** de [mostRecent] para usar en contextos
     * donde no se puede suspender.
     *
     * ## Por qué existe
     *
     * Hay sitios donde no se puede llamar a una función `suspend`
     * porque el código no está dentro de una corrutina. Por ejemplo,
     * `MainActivity.onCreate` a veces necesita saber si hay sesión
     * guardada antes de montar la UI.
     *
     * ## Por qué es rápido
     *
     * Las `SharedPreferences` ya están cargadas en memoria tras la
     * primera lectura. Acceder a ellas es prácticamente instantáneo
     * (milisegundos). Por eso es aceptable hacerlo en el hilo
     * principal para una decisión puntual.
     *
     * @return La sesión más reciente, o `null` si no hay ninguna.
     */
    fun peekMostRecentSync(): StoredSession? = encryptedPreferences.all.keys
        .filter { preferenceKey -> preferenceKey.endsWith(".$KEY_USER") }
        .mapNotNull { preferenceKey ->
            loadSync(preferenceKey.removeSuffix(".$KEY_USER"))
        }
        .maxByOrNull { storedSession -> storedSession.lastUsedAt }

    /**
     * Lee la sesión de una provincia desde las preferencias ya
     * cargadas.
     *
     * ## Cuándo devuelve `null`
     *
     * - Si falta el usuario o el token (datos mínimos sin los que la
     *   sesión no sirve).
     * - Si los campos opcionales (`lastUsedAt`, `userId`) no están,
     *   se rellenan con 0.
     *
     * @param provinciaSlug Slug de la provincia.
     * @return La sesión, o `null` si faltan datos mínimos.
     */
    private fun loadSync(provinciaSlug: String): StoredSession? {
        val storedUser = encryptedPreferences.getString(
            preferenceKey(provinciaSlug, KEY_USER),
            null,
        ) ?: return null
        val storedToken = encryptedPreferences.getString(
            preferenceKey(provinciaSlug, KEY_WSTOKEN),
            null,
        ) ?: return null
        return StoredSession(
            provinciaSlug = provinciaSlug,
            user = storedUser,
            wstoken = storedToken,
            lastUsedAt = encryptedPreferences.getLong(
                preferenceKey(provinciaSlug, KEY_LAST_USED_AT),
                0L,
            ),
            userId = encryptedPreferences.getInt(
                preferenceKey(provinciaSlug, KEY_USER_ID),
                0,
            ),
        )
    }

    /**
     * Compone la clave interna de una propiedad de una provincia.
     *
     * ## Qué hace
     *
     * Junta el slug de la provincia con el nombre de la propiedad,
     * separándolos por un punto. Por ejemplo:
     *
     * ```
     * preferenceKey("cordoba", "user") → "cordoba.user"
     * ```
     *
     * ## Por qué se usa un punto como separador
     *
     * Porque los slugs de provincia no contienen puntos (son letras
     * minúsculas sin tildes), así que no hay riesgo de colisión. El
     * punto hace las claves legibles al inspeccionarlas.
     *
     * @param provinciaSlug Slug de la provincia.
     * @param fieldName Nombre de la propiedad.
     * @return La clave completa.
     */
    private fun preferenceKey(provinciaSlug: String, fieldName: String) =
        "$provinciaSlug.$fieldName"

    private companion object {
        /**
         * Nombre del fichero de preferencias cifradas.
         *
         * Distinto del de credenciales (`imoodle_junta_credentials`)
         * para separar dominios: los tokens son menos sensibles que
         * las contraseñas y se gestionan de forma independiente.
         */
        const val PREFERENCES_FILE_NAME = "imoodle_auth_secure"

        /**
         * Nombre del fichero donde `security-crypto` guarda su
         * "keyset".
         */
        const val KEYSET_PREFERENCES_FILE_NAME =
            "__androidx_security_crypto_encrypted_prefs__"

        /** Sufijo de la clave del usuario de Moodle. */
        const val KEY_USER = "user"

        /** Sufijo de la clave del token de Moodle. */
        const val KEY_WSTOKEN = "wstoken"

        /** Sufijo de la clave del timestamp del último uso. */
        const val KEY_LAST_USED_AT = "lastUsedAt"

        /** Sufijo de la clave del ID interno del usuario en Moodle. */
        const val KEY_USER_ID = "userId"
    }
}