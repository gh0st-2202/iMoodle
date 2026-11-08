package onion.gh0st.iMoodle.ui.common

/**
 * Utilidad para validar URLs que vienen del servidor antes de
 * abrirlas en otra app.
 *
 * ## Qué problema resuelve
 *
 * La app a veces tiene que abrir enlaces externos: por ejemplo, un
 * módulo de tipo `url` que apunta a una web del profesor. Cuando el
 * usuario pulsa el enlace, la app lanza un `Intent` con
 * `ACTION_VIEW`, que hace que Android abra la app apropiada (el
 * navegador, YouTube, etc.).
 *
 * El problema es que **esas URLs las escribe el profesor**, no la
 * app. Un profesor malicioso (o un alumno con acceso al curso)
 * podría poner una URL con un **esquema peligroso**:
 *
 * - `javascript:alert(1)` podría ejecutar código si se abriera en
 *   un WebView.
 * - `file:///sdcard/secret.txt` podría intentar leer archivos del
 *   dispositivo.
 * - `intent://...` podría lanzar un Intent arbitrario, potencialmente
 *   abriendo apps internas del sistema.
 * - `content://com.android.contacts/...` podría acceder a contactos.
 *
 * Para evitarlo, esta utilidad valida que la URL sea `http://` o
 * `https://` antes de lanzar el Intent. Si no lo es, se rechaza.
 *
 * ## Quién la usa
 *
 * - `CourseDetailFragment.openUrl`: antes de lanzar el `ACTION_VIEW`
 *   para un módulo `url`.
 */
object WebUrls {

    /**
     * Comprueba si una URL es segura para abrir en otra app.
     *
     * ## Qué considera "segura"
     *
     * Solo URLs que empiecen por `http://` o `https://`, ignorando
     * mayúsculas y espacios al principio o al final. Cualquier otro
     * esquema se considera inseguro.
     *
     * ## Cómo lo comprueba
     *
     * - Convierte la URL a minúsculas y quita espacios.
     * - Comprueba si empieza por `"http://"` o `"https://"`.
     *
     * ## Por qué no usa `URI(...)`
     *
     * Porque `URI` rechaza URLs con espacios o caracteres no ASCII
     * (por ejemplo, `"https://example.com/mi documento.pdf"`), que
     * Android sí sabe abrir. Al comprobar por prefijo, esas URLs
     * válidas se aceptan.
     *
     * ## Qué NO hace
     *
     * No valida que el dominio sea legítimo, ni que la URL no sea
     * maliciosa, ni nada por el estilo. Solo distingue entre "URL
     * web" y "otro esquema peligroso". Es una primera línea de
     * defensa, no una garantía absoluta.
     *
     * @param url URL a validar. Puede ser `null`.
     * @return `true` si es una URL web (`http` o `https`), `false`
     *   en caso contrario.
     */
    fun isSafeWebUrl(url: String?): Boolean {
        val normalizedUrl = url?.trim()?.lowercase() ?: return false
        return normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
    }
}