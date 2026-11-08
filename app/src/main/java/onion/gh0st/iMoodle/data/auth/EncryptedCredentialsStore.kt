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
 * ## Qué guarda y por qué
 *
 * Guarda el **usuario y la contraseña** de la Junta de Andalucía en el
 * dispositivo, de forma **cifrada**. A primera vista puede parecer
 * peligroso guardar una contraseña, así que conviene explicar por qué
 * se hace:
 *
 * Los servicios de la Junta (Moodle, Pasen) emiten tokens de sesión
 * que caducan al cabo de un rato. Cuando un token caduca, hay dos
 * opciones:
 *
 * 1. **Molestar al usuario**: cerrar la sesión y mandarlo a la
 *    pantalla de login para que vuelva a escribir su contraseña.
 *    Esto es lo que hacen muchas apps, pero es una experiencia
 *    horrible: el usuario está viendo sus tareas y de repente lo
 *    echas.
 * 2. **Refrescar el token sin que el usuario se entere**: usar las
 *    credenciales guardadas para pedir un token nuevo de forma
 *    transparente.
 *
 * Esta app implementa la opción 2. Para ello necesita las
 * credenciales guardadas en algún sitio. Y ese "algún sitio" es este
 * almacén cifrado.
 *
 * ## Cómo se cifran
 *
 * Se usa la librería **Jetpack Security** de Google, que combina dos
 * piezas:
 *
 * 1. **Android Keystore**: un almacén de claves que el sistema
 *    operativo guarda en una zona protegida del dispositivo. Las
 *    claves **nunca salen de ahí**: ni la app ni el usuario pueden
 *    leerlas directamente. Solo se pueden usar para cifrar y
 *    descifrar.
 * 2. **EncryptedSharedPreferences**: una versión cifrada de las
 *    `SharedPreferences` (el sistema de pares clave-valor de
 *    Android). Cada valor se cifra con una clave derivada del
 *    Keystore antes de escribirse en disco.
 *
 * El resultado: si alguien accede al fichero de preferencias (por
 * ejemplo, con un móvil rooteado), solo verá datos binarios
 * ilegibles. Sin la clave del Keystore, no puede descifrarlos.
 *
 * ## Qué NO es una garantía absoluta
 *
 * Hay que ser honesto: guardar la contraseña, aunque sea cifrada, es
 * un compromiso. Si el móvil se pierde y está desbloqueado, o si un
 * atacante tiene acceso root y las claves del Keystore, podría
 * descifrarla. Pero:
 *
 * 1. La alternativa (pedir la contraseña cada 30 minutos) es
 *    inaceptable para el usuario.
 * 2. Las claves del Keystore están protegidas por hardware en la
 *    mayoría de dispositivos modernos, lo que hace muy difícil
 *    extraerlas.
 * 3. Las credenciales se borran al hacer logout.
 *
 * ## Separación de otros almacenes
 *
 * Este almacén guarda **credenciales** (usuario y contraseña). Es
 * distinto de:
 *
 * - **`EncryptedTokenStore`**: guarda tokens de sesión (que son
 *   revocables y no comprometen la contraseña).
 * - **`UserProfileCache`**: guarda datos del perfil de Pasen (centro,
 *   edad, etc.), que no son sensibles.
 *
 * Cada uno tiene su propio fichero de preferencias, para separar
 * dominios y facilitar la gestión.
 *
 * ## Recuperación ante ficheros ilegibles
 *
 * Los ficheros cifrados pueden volverse ilegibles por varios motivos:
 * restaurar una copia de seguridad en otro móvil (donde la clave del
 * Keystore no existe), invalidación de la clave por parte del
 * sistema, corrupción del fichero, etc. Si eso pasa, intentar crear
 * las preferencias lanza una excepción y la app crashearía en cada
 * arranque.
 *
 * Para evitarlo, esta clase captura esa excepción, **borra los
 * ficheros corruptos** y vuelve a crear las preferencias vacías. El
 * usuario solo tendrá que volver a iniciar sesión: es un precio
 * aceptable a cambio de no tener una app que no arranca.
 *
 * @param context Contexto de aplicación inyectado por Hilt. Se usa
 *   para acceder al sistema de ficheros y al Keystore.
 */
@Singleton
class EncryptedCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialsStore {

    /**
     * Preferencias cifradas, listas para leer y escribir.
     *
     * Se construyen una sola vez al crear la instancia (porque la
     * clase es `@Singleton`). Si la creación falla por fichero
     * corrupto, [createPrefs] se encarga de recuperarse.
     */
    private val encryptedPreferences: SharedPreferences = createPrefs(context)

    /**
     * Crea las preferencias cifradas con recuperación ante ficheros
     * ilegibles.
     *
     * ## Qué hace
     *
     * Intenta crear las preferencias. Si falla (porque el fichero
     * está corrupto o la clave del Keystore ya no existe), borra los
     * ficheros y lo intenta de nuevo.
     *
     * ## Por qué es importante
     *
     * Sin esta recuperación, la app crashearía en cada arranque si el
     * fichero estuviera corrupto. Es una situación poco frecuente,
     * pero muy visible para el usuario cuando ocurre.
     *
     * @param context Contexto de aplicación.
     * @return Las preferencias cifradas, listas para leer y escribir.
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
     * ## Qué hace
     *
     * 1. Crea (o recupera) una clave maestra en el Keystore. Esta
     *    clave es la raíz de toda la seguridad: con ella se derivan
     *    las claves que cifran los valores.
     * 2. Crea las `EncryptedSharedPreferences` con dos esquemas de
     *    cifrado:
     *    - **Claves** (`AES256_SIV`): cifra los nombres de las
     *      claves de preferencias.
     *    - **Valores** (`AES256_GCM`): cifra los valores.
     *
     * ## Por qué cifrar también las claves y no solo los valores
     *
     * Porque el nombre de la clave puede filtrar información. Por
     * ejemplo, si tuviéramos claves como `password_admin`,
     * `password_user`, etc., un atacante podría deducir la estructura
     * de la app. Cifrando también las claves, esa información queda
     * oculta.
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
     * ## Cuándo se llama
     *
     * Solo cuando [buildEncryptedPreferences] falla: el fichero está
     * corrupto o la clave del Keystore ya no existe. En ese caso, la
     * única salida es borrar y empezar de cero.
     *
     * ## Qué borra
     *
     * - El fichero de preferencias propiamente dicho.
     * - El fichero de backup (`.bak`) que Android puede haber creado.
     * - El fichero de "keyset" que usa `security-crypto`
     *   internamente para guardar la clave de cifrado de las claves.
     *
     * ## Por qué borra también el `.bak`
     *
     * Porque Android, al escribir preferencias, crea un fichero
     * temporal `.bak` que luego renombra. Si el fichero principal
     * está corrupto pero el `.bak` no, al borrar solo el principal el
     * sistema podría restaurar el `.bak` y volveríamos a tener el
     * problema. Borrando ambos, nos aseguramos de empezar limpios.
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
     * Guarda las credenciales cifradas en disco.
     *
     * ## Qué hace
     *
     * Escribe dos claves en las preferencias cifradas:
     *
     * - `username`: el usuario de la Junta.
     * - `password`: la contraseña.
     *
     * Ambos valores se cifran automáticamente antes de escribirse.
     *
     * ## Por qué se ejecuta en `Dispatchers.IO`
     *
     * Porque escribir en disco puede tardar (aunque sea poco). Hacerlo
     * en el hilo principal podría causar "jank" (pequeños tirones en
     * la UI). `Dispatchers.IO` está pensado precisamente para
     * operaciones de entrada/salida.
     *
     * ## Qué pasa si ya había credenciales guardadas
     *
     * Se sobrescriben. Esto es lo correcto: el usuario ha vuelto a
     * iniciar sesión, así que las credenciales nuevas son las que
     * valen.
     *
     * @param credentials Credenciales a persistir.
     */
    override suspend fun save(credentials: JuntaCredentials) = withContext(Dispatchers.IO) {
        encryptedPreferences.edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    /**
     * Carga las credenciales guardadas.
     *
     * ## Qué hace
     *
     * Lee las dos claves (`username` y `password`) de las preferencias
     * cifradas y, si ambas existen, devuelve un `JuntaCredentials`.
     *
     * ## Cuándo devuelve `null`
     *
     * - Cuando no hay credenciales guardadas (primer arranque, tras
     *   logout, etc.).
     * - Cuando solo hay una de las dos claves (situación anómala:
     *   quizá se corrompió el fichero a medias). Es preferible
     *   devolver `null` y forzar un re-login que devolver un objeto
     *   con la contraseña vacía.
     *
     * @return Las credenciales, o `null` si no hay ninguna guardada.
     */
    override suspend fun load(): JuntaCredentials? = withContext(Dispatchers.IO) {
        val storedUsername = encryptedPreferences.getString(KEY_USERNAME, null)
            ?: return@withContext null
        val storedPassword = encryptedPreferences.getString(KEY_PASSWORD, null)
            ?: return@withContext null
        JuntaCredentials(username = storedUsername, password = storedPassword)
    }

    /**
     * Borra las credenciales guardadas.
     *
     * ## Qué hace
     *
     * Limpia **todas** las claves de las preferencias cifradas. Como
     * este fichero solo contiene las credenciales, limpiar todo es
     * equivalente a borrarlas.
     *
     * ## Cuándo se llama
     *
     * - Al hacer logout (por decisión del usuario).
     * - Cuando la Junta rechaza las credenciales (contraseña
     *   cambiada, cuenta bloqueada). En ese caso, el
     *   `CasCredentialsTokenRefresher` las borra para no intentar
     *   logins en bucle con credenciales inválidas.
     */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        encryptedPreferences.edit().clear().apply()
    }

    private companion object {
        /**
         * Nombre del fichero de preferencias cifradas.
         *
         * Se elige un nombre descriptivo (`imoodle_junta_credentials`)
         * para que, al inspeccionar el sistema de ficheros, quede
         * claro qué contiene.
         */
        const val PREFERENCES_FILE_NAME = "imoodle_junta_credentials"

        /**
         * Nombre del fichero donde `security-crypto` guarda su
         * "keyset" (la clave de cifrado de las claves).
         *
         * Este nombre lo impone la librería, no se puede cambiar.
         */
        const val KEYSET_PREFERENCES_FILE_NAME =
            "__androidx_security_crypto_encrypted_prefs__"

        /** Clave del usuario de la Junta. */
        const val KEY_USERNAME = "username"

        /** Clave de la contraseña de la Junta. */
        const val KEY_PASSWORD = "password"
    }
}