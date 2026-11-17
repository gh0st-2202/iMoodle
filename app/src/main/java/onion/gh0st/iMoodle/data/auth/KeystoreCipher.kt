package onion.gh0st.iMoodle.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifra y descifra textos con una clave **AES-256-GCM guardada en el
 * Android Keystore**.
 *
 * ## Por qué existe
 *
 * La app guarda la contraseña de la Junta y los tokens de Moodle en el
 * teléfono. Hasta ahora lo hacía con `EncryptedSharedPreferences`, de la
 * librería `androidx.security:security-crypto`, que Google ha marcado
 * como **obsoleta por completo** y recomienda sustituir por el uso
 * directo del Keystore. Esta clase es ese uso directo: unas 100 líneas
 * sin dependencias externas.
 *
 * ## Cómo funciona
 *
 * - La **clave** se genera dentro del Keystore del sistema la primera
 *   vez que hace falta y **nunca sale de él**: ni la app ni un volcado
 *   del almacenamiento pueden leerla; solo se le puede pedir "cifra
 *   esto" o "descifra esto".
 * - Cada cifrado usa un **IV aleatorio** de 12 bytes. El resultado se
 *   guarda como `Base64(IV + texto cifrado)`.
 * - GCM es un modo *autenticado*: si alguien manipula el texto cifrado,
 *   el descifrado falla en lugar de devolver basura.
 *
 * ## Qué pasa si la clave se pierde
 *
 * Puede ocurrir tras restaurar una copia de seguridad en otro teléfono
 * o si el sistema invalida el Keystore. Entonces [decrypt] devuelve
 * `null` (como si no hubiera nada guardado) y el usuario simplemente
 * tendrá que iniciar sesión de nuevo. La app **no** se cae.
 *
 * ## Un único alias compartido
 *
 * Todos los almacenes seguros usan la misma clave ([KEY_ALIAS]). Antes,
 * cada almacén intentaba «repararse» borrando ficheros que en realidad
 * compartía con el otro, lo que podía destruir los datos del vecino.
 * Con una sola clave y sin autoborrados cruzados ese fallo desaparece.
 */
@Singleton
class KeystoreCipher @Inject constructor() {

    @Volatile
    private var cachedKey: SecretKey? = null

    /**
     * Cifra un texto.
     *
     * Si el Keystore indica que la clave actual ha quedado
     * **permanentemente** invalidada (o no se puede recuperar), la
     * elimina, genera una nueva y reintenta una vez. Los datos
     * antiguos serán ilegibles, pero eso ya ocurría con la clave rota.
     *
     * ## Por qué no se borra ante cualquier error
     *
     * Porque todos los almacenes seguros comparten el mismo alias.
     * Un fallo transitorio del Keystore (por ejemplo, un
     * `KeyStoreException` pasajero) no justifica destruir la clave y
     * dejar ilegibles los tokens y las credenciales de todos los
     * almacenes. Solo se borra cuando el error demuestra que la clave
     * ya no sirve.
     *
     * @param plainText Texto en claro.
     * @return `Base64(IV + texto cifrado)`.
     * @throws GeneralSecurityException si el Keystore no funciona ni con una clave nueva.
     */
    fun encrypt(plainText: String): String =
        try {
            encryptWith(obtainKey(), plainText)
        } catch (exception: KeyPermanentlyInvalidatedException) {
            deleteKey()
            encryptWith(obtainKey(), plainText)
        } catch (exception: UnrecoverableKeyException) {
            deleteKey()
            encryptWith(obtainKey(), plainText)
        }

    /**
     * Descifra un texto producido por [encrypt].
     *
     * @param encoded Valor `Base64(IV + texto cifrado)`.
     * @return El texto original, o `null` si no se puede descifrar
     *   (clave perdida, dato corrupto o manipulado, formato inválido).
     */
    fun decrypt(encoded: String): String? =
        try {
            val payload = Base64.getDecoder().decode(encoded)
            if (payload.size <= IV_SIZE_BYTES) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    obtainKey(),
                    GCMParameterSpec(TAG_SIZE_BITS, payload, 0, IV_SIZE_BYTES),
                )
                val plain = cipher.doFinal(payload, IV_SIZE_BYTES, payload.size - IV_SIZE_BYTES)
                String(plain, Charsets.UTF_8)
            }
        } catch (exception: GeneralSecurityException) {
            null
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: IOException) {
            null
        }

    /**
     * Elimina la clave del Keystore.
     *
     * Todo lo cifrado con ella pasa a ser ilegible. Se usa al recuperarse
     * de una clave rota y, desde [SecureValueStore], para limpiar la clave
     * antigua de `security-crypto`.
     *
     * @param alias Alias de la clave a borrar (por defecto, la de la app).
     */
    fun deleteKey(alias: String = KEY_ALIAS) {
        runCatching { openKeyStore().deleteEntry(alias) }
        if (alias == KEY_ALIAS) cachedKey = null
    }

    /**
     * Cifra con una clave concreta.
     *
     * @param key Clave AES del Keystore.
     * @param plainText Texto en claro.
     * @return `Base64(IV + texto cifrado)`.
     */
    private fun encryptWith(key: SecretKey, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    /**
     * Devuelve la clave de la app, creándola en el Keystore si no existe.
     *
     * Se sincroniza para que dos hilos que lleguen a la vez no creen dos
     * claves distintas con el mismo alias.
     *
     * @return La clave AES-256 almacenada en el Keystore.
     */
    @Synchronized
    private fun obtainKey(): SecretKey {
        cachedKey?.let { return it }

        val existingKey = openKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
        val key = existingKey ?: generateKey()
        cachedKey = key
        return key
    }

    /**
     * Genera una clave AES-256-GCM nueva dentro del Keystore.
     *
     * @return La clave recién creada.
     */
    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /**
     * Abre el Keystore de Android.
     *
     * @return El [KeyStore] ya cargado.
     */
    private fun openKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    companion object {
        /** Alias de la clave de la app dentro del Keystore. */
        const val KEY_ALIAS = "imoodle_secure_store_key"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
    }
}
