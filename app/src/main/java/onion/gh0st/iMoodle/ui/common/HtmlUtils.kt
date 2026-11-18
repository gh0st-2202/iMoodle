package onion.gh0st.iMoodle.ui.common

import android.text.Html
import android.text.Spanned

/**
 * Utilidades para convertir el HTML de Moodle al formato que necesita
 * cada pantalla.
 *
 * ## Qué problema resuelve
 *
 * Muchas descripciones de Moodle (tareas, cursos, etiquetas,
 * mensajes de foro) vienen con **formato HTML**: negritas,
 * cursivas, enlaces, párrafos, tablas, etc. Es el formato que usa el
 * editor de Moodle.
 *
 * La app no puede mostrar el HTML tal cual (se verían las etiquetas
 * `<p>`, `<b>`, etc.). Tiene que "limpiarlo" para mostrar solo el
 * texto. Pero según el contexto, se necesita un nivel de limpieza
 * distinto:
 *
 * - **Para descripciones cortas**: texto plano sin etiquetas.
 * - **Para párrafos largos**: texto plano preservando saltos de
 *   línea (para que se lean bien los párrafos).
 * - **Para textos interactivos**: spans preservados (enlaces
 *   clicables, negritas, cursivas).
 *
 * Además incluye dos ayudas específicas: convertir el texto que escribe
 * el usuario en HTML seguro para las actividades que lo guardan así
 * (glosario, wiki) y retirar el «chrome» que Moodle añade al contenido
 * web de una wiki (índice y enlaces «[Editar]»).
 *
 * ## Quién la usa
 *
 * - `CourseDetailAdapter`: para limpiar las descripciones de los
 *   módulos y las etiquetas.
 * - `AssignmentDetailFragment`: para renderizar la descripción de las
 *   tareas con sus enlaces.
 * - `DiscussionAdapter`: para renderizar los posts de foro con
 *   formato.
 * - `GenericModuleFragment`: para limpiar la descripción de los
 *   módulos genéricos.
 * - `WikiPageFragment`: para limpiar el contenido de una página antes
 *   de renderizarlo.
 * - `GlossaryEntryDialog` y `NewWikiPageDialog`: para convertir el
 *   texto del usuario en HTML.
 */
object HtmlUtils {

    /**
     * Convierte HTML a texto plano, eliminando todas las etiquetas.
     *
     * ## Qué hace
     *
     * Usa `Html.fromHtml` con el modo `FROM_HTML_MODE_LEGACY`, que
     * convierte el HTML a un `Spanned` con los spans ya resueltos
     * (negritas, cursivas, etc.). Luego lo convierte a `String` con
     * `toString()`, que elimina los spans pero mantiene el texto.
     * Finalmente, `trim()` quita espacios al principio y al final.
     *
     * ## Cuándo se usa
     *
     * Para descripciones cortas que no necesitan preservar la
     * estructura (por ejemplo, un resumen de módulo).
     *
     * ## Qué se pierde
     *
     * - **Saltos de línea**: los `<br>`, `</p>`, etc. se pierden. Si
     *   el texto tenía varios párrafos, quedarán pegados.
     * - **Enlaces clicables**: se convierten a texto plano.
     * - **Imágenes**: se descartan.
     *
     * @param html Cadena con HTML de Moodle.
     * @return Texto plano sin etiquetas, con espacios recortados.
     */
    fun stripHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()

