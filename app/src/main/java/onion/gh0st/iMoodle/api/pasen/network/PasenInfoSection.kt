package onion.gh0st.iMoodle.api.pasen.network

/**
 * Una sección de datos del alumno en la respuesta cruda de Pasen.
 *
 * ## Qué es una "sección"
 *
 * Cuando se pide el endpoint `datos-alumno`, el servidor devuelve los
 * datos agrupados por categorías. Por ejemplo:
 *
 * - **Datos Personales**: nombre, edad, fecha de nacimiento, sexo.
 * - **Tutores Legales**: nombre del tutor, teléfono, email, etc.
 *
 * Cada una de esas categorías es lo que esta clase llama "sección".
 *
 * ## Para qué se usa
 *
 * Es un objeto **interno del parser**, no un modelo de dominio. Solo
 * lo usan `PasenStudentInfoParser` (que lo construye) y
 * `PasenUserProfileRepository` (que lee la sección "Datos
 * Personales"). El dominio de la app nunca ve esta clase: solo ve el
 * `User` final enriquecido.
 *
 * Por eso vive en `api/pasen/network/` y no en `domain/`. Si algún
 * día otra fuente de datos (por ejemplo, otra app oficial de la
 * Junta) devolviera algo parecido, se podría reutilizar; pero, de
 * momento, es específica de Pasen.
 *
 * @property title Título de la sección tal cual lo envía Pasen. Por
 *   ejemplo, `"Datos Personales"` o `"Tutores Legales"`.
 * @property items Lista de pares `(Clave, Valor)` de la sección. Por
 *   ejemplo, `("Edad", "21 años")` o `("Sexo", "Hombre")`.
 */
data class PasenInfoSection(
    val title: String,
    val items: List<Pair<String, String>>,
)