package onion.gh0st.iMoodle.api.pasen.network

import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser del JSON del horario de Pasen.
 *
 * ## Qué hace esta clase
 *
 * Recibe el cuerpo JSON crudo que devuelve el endpoint `get-horario`
 * de Pasen y lo convierte en una lista de `ScheduleSlot` de dominio.
 * Es la pieza que traduce el formato de la Junta al formato que
 * entiende la app.
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
 *       "day": "Lunes",
 *       "hoursection": "8:30 - 9:30",
 *       "section": 1,
 *       "subject": "Acceso a datos",
 *       "prof": "García, Juan",
 *       "codesection": 1000001
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * Cada elemento del array `data` es una franja horaria: una
 * asignatura en un día y una hora concretos.
 *
 * ## Por qué es un `object` y no una clase
 *
 * Porque no tiene estado. Es una función pura: recibe un `String`,
 * devuelve una lista. No necesita instanciarse, así que se expone
 * como un singleton de Kotlin (`object`) para que se pueda llamar
 * directamente: `PasenScheduleParser.parse(json)`.
 *
 * ## Qué pasa si el JSON es inválido
 *
 * - Si `success` es `false`: se lanza `IllegalStateException` con el
 *   mensaje del servidor.
 * - Si falta el array `data`: se lanza `IllegalStateException`.
 * - Si algún slot individual tiene campos vacíos: se rellena con
 *   valores por defecto (cadena vacía, 0), pero el slot se incluye
 *   igualmente. Es preferible mostrar un slot con datos parciales
 *   que perderlo.
 */
object PasenScheduleParser {

    /**
     * Parsea el cuerpo JSON del horario.
     *
     * ## Qué hace paso a paso
     *
     * 1. Convierte el `String` en un `JSONObject`.
     * 2. Comprueba el campo `success`. Si es `false`, lanza una
     *    excepción con el mensaje del servidor.
     * 3. Extrae el array `data`. Si no existe, lanza una excepción.
     * 4. Recorre el array y convierte cada elemento en un
     *    `ScheduleSlot`.
     *
     * ## Manejo de slots inválidos
     *
     * El recorrido usa `mapNotNull`, lo que significa que los
     * elementos que no se puedan leer como `JSONObject` se ignoran
     * silenciosamente. Es defensivo: un slot malformado no debe
     * tumbar el resto del horario.
     *
     * @param rawJsonBody Cuerpo JSON devuelto por Pasen.
     * @return Lista de slots del horario.
     * @throws IllegalStateException si el envelope indica error o no
     *   trae el campo `data`.
     */
    fun parse(rawJsonBody: String): List<ScheduleSlot> {
        val responseJson = JSONObject(rawJsonBody)
        if (!responseJson.optBoolean("success", false)) {
            val errorMessage = responseJson.optString("message", "Error desconocido de iPasen")
            throw IllegalStateException(errorMessage)
        }
        val slotsArray = responseJson.optJSONArray("data")
            ?: throw IllegalStateException("iPasen no devolvió 'data'")
        return parseSlots(slotsArray)
    }

    /**
     * Convierte el array de slots crudos en una lista de
     * `ScheduleSlot`.
     *
     * @param slotsArray Array `data` del JSON de Pasen.
     * @return Lista de slots.
     */
    private fun parseSlots(slotsArray: JSONArray): List<ScheduleSlot> =
        (0 until slotsArray.length()).mapNotNull { slotIndex ->
            slotsArray.optJSONObject(slotIndex)?.let { slotJson -> parseSlot(slotJson) }
        }

    /**
     * Convierte un `JSONObject` de slot en un `ScheduleSlot` de
     * dominio.
     *
     * ## Sobre el nombre del profesor
     *
     * En el JSON de Pasen, el campo del profesor se llama `prof`,
     * pero en el dominio lo llamamos `professor` para que sea más
     * legible. Aquí se hace la traducción.
     *
     * ## Sobre los valores por defecto
     *
     * Si algún campo falta, se rellena con un valor vacío o cero. Es
     * defensivo: es preferible mostrar un slot con datos incompletos
     * que romper el parseo entero.
     *
     * @param slotJson `JSONObject` de un slot.
     * @return Slot de dominio.
     */
    private fun parseSlot(slotJson: JSONObject): ScheduleSlot =
        ScheduleSlot(
            day = slotJson.optString("day", ""),
            hoursection = slotJson.optString("hoursection", ""),
            section = slotJson.optInt("section", 0),
            subject = slotJson.optString("subject", ""),
            professor = slotJson.optString("prof", ""),
            codeSection = slotJson.optInt("codesection", 0),
        )
}