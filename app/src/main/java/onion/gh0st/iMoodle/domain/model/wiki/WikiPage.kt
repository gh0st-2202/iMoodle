package onion.gh0st.iMoodle.domain.model.wiki

/**
 * Una página de una wiki de Moodle.
 *
 * ## Qué es una wiki
 *
 * La wiki (`mod_wiki`) es una actividad de Moodle formada por un
 * conjunto de páginas que el alumnado puede leer y, si el curso lo
 * permite, editar. Cada página tiene un título y un contenido con
 * formato enriquecido.
 *
 * ## Diferencia con [WikiPageDraft]
 *
 * `WikiPage` contiene el contenido **ya renderizado** que se muestra
 * al usuario. Para editar hace falta el contenido **original** (el que
 * se envía de vuelta a Moodle); eso lo representa `WikiPageDraft` y se
 * pide aparte solo cuando el usuario pulsa "Editar".
 *
 * ## Quién lo usa
 *
 * - `MoodleWikiRepository` lo construye a partir del JSON de Moodle.
 * - `WikiAdapter` lo usa en la lista de páginas.
 * - `WikiPageFragment` lo usa para mostrar el contenido.
 *
 * @property id ID interno de la página en Moodle.
 * @property title Título visible de la página.
 * @property content Contenido de la página, en HTML listo para mostrar.
 *   Puede estar vacío si la página todavía no tiene texto.
 * @property modifiedAt Fecha de la última modificación en **segundos**
 *   (epoch). Vale `0` si Moodle no la informa (por ejemplo, al pedir el
 *   contenido de una sola página).
 * @property canEdit `true` si el usuario actual puede editar la página.
 *   Lo decide Moodle, así que la app no tiene que adivinar permisos.
 * @property isFirstPage `true` si es la portada de la wiki.
 */
data class WikiPage(
    val id: Int,
    val title: String,
    val content: String = "",
    val modifiedAt: Long = 0L,
    val canEdit: Boolean = false,
    val isFirstPage: Boolean = false,
)
