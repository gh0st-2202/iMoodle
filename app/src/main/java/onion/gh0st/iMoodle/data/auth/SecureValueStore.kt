package onion.gh0st.iMoodle.data.auth

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Almacén clave-valor privado cuyos **valores** se guardan cifrados con
 * [KeystoreCipher].
 *
 * ## Qué es
 *
 * Un `SharedPreferences` normal (un pequeño fichero XML privado de la
 * app) en el que cada valor se cifra antes de escribirlo y se descifra al
 * leerlo. Sustituye a `EncryptedSharedPreferences` (obsoleta). Los
 * **nombres de las claves** (`username`, `<provincia>.wstoken`…) se
 * guardan en claro porque no son secretos; lo que importa, el contenido,
 * va cifrado.
 *
 * ## Detalles de diseño
 *
 * - **Escrituras atómicas**: [putAll] graba varias entradas con un único
 *   `commit()`, así nunca queda un registro a medias (por ejemplo, un
 *   usuario sin su token). Se usa `commit()` en lugar de `apply()` para
 *   que, al volver, el dato ya esté en disco aunque el proceso muera a
 *   continuación. Todas las llamadas deben hacerse fuera del hilo
 *   principal.
 * - **Lectura tolerante**: si un valor no se puede descifrar (clave
 *   perdida, dato corrupto) se trata como si no existiera, en lugar de
 *   lanzar una excepción.
 * - **Limpieza de lo antiguo**: al crearse, borra los ficheros y la
 *   clave que dejó la versión anterior basada en `security-crypto`. Esos
 *   datos no se pueden migrar sin conservar la librería obsoleta, así
 *   que tras actualizar hay que iniciar sesión una vez más.
 *
 * @param context Contexto de aplicación.
 * @param fileName Nombre del fichero de preferencias que usa este almacén.
 * @param cipher Cifrador con la clave del Keystore.
 */
internal class SecureValueStore(
    context: Context,
    fileName: String,
    private val cipher: KeystoreCipher,
) {

    private val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    init {
        removeLegacyStorage(context)
    }

    /**
     * Lee un valor.
     *
     * @param key Nombre de la clave.
     * @return El valor descifrado, o `null` si no existe o no se puede descifrar.
     */
    fun getString(key: String): String? =
        preferences.getString(key, null)?.let { encoded -> cipher.decrypt(encoded) }

    /**
     * Lee un número entero largo guardado como texto cifrado.
     *
     * @param key Nombre de la clave.
     * @param defaultValue Valor a devolver si no existe o no es un número.
     * @return El número leído o [defaultValue].
     */
    fun getLong(key: String, defaultValue: Long): Long =
        getString(key)?.toLongOrNull() ?: defaultValue

    /**
     * Lee un entero guardado como texto cifrado.
     *
     * @param key Nombre de la clave.
     * @param defaultValue Valor a devolver si no existe o no es un número.
     * @return El número leído o [defaultValue].
     */
    fun getInt(key: String, defaultValue: Int): Int =
        getString(key)?.toIntOrNull() ?: defaultValue

    /**
     * Devuelve los nombres de todas las claves guardadas.
     *
     * @return Conjunto de claves (en claro).
     */
    fun keys(): Set<String> = preferences.all.keys.toSet()

    /**
     * Guarda varias entradas de forma atómica.
     *
     * @param entries Pares `clave -> valor`. Los valores se cifran uno a uno.
     * @throws GeneralSecurityException si el Keystore no puede cifrar.
     * @throws IOException si el sistema no puede escribir el fichero.
     */
    fun putAll(entries: Map<String, String>) {
        val editor = preferences.edit()
        entries.forEach { (key, value) -> editor.putString(key, cipher.encrypt(value)) }
        commitOrThrow(editor)
    }

    /**
     * Elimina varias entradas de forma atómica.
     *
     * @param keys Claves a borrar.
     * @throws IOException si el sistema no puede escribir el fichero.
     */
    fun remove(keys: Collection<String>) {
        val editor = preferences.edit()
        keys.forEach { key -> editor.remove(key) }
        commitOrThrow(editor)
    }

    /**
     * Elimina todas las entradas de este almacén.
     *
     * @throws IOException si el sistema no puede escribir el fichero.
     */
    fun clear() {
        commitOrThrow(preferences.edit().clear())
    }

    /**
     * Confirma una edición y lanza si el sistema no pudo escribirla.
     *
     * ## Por qué se comprueba `commit()`
     *
     * Porque devuelve `false` cuando la escritura en disco falla. Si
     * se ignorara, el logout podría «limpiar» el token solo en
     * memoria y dejar el fichero con la sesión anterior, que se
     * restauraría al reiniciar. También se ignoraría un fallo al
     * guardar credenciales, y el refresco silencioso dejaría de
     * funcionar sin avisar.
     *
     * @param editor Edición pendiente.
     * @throws IOException si `commit()` devuelve `false`.
     */
    private fun commitOrThrow(editor: SharedPreferences.Editor) {
        if (!editor.commit()) {
            throw IOException("No se pudo escribir el almacén seguro")
        }
    }

    /**
     * Borra los ficheros de `EncryptedSharedPreferences` y su clave
     * maestra del Keystore.
     *
     * Es idempotente: si no queda nada de la versión antigua, no hace
     * nada. El fichero de *keyset* se borra una sola vez para los dos
     * almacenes antiguos (compartían el mismo).
     *
     * @param context Contexto de aplicación.
     */
    private fun removeLegacyStorage(context: Context) {
        LEGACY_PREFERENCE_FILES.forEach { legacyName -> context.deleteSharedPreferences(legacyName) }
        cipher.deleteKey(LEGACY_MASTER_KEY_ALIAS)
    }

    private companion object {
        /** Ficheros que creaba `EncryptedSharedPreferences` (datos de ambos almacenes y keyset compartido). */
        val LEGACY_PREFERENCE_FILES = listOf(
            "imoodle_junta_credentials",
            "imoodle_auth_secure",
            "__androidx_security_crypto_encrypted_prefs__",
        )

        /** Alias de la clave maestra que creaba `MasterKey` de `security-crypto`. */
        const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
}
