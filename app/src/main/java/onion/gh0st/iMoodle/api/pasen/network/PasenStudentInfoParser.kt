package onion.gh0st.iMoodle.api.pasen.network

import org.json.JSONObject

/**
 * Parser del JSON de `datos-alumno` de Pasen.
 *
 * ## Qué hace esta clase
 *
 * Recibe el cuerpo JSON crudo que devuelve el endpoint
 * `datos-alumno` de Pasen y lo convierte en una lista de
 * `PasenInfoSection`. Cada sección contiene pares clave-valor (por
 * ejemplo, `("Edad", "21 años")`).
 *
 * ## Formato del JSON de entrada
 *
 * El JSON tiene esta forma (simplificada):
 *
 * ```
 * {
 *   "success": true,
 *   "data": [
 *     {
 *       "section": "Datos Personales",
 *       "items": [
 *         ["Nombre", "Apellido1 Apellido2, Nombre"],
 *         ["Edad", "21 años"],
 *         ["Fecha de nacimiento", "01/01/2005"],
 *         ["Sexo", "Hombre"]
 *       ]
 *     },
 *     {
 *       "section": "Tutores Legales",
 *       "items": [...]
 *     }
 *   ]
 * }
 * ```
 *
 * Fíjate en que cada elemento de `items` es un array de dos
 * posiciones: la primera es la clave, la segunda el valor.
 *
 * ## Qué hace el repositorio con el resultado
 *
 * `PasenUserProfileRepository` busca la sección `"Datos Personales"`
 * y extrae de ella los cuatro campos que necesita (`Fecha de
 * nacimiento`, `Edad`, `Sexo`, y el nombre del centro que viene en
 * `info-session`). Las demás secciones se ignoran.
 *
 * ## Por qué es un `object` y no una clase
 *
 * Mismo motivo que `PasenScheduleParser`: no tiene estado. Es una
 * función pura.
 */
object PasenStudentInfoParser {

    /**
     * Parsea el cuerpo JSON de `datos-alumno`.
     *
     * ## Qué hace paso a paso
     *
     * 1. Convierte el `String` en un `JSONObject`.
     * 2. Comprueba el campo `success`. Si es `false`, lanza una
     *    excepción con el mensaje del servidor.
     * 3. Extrae el array `data`. Si no existe, devuelve lista vacía
     *    (es un caso válido: el alumno puede no tener datos
     *    personales informados).
     * 4. Recorre cada sección:
     *    - Extrae el título (`section`).
     *    - Recorre el array `items`.
     *    - Para cada par `[clave, valor]`, si el valor no está
     *      vacío, lo añade a la sección.
     *    - Si después de filtrar la sección tiene al menos un ítem,
     *      se añade a la lista de salida.
     *
     * ## Por qué se filtran los valores vacíos
     *
     * Porque Pasen a veces devuelve ítems con el valor en blanco
     * (por ejemplo, tutores legales sin datos). Esos ítems no aportan
     * nada y ensucian la UI. Filtrarlos deja solo la información
     * útil.
     *
     * @param rawJsonBody Cuerpo JSON devuelto por Pasen.
     * @return Lista de secciones con sus datos.
     * @throws IllegalStateException si el envelope indica error.
     */
    fun parse(rawJsonBody: String): List<PasenInfoSection> {
        val responseJson = JSONObject(rawJsonBody)
        if (!responseJson.optBoolean("success", false)) {
            val errorMessage = responseJson.optString("message", "Error desconocido de iPasen")
            throw IllegalStateException(errorMessage)
        }

        val sectionsArray = responseJson.optJSONArray("data") ?: return emptyList()
        val sections = mutableListOf<PasenInfoSection>()

        for (sectionIndex in 0 until sectionsArray.length()) {
            val sectionJson = sectionsArray.optJSONObject(sectionIndex) ?: continue
            val sectionTitle = sectionJson.optString("section", "")

            val itemsArray = sectionJson.optJSONArray("items") ?: continue
            val sectionItems = mutableListOf<Pair<String, String>>()

            for (itemIndex in 0 until itemsArray.length()) {
                val itemArray = itemsArray.optJSONArray(itemIndex) ?: continue
                if (itemArray.length() >= 2) {
                    val itemKey = itemArray.optString(0, "")
                    val itemValue = itemArray.optString(1, "")
                    if (itemValue.isNotBlank()) {
                        sectionItems.add(itemKey to itemValue)
                    }
                }
            }

            if (sectionItems.isNotEmpty()) {
                sections.add(PasenInfoSection(sectionTitle, sectionItems))
            }
        }
        return sections
    }
}