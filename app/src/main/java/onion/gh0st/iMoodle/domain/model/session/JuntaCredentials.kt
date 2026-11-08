package onion.gh0st.iMoodle.domain.model.session

/**
 * Credenciales unificadas de la Junta de Andalucía.
 *
 * ## Qué es esto
 *
 * Es el par **usuario y contraseña** que el alumno usa para
 * identificarse en el sistema de identificación único de la Junta
 * (IdEA/CAS). Las mismas credenciales sirven para:
 *
 * - Moodle Centros.
 * - Pasen (la app oficial).
 * - Secretaría Virtual.
 * - Cualquier otro servicio de la Junta.
 *
 * ## Por qué se guardan
 *
 * Guardar una contraseña es delicado, así que conviene explicar por
 * qué se hace:
 *
 * Los servicios de la Junta emiten tokens de sesión que caducan al
 * cabo de un rato (típicamente 30 minutos). Cuando un token caduca,
 * hay dos opciones:
 *
 * 1. **Molestar al usuario**: cerrar la sesión y mandarlo al login.
 *    Esto es lo que hacen muchas apps, pero es una experiencia
 *    horrible.
 * 2. **Refrescar el token sin que el usuario se entere**: usar las
 *    credenciales guardadas para pedir un token nuevo de forma
 *    transparente.
 *
 * Esta app implementa la opción 2, y para eso necesita las
 * credenciales guardadas.
 *
 * ## Cómo se guardan
 *
 * Se guardan **cifradas** en el dispositivo, con
 * `EncryptedCredentialsStore`. La contraseña nunca se escribe en
 * claro en disco. Se borran al hacer logout.
 *
 * ## Qué NO es esto
 *
 * - **No son las credenciales de Moodle**: son las de la Junta, que
 *   se usan para CAS. Moodle tiene su propio nombre de usuario
 *   (aunque normalmente es el mismo).
 * - **No es un token**: es la contraseña, no un token de sesión.
 * - **No se envía a Moodle directamente**: se usa para hablar con el
 *   CAS, que a su vez identifica al usuario ante Moodle.
 *
 * @property username Usuario de la Junta. Puede ser el NIF, el NIE o
 *   el identificador IdEA del alumno, según lo que use el centro.
 * @property password Contraseña del usuario.
 */
data class JuntaCredentials(
    val username: String,
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
     * el objeto a un log, la contraseña aparecería en claro.
     *
     * Sobrescribiendo `toString()` nos aseguramos de que, aunque
     * alguien imprima el objeto por error, la contraseña aparezca
     * como `***`.
     *
     * @return Una cadena como `"JuntaCredentials(username=jdoe, password=******)"`.
     */
    override fun toString(): String = "JuntaCredentials(username=$username, password=******)"
}