package onion.gh0st.iMoodle.domain.model.session

/**
 * Representación del usuario autenticado en la app.
 *
 * ## Qué es esto
 *
 * Es el modelo que representa a la **persona** que está usando la
 * app. Contiene dos bloques de información:
 *
 * 1. **Identidad** (de Moodle): quién es el usuario en Moodle.
 * 2. **Perfil extendido** (de Pasen, opcional): datos personales que
 *    la Junta conoce pero que Moodle no expone (centro, fecha de
 *    nacimiento, edad, sexo).
 *
 * ## Por qué es un único modelo y no dos
 *
 * Porque el alumno es **una sola persona**, aunque los datos vengan
 * de dos fuentes distintas. Si tuviéramos dos modelos
 * (`MoodleUser` + `PasenUser`), la UI tendría que elegir cuál usar y
 * sincronizarlos. Con un solo modelo, la UI siempre trabaja con el
 * mismo `User`, y los campos opcionales se rellenan cuando se
 * consulta Pasen.
 *
 * ## Cuándo se cargan los datos de Pasen
 *
 * Los campos `centerName`, `birthDate`, `age` y `gender` son
 * **opcionales** (nullable). Su valor por defecto es `null`, que
 * significa "aún no se ha consultado Pasen".
 *
 * Cuando el usuario abre la pestaña de perfil, el
 * `ProfileViewModel` llama a `UserProfileRepository.enrich(user)`,
 * que hace login a Pasen, pide los datos, y devuelve un `User`
 * enriquecido con esos campos. El resto del tiempo (por ejemplo,
 * cuando el `User` viene de Moodle directamente), los campos son
 * `null`.
 *
 * ## Qué significa `null` en los campos de Pasen
 *
 * **`null` significa "no se ha consultado"**, no "el proveedor no lo
 * informa". Si se ha consultado y el proveedor no lo tiene, se
 * rellena con la cadena `"No disponible"`. Es una distinción
 * importante para la UI:
 *
 * - `null`: no se ha intentado cargar. Se muestra el placeholder
 *   (por ejemplo, `"-"`).
 * - `"No disponible"`: se intentó cargar, pero Pasen no lo tiene. Se
 *   muestra tal cual.
 *
 * @property id ID interno de Moodle (`userid`). `0` si no se conoce
 *   aún (por ejemplo, en auto-login offline con una sesión antigua
 *   que no lo persistió).
 * @property username Nombre de usuario con el que se hizo login en
 *   Moodle. Es distinto del usuario de la Junta (que es el NIF,
 *   NIE o IdEA).
 * @property firstname Nombre de pila. Puede estar vacío en
 *   auto-login offline sin conexión (cuando no se ha podido cargar
 *   el perfil completo).
 * @property lastname Apellidos. Puede estar vacío en auto-login
 *   offline sin conexión.
 * @property fullname Nombre completo ya formateado por Moodle (por
 *   ejemplo, `"Apellido1 Apellido2, Nombre"`). La UI suele preferir
 *   recomponerlo a partir de `firstname` y `lastname`.
 * @property email Correo electrónico (cadena vacía si no hay).
 * @property profileImageUrl URL del avatar del usuario, o `null` si
 *   no tiene foto configurada.
 * @property centerName Nombre del centro educativo, o `null` si aún
 *   no se ha consultado Pasen.
 * @property birthDate Fecha de nacimiento formateada (por ejemplo,
 *   `"01/01/2005"`), o `null` si aún no se ha consultado Pasen.
 * @property age Edad formateada (por ejemplo, `"21 años"`), o `null`
 *   si aún no se ha consultado Pasen.
 * @property gender Sexo (`"Hombre"`, `"Mujer"`), o `null` si aún no
 *   se ha consultado Pasen.
 */
data class User(
    val id: Int,
    val username: String,
    val firstname: String,
    val lastname: String,
    val fullname: String,
    val email: String,
    val profileImageUrl: String? = null,
    val centerName: String? = null,
    val birthDate: String? = null,
    val age: String? = null,
    val gender: String? = null,
)