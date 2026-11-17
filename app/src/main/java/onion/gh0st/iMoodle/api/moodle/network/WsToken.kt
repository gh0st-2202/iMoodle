package onion.gh0st.iMoodle.api.moodle.network

import okio.ByteString.Companion.decodeBase64

/**
 * Extrae el `wstoken` de Moodle de la cabecera `Location` que devuelve
 * `admin/tool/mobile/launch.php`.
 *
 * ## Formato de la redirección
 *
 * Moodle responde con una redirección a un esquema propio de la app
 * móvil oficial (`moodlemobile://token=<base64>`). El valor de `token`
 * es Base64 en variante URL-safe y, una vez decodificado, contiene
 * tres partes separadas por `:::`:
 *
 * ```
 * firma:::token:::privado
 * ```
 *
 * y el token buscado es la **segunda** parte (índice 1).
 *
 * ## Formato antiguo (comportamiento heredado)
 *
 * Si el valor decodificado no contiene `:::`, se prueba con un único
 * `:` como separador y se toma el componente de **índice 2**. Es el
 * comportamiento que tenía el código original; no se ha podido contrastar
 * con servidores Moodle antiguos, así que se conserva tal cual (el KDoc
 * original afirmaba "la segunda parte" para este caso, lo que no
 * coincidía con el código).
 *
 * ## Por qué se normaliza el Base64
 *
 * La variante URL-safe usa `-` y `_` en lugar de `+` y `/`, y a veces
 * recorta el relleno (`=`). Antes de decodificar se convierten los
 * caracteres y se recalcula el relleno hasta un múltiplo de 4.
 *
 * Esta función es la **única** implementación de esa lógica: la usan
 * tanto [MoodleTokenFetcher] (login completo) como
 * [CookiesBasedTokenRefresher] (refresco silencioso), que solo
 * difieren en cómo reaccionan cuando devuelve `null`.
 *
 * @receiver Cabecera `Location` de la respuesta de `launch.php`.
 * @return El `wstoken` limpio, o `null` si falta o el formato no es el esperado.
 */
internal fun String.decodeWstokenOrNull(): String? {
    if (!contains("token=")) return null

    val rawToken = substringAfter("token=").substringBefore("&")
    val normalizedToken = rawToken.replace('-', '+').replace('_', '/')
    val paddedToken = normalizedToken + "=".repeat((4 - normalizedToken.length % 4) % 4)

    val decodedToken = paddedToken.decodeBase64()?.utf8() ?: return null

    return decodedToken.split(":::").getOrNull(1)?.takeIf { it.isNotEmpty() }
        ?: decodedToken.split(":").getOrNull(2)?.takeIf { it.isNotEmpty() }
}
