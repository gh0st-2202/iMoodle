package onion.gh0st.iMoodle.api.moodle.network

import java.net.URI

/**
 * Utilidad para añadir el `wstoken` a una URL de Moodle.
 *
 * ## Qué problema resuelve
 *
 * Moodle devuelve las URLs de los archivos (apuntes, PDFs, imágenes…)
 * **sin** el token de la API REST. Pero si intentas descargar esos
 * archivos sin el token, Moodle te devuelve un `401` (no autorizado).
 * Hay que añadirlo a la URL antes de pedir el archivo.
 *
 * Esta función de extensión hace exactamente eso: recibe una URL de
 * Moodle, le añade el token como parámetro `token=...`, y devuelve la
 * URL resultante.
 *
 * ## Detalle de seguridad
 *
 * Moodle puede devolver URLs a **otros dominios** (por ejemplo, el
 * campo `fileurl` de un módulo de tipo `url`, que apunta a una web
 * externa del profesor). Si añadiéramos el token a esas URLs, se lo
 * estaríamos regalando al servidor externo. Por eso la función
 * acepta un parámetro `allowedHost`: si la URL no pertenece a ese
 * host, **no se le añade el token**.
 *
 * ## Cuándo se usa
 *
 * - Al mapear archivos de un curso (`MoodleCourseRepository`).
 * - Al mapear adjuntos de una tarea (`MoodleAssignmentRepository`).
 * - Al construir la URL de la imagen de portada de un curso.
 * - Al resolver la URL del avatar del usuario.
 */

/**
 * Añade el `wstoken` a una URL si aún no lo lleva.
 *
 * ## Comportamiento
 *
 * La función decide qué hacer según la URL recibida:
 *
 * 1. **Si se ha especificado `allowedHost` y la URL NO pertenece a
 *    ese host**: devuelve la URL tal cual, sin tocar. Esto protege
 *    enlaces externos (por ejemplo, un `url` de un profesor que
 *    apunta a YouTube) de que se les filtre el token.
 * 2. **Si la URL ya contiene `token=`**: devuelve la URL tal cual.
 *    Evita duplicar el parámetro.
 * 3. **Si la URL ya tiene una query string (`?` en medio)**:
 *    añade `&token=...` al final.
 * 4. **Si la URL no tiene query string**: añade `?token=...` al
 *    final.
 *
 * ## Ejemplos
 *
 * ```
 * "https://moodle/x.pdf".appendMoodleToken("abc", "moodle")
 *   → "https://moodle/x.pdf?token=abc"
 *
 * "https://moodle/x.pdf?a=1".appendMoodleToken("abc", "moodle")
 *   → "https://moodle/x.pdf?a=1&token=abc"
 *
 * "https://evil.com/x.pdf".appendMoodleToken("abc", "moodle")
 *   → "https://evil.com/x.pdf"    (sin token, por seguridad)
 * ```
 *
 * @receiver URL original (con o sin query string).
 * @param token Token de Web Services a añadir.
 * @param allowedHost Host al que se limita el token. Si la URL no
 *   pertenece a este host, no se le añade el token. `null` desactiva
 *   la comprobación (comportamiento heredado de versiones antiguas,
 *   no recomendado).
 * @return La URL con el token incorporado, o la URL sin tocar si no
 *   procede añadirlo.
 */
internal fun String.appendMoodleToken(token: String, allowedHost: String? = null): String = when {
    allowedHost != null && !isFromHost(allowedHost) -> this
    contains("token=") -> this
    contains("?") -> "$this&token=$token"
    else -> "$this?token=$token"
}

/**
 * Comprueba si una URL pertenece a un host concreto.
 *
 * ## Cómo lo comprueba
 *
 * Extrae la **autoridad completa** de la URL (que incluye host,
 * puerto si lo hay, y usuario si lo hay) y la compara con el host
 * esperado, ignorando mayúsculas/minúsculas.
 *
 * ## Por qué se compara la autoridad y no solo el host
 *
 * Porque hay un truco clásico para engañar comparaciones ingenuas de
 * host: una URL como `https://host.es@evil.com/x` parece contener
 * `host.es`, pero en realidad el host real es `evil.com` (lo que hay
 * antes de la `@` es el usuario de la URL). Al comparar la autoridad
 * completa, ese truco no funciona: `host.es@evil.com` no es igual a
 * `host.es`, así que la URL se rechaza.
 *
 * @receiver URL a comprobar.
 * @param host Host esperado (con puerto si lo lleva).
 * @return `true` si la URL pertenece al host.
 */
private fun String.isFromHost(host: String): Boolean =
    runCatching { URI(this).rawAuthority }.getOrNull().equals(host, ignoreCase = true)