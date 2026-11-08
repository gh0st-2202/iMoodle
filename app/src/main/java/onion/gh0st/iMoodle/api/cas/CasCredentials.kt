package onion.gh0st.iMoodle.api.cas

/**
 * Credenciales del usuario para el CAS de la Junta de Andalucía.
 *
 * ## Qué es esto
 *
 * Es un envoltorio alrededor del par usuario/contraseña que el alumno
 * introduce en la pantalla de login. Existe como clase propia (en
 * lugar de pasar dos `String` sueltos) por tres motivos:
 *
 * 1. **Seguridad**: se puede sobrescribir [toString] para enmascarar
 *    la contraseña. Si alguien (por ejemplo, un log de errores)
 *    imprime el objeto, la contraseña no aparece en claro.
 * 2. **Comodidad**: pasar un solo objeto es menos propenso a errores
 *    que pasar dos strings con el orden correcto.
 * 3. **Evolución**: si en el futuro hay que añadir un campo (por
 *    ejemplo, un segundo factor de autenticación), se añade aquí y
 *    no hay que cambiar todas las firmas.
 *
 * ## Qué NO guarda
 *
 * Esta clase **no persiste nada**. Es puramente un contenedor en
 * memoria. La persistencia cifrada para el refresco silencioso la
 * gestiona `EncryptedCredentialsStore`.
 *
 * @property user Nombre de usuario de la Junta. Puede ser el NIF, el
 *   NIE o el identificador IdEA del alumno, según lo que use el
 *   centro. No es el nombre de usuario de Moodle.
 * @property password Contraseña del usuario de la Junta.
 */
data class CasCredentials(
    val user: String,
    val password: String,
) {

    /**
     * Devuelve una representación del objeto con la contraseña
     * enmascarada.
     *
     * ## Por qué es importante
     *
     * Por defecto, un `data class` de Kotlin genera un `toString()`
     * que imprime **todos** los campos, incluida la contraseña. Si en
     * algún punto del código se hace `println(credentials)` o se pasa
     * el objeto a un `Log.e`, la contraseña aparecería en claro en
     * logcat. Eso es un riesgo de seguridad: cualquier otra app con
     * acceso a logcat, o cualquier captura de pantalla del log,
     * podría leerla.
     *
     * Sobrescribiendo `toString()` nos aseguramos de que, aunque
     * alguien imprima el objeto por error, la contraseña aparezca
     * como `***`.
     *
     * @return Una cadena como `"CasCredentials(user=jdoe, password=***)"`.
     */
    override fun toString(): String = "CasCredentials(user=$user, password=***)"
}