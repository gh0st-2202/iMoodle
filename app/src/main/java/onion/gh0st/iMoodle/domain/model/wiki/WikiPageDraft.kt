package onion.gh0st.iMoodle.domain.model.wiki

/**
 * Contenido original de una página de wiki, listo para editar.
 *
 * ## Por qué no basta con [WikiPage]
 *
 * Moodle guarda las páginas en el formato que use la wiki (HTML,
 * Markdown, Creole…) y, al consultarlas, devuelve el contenido **ya
 * renderizado** para mostrarlo. Si se enviara ese HTML de vuelta al
 * editar, Moodle lo volvería a formatear y el texto se estropearía.
 *
 * Por eso, cuando el usuario pulsa "Editar", la app pide el contenido
 * original con `mod_wiki_get_page_for_editing` y lo envuelve en este
 * modelo. Al guardar se devuelve **exactamente** ese contenido, con los
 * cambios del usuario, sin conversiones.
 *
 * ## Quién lo usa
 *
 * - `MoodleWikiRepository` lo construye a partir del JSON de Moodle.
 * - `WikiPageViewModel` lo emite para abrir el editor.
 * - `EditWikiPageDialog` lo usa como texto inicial.
 *
 * @property pageId ID de la página que se está editando.
 * @property content Contenido original de la página, en el formato
 *   propio de la wiki.
 */
data class WikiPageDraft(
    val pageId: Int,
    val content: String,
)