    /**
     * Convierte HTML a texto plano preservando saltos de línea
     * estructurales.
     *
     * ## Qué hace
     *
     * 1. Antes de llamar a `Html.fromHtml`, sustituye ciertas
     *    etiquetas HTML por saltos de línea (`\n`):
     *    - `<br>` → `\n`.
     *    - `</p>` → `\n`.
     *    - `</tr>` → `\n` (filas de tabla).
     *    - `</h1>` a `</h6>` → `\n` (cabeceras).
     * 2. Aplica `Html.fromHtml` y `trim()`.
     * 3. Normaliza los saltos de línea: si hay 3 o más seguidos, los
     *    reduce a 2 (para que no haya párrafos con demasiado
     *    espacio).
     *
     * ## Cuándo se usa
     *
     * Para textos largos que deben preservar la estructura (por
     * ejemplo, una etiqueta con varios párrafos, o una descripción
     * de curso con listas).
     *
     * ## Qué se preserva y qué no
     *
     * - **Preserva**: la separación entre párrafos.
     * - **No preserva**: los enlaces clicables, las imágenes, los
     *   estilos (negritas, cursivas).
     *
     * @param html Cadena con HTML de Moodle.
     * @return Texto plano con saltos de línea normalizados.
     */
    fun stripHtmlPreservingLines(html: String): String {
        val htmlWithNewlines = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</tr>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n")
        return Html.fromHtml(htmlWithNewlines, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")
    }

    /**
     * Convierte HTML a un `Spanned` preservando los spans (enlaces,
     * negritas, cursivas).
     *
     * ## Qué hace
     *
     * Usa `Html.fromHtml` con el modo `FROM_HTML_MODE_COMPACT`, que
     * genera un `Spanned` con los spans ya aplicados. Un `Spanned` es
     * un `CharSequence` que lleva información de formato adjunta
     * (por ejemplo, "estos caracteres son negrita", "estos son un
     * enlace").
     *
     * ## Cuándo se usa
     *
     * Para textos interactivos (por ejemplo, los posts de un foro),
     * donde el usuario puede querer pulsar un enlace.
     *
     * ## Requisito del `TextView`
     *
     * Para que los enlaces sean clicables, el `TextView` que recibe
     * el `Spanned` debe tener `movementMethod = LinkMovementMethod`.
     * Sin esto, los enlaces se ven como texto subrayado pero no se
     * pueden pulsar.
     *
     * @param html Cadena con HTML de Moodle.
     * @return El `Spanned` listo para asignar a un `TextView`.
     */
    fun renderHtml(html: String): Spanned =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)

    /**
     * Convierte el texto que escribe el usuario en HTML sencillo.
     *
     * ## Qué problema resuelve
     *
     * Los editores de Moodle (glosario, wiki) guardan el contenido en
     * HTML. Si se envía el texto tal cual, los saltos de línea se
     * pierden al mostrarlo y cualquier `<` que haya escrito el usuario se
     * interpreta como una etiqueta. Esta función escapa los caracteres
     * especiales y convierte cada salto de línea en un `<br>`, de modo
     * que el texto se ve igual dentro y fuera del editor.
     *
     * ## Qué NO hace
     *
     * No interpreta el texto como HTML: no convierte `**negrita**` ni
     * nada parecido. Si el usuario quiere formato, tendría que escribirlo
     * y eso aquí quedaría escapado. Es el compromiso de un editor de
     * texto plano.
     *
     * @param text Texto plano escrito por el usuario.
     * @return El mismo texto en HTML seguro, con los saltos preservados.
     */
    fun plainTextToHtml(text: String): String =
        Html.escapeHtml(text)
            .replace("\r\n", "\n")
            .replace("\n", "<br>")

    /**
     * Quita del contenido renderizado de una wiki el «chrome» de la web
     * de Moodle.
     *
     * ## Qué problema resuelve
     *
     * `mod_wiki_get_page_contents` devuelve `cachedcontent`, que es el
     * HTML que Moodle pinta en su propia página. Ese HTML incluye dos
     * elementos que solo tienen sentido en el navegador:
     *
     * - El **índice** (`<div class="wiki-toc">…</div>`), con enlaces
     *   internos a `#toc-1`, `#toc-2`…
     * - Un enlace **[Editar]** al final de cada encabezado de nivel 1
     *   (`<a class="wiki_edit_section" href="edit.php?…">`), que abre
     *   el editor web de esa sección.
     *
     * En la app no hay navegador ni editor por secciones: el índice
     * ocupa media pantalla y los enlaces `edit.php` no llevan a ningún
     * sitio. Peor aún, su texto (`[Editar]`) se queda pegado al título
     * de la sección como si fuera parte de él.
     *
     * ## Qué NO hace
     *
     * No convierte el HTML a texto ni elimina otro contenido: solo
     * retira esos dos elementos. Los encabezados y los enlaces propios
     * de la página se conservan.
     *
     * @param html Contenido renderizado devuelto por Moodle.
     * @return El mismo HTML sin el índice ni los enlaces de edición.
     */
    fun stripWikiChrome(html: String): String =
        html.replace(WIKI_TOC, "")
            .replace(WIKI_SECTION_EDIT_LINK, "")
            .trim()

    /** Índice que Moodle añade al principio del contenido de una wiki. */
    private val WIKI_TOC = Regex(
        """<div\s+class\s*=\s*["']wiki-toc["'][^>]*>.*?</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Enlace «[Editar]» que Moodle añade al final de cada encabezado. */
    private val WIKI_SECTION_EDIT_LINK = Regex(
        """\s*<a\b[^>]*class\s*=\s*["'][^"']*wiki_edit_section[^"']*["'][^>]*>.*?</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
}