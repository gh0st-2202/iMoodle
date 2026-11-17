package onion.gh0st.iMoodle.api.moodle.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * Convierte un `JSONArray` en una lista de sus elementos que son objetos.
 *
 * ## Por qué existe
 *
 * `org.json` solo ofrece acceso por índice, lo que obliga a escribir
 * bucles `for (i in 0 until array.length())` anidados que dificultan
 * leer el parseo de las respuestas de Moodle. Con esta extensión el
 * parseo se escribe como una cadena de `flatMap`/`map`.
 *
 * Los elementos que no sean objetos JSON (números, cadenas, `null`) se
 * ignoran.
 *
 * @receiver Array a recorrer; puede ser `null` (por ejemplo, el
 *   resultado de `optJSONArray`), en cuyo caso se devuelve una lista vacía.
 * @return Los objetos del array, en orden.
 */
internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}
