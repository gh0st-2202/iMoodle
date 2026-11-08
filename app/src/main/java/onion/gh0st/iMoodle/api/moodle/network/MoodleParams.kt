package onion.gh0st.iMoodle.api.moodle.network

/**
 * Constructor de parámetros para las llamadas a la API REST de Moodle.
 *
 * ## Qué problema resuelve
 *
 * La API REST de Moodle espera los parámetros en un formato muy
 * concreto, que no es el habitual de otras APIs. En lugar de enviar
 * un JSON en el cuerpo del `POST`, Moodle espera los parámetros como
 * query string en la URL, y además tiene convenciones especiales para
 * arrays y objetos anidados:
 *
 * - Un array: `courseids[0]=1&courseids[1]=2&courseids[2]=3`.
 * - Un objeto anidado: `options[name]=value&options[other]=x`.
 *
 * Si cada repositorio tuviera que construir estas cadenas a mano,
 * habría duplicación y errores. Esta clase centraliza la lógica.
 *
 * ## Cómo se usa
 *
 * ```
 * val params = MoodleParams().apply {
 *     put("userid", 42)
 *     putArray("courseids", listOf(1, 2, 3))
 *     putNested("options", "name", "value")
 * }
 * val urlEncodedMap = params.build()
 * ```
 *
 * El `MoodleApiClient` recibe ese `Map<String, String>` y lo
 * serializa en la URL.
 *
 * ## Por qué es mutable
 *
 * Porque está pensada como un builder: se construye incrementalmente,
 * añadiendo parámetros. Una vez construido, `build()` devuelve un
 * `Map` inmutable que ya no cambia.
 */
class MoodleParams {

    /** Lista interna de pares clave-valor. Se convierte en `Map` en [build]. */
    private val parameters = mutableListOf<Pair<String, String>>()

    /**
     * Añade un parámetro simple.
     *
     * ## Comportamiento con valores `null`
     *
     * Si [value] es `null`, no se añade nada. Esto es cómodo porque
     * muchos parámetros de Moodle son opcionales: si no los tienes,
     * simplemente no los envías. Evita tener que hacer `if (x != null)`
     * en cada llamada.
     *
     * @param key Nombre del parámetro.
     * @param value Valor. Si es `null`, se ignora.
     */
    fun put(key: String, value: Any?) {
        if (value == null) return
        parameters.add(key to value.toString())
    }

    /**
     * Añade un array de valores.
     *
     * ## Cómo lo espera Moodle
     *
     * Moodle no acepta arrays como JSON dentro de un parámetro.
     * Espera que cada elemento vaya en su propia clave, con la
     * posición entre corchetes:
     *
     * ```
     * putArray("courseids", listOf(1, 2, 3))
     * ```
     *
     * produce:
     *
     * ```
     * courseids[0]=1&courseids[1]=2&courseids[2]=3
     * ```
     *
     * ## Cuándo se usa
     *
     * Siempre que un endpoint de Moodle acepte varios IDs a la vez
     * (por ejemplo, `mod_assign_get_assignments` acepta varios
     * `courseids` para pedir las tareas de varios cursos en una sola
     * llamada).
     *
     * @param key Nombre base del array (sin corchetes).
     * @param values Lista de valores a añadir.
     */
    fun putArray(key: String, values: List<Any>) {
        values.forEachIndexed { index, value ->
            parameters.add("$key[$index]" to value.toString())
        }
    }

    /**
     * Añade un parámetro dentro de un objeto anidado.
     *
     * ## Cómo lo espera Moodle
     *
     * Moodle acepta objetos anidados con la notación
     * `prefix[subkey]=value`. Por ejemplo:
     *
     * ```
     * putNested("options", "name", "value")
     * ```
     *
     * produce:
     *
     * ```
     * options[name]=value
     * ```
     *
     * ## Cuándo se usa
     *
     * Algunos endpoints de Moodle agrupan parámetros en un objeto
     * `options`. Por ejemplo, `core_calendar_get_action_events_by_timesort`
     * acepta `options[userevents]=1` para filtrar eventos.
     *
     * @param prefix Nombre del objeto padre.
     * @param key Nombre del parámetro dentro del objeto.
     * @param value Valor.
     */
    fun putNested(prefix: String, key: String, value: Any) {
        parameters.add("$prefix[$key]" to value.toString())
    }

    /**
     * Construye un `Map` inmutable con todos los parámetros.
     *
     * ## Por qué devuelve un `Map` y no la lista interna
     *
     * Porque el `Map` es inmutable y se puede pasar a otras capas sin
     * miedo a que alguien lo modifique. La lista interna sigue siendo
     * privada y mutable, pero queda encapsulada.
     *
     * ## Detalle sobre duplicados
     *
     * Si se añade la misma clave dos veces, el `Map` se queda con el
     * último valor. Es el comportamiento estándar de `toMap()`, y es
     * el correcto para nuestra API: si dos partes del código intentan
     * poner el mismo parámetro, la última es la que se envía.
     *
     * @return El mapa de parámetros listo para serializar.
     */
    fun build(): Map<String, String> = parameters.toMap()
}