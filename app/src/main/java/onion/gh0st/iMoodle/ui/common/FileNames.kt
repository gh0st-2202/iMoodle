package onion.gh0st.iMoodle.ui.common

/**
 * Utilidad para sanear nombres de archivo que vienen del servidor.
 *
 * ## Qué problema resuelve
 *
 * Cuando la app descarga un archivo de Moodle, el nombre viene del
 * servidor (lo pone el profesor al subirlo). Ese nombre **no es de
 * fiar** por varios motivos:
 *
 * 1. **Caracteres inválidos**: puede contener caracteres que el
 *    sistema de archivos de Android no acepta (como `/`, `:`, `*`,
 *    `?`, `"`, `<`, `>`, `|`). Si se usara tal cual como nombre de
 *    archivo, la descarga fallaría.
 * 2. **Path traversal**: puede contener secuencias como
 *    `../../etc/passwd`, que intentan "escapar" del directorio de
 *    descargas y escribir en otro sitio. Esto es un vector de ataque
 *    clásico: si la app no lo sanea, un profesor malicioso podría
 *    hacer que la app sobrescriba archivos del sistema.
 * 3. **Nombres ocultos**: puede empezar por punto (`.archivo`), lo
 *    que en Linux/Android significa "archivo oculto". No queremos
 *    crear archivos ocultos sin querer.
 * 4. **Nombres vacíos**: puede quedar vacío después de limpiar
 *    caracteres, y un nombre de archivo vacío no es válido.
 *
 * Esta utilidad se encarga de convertir cualquier nombre que venga
 * del servidor en un nombre **seguro** para usar en el sistema de
 * archivos de Android.
 *
 * ## Quién la usa
 *
 * `FileDownloadHelper`, antes de lanzar la descarga con
 * `DownloadManager`.
 */
object FileNames {

    /**
     * Regex con los caracteres prohibidos en nombres de archivo.
     *
     * ## Qué incluye
     *
     * - **Barras**: `/` y `\` (separadores de ruta).
     * - **Caracteres reservados de Windows/Android**: `:`, `*`, `?`,
     *   `"`, `<`, `>`, `|`.
     * - **Caracteres de control** (códigos Unicode U+0000 a U+001F):
     *   no se pueden usar en nombres de archivo.
     */
    private val FORBIDDEN_CHARACTERS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    /**
     * Sanea un nombre de archivo que viene del servidor.
     *
     * ## Qué hace paso a paso
     *
     * 1. **Normaliza separadores**: convierte `\` en `/` para tratar
     *    ambos igual.
     * 2. **Descarta la ruta**: toma solo la última parte después del
     *    último `/`. Así `../../etc/passwd` se convierte en `passwd`.
     * 3. **Sustituye caracteres prohibidos**: cambia cada carácter
     *    inválido por `_`.
     * 4. **Limpia extremos**: quita espacios al principio y al final,
     *    y puntos al principio y al final (para evitar archivos
     *    ocultos y nombres que empiezan por punto).
     * 5. **Fallback**: si después de todo esto el nombre queda vacío,
     *    usa el fallback (`"archivo"` por defecto).
     *
     * ## Ejemplos
     *
     * ```
     * sanitize("informe final.pdf")          → "informe final.pdf"
     * sanitize("../../etc/passwd")           → "passwd"
     * sanitize("..\\..\\evil.apk")           → "evil.apk"
     * sanitize("/absolute/a.pdf")            → "a.pdf"
     * sanitize("a:b?c*.pdf")                 → "a_b_c_.pdf"
     * sanitize("")                           → "archivo"
     * sanitize("...oculto")                  → "oculto"
     * ```
     *
     * ## Por qué el fallback
     *
     * Porque a veces el nombre original es imposible de sanear (por
     * ejemplo, si solo contiene puntos). En ese caso, es mejor usar
     * un nombre genérico que dejar la descarga sin nombre.
     *
     * @param rawFileName Nombre original, tal cual viene del
     *   servidor.
     * @param fallback Nombre a usar si no queda nada utilizable
     *   después del saneado.
     * @return Un nombre seguro para usar en el sistema de archivos.
     */
    fun sanitize(rawFileName: String, fallback: String = "archivo"): String {
        val lastPathComponent = rawFileName
            .replace('\\', '/')
            .substringAfterLast('/')
        val cleanedFileName = lastPathComponent
            .replace(FORBIDDEN_CHARACTERS, "_")
            .trim()
            .trim('.')
        return cleanedFileName.ifEmpty { fallback }
    }
}