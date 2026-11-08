package onion.gh0st.iMoodle.domain.model.session

/**
 * Matrícula del alumno en un centro educativo.
 *
 * ## Qué es una matrícula
 *
 * En la Junta de Andalucía, la "matrícula" es la unidad que identifica
 * al alumno dentro de un centro concreto. Una misma persona puede
 * tener varias matrículas:
 *
 * - Una por cada centro en el que esté matriculada.
 * - Una por cada curso académico (aunque el centro sea el mismo).
 *
 * Por ejemplo, una alumna que estudia en el IES Córdoba y, además,
 * hace un curso en el IES Sevilla, tendría dos matrículas.
 *
 * ## Por qué es importante
 *
 * El ID de la matrícula (`id`) es necesario para **casi todos** los
 * endpoints de Pasen que devuelven datos del alumno: horario, notas,
 * faltas de asistencia, comunicaciones, etc. Sin él, no se puede
 * pedir nada.
 *
 * Por eso, el primer paso después del login a Pasen es siempre
 * obtener la matrícula con `info-session`.
 *
 * ## Qué NO contiene
 *
 * - **No contiene el nombre del alumno**: eso está en `User`.
 * - **No contiene el nombre del curso**: cada matrícula tiene un
 *   `course` en la respuesta de Pasen, pero la app solo usa la
 *   primera matrícula y no lo necesita.
 * - **No contiene el token**: la autenticación es cosa de
 *   `PasenApiClient`.
 *
 * ## Quién lo usa
 *
 * - `PasenApiClient.getSessionInfo` lo construye a partir del JSON
 *   de Pasen.
 * - `PasenScheduleRepository` y `PasenUserProfileRepository` lo usan
 *   para obtener el `id` antes de pedir horario o datos del alumno.
 *
 * @property id ID interno de la matrícula en el centro. Es el que se
 *   usa como parámetro `enrollment` en el resto de endpoints.
 * @property centerCode Código oficial del centro educativo. Es un
 *   identificador numérico que usa la Junta internamente.
 * @property centerName Nombre legible del centro. Es lo que se
 *   muestra al usuario.
 */
data class Enrollment(
    val id: String,
    val centerCode: String,
    val centerName: String,
)