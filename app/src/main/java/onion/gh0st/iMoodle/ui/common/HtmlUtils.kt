package onion.gh0st.iMoodle.ui.common

import android.text.Html
import android.text.Spanned

/**
 * Utilidades para convertir HTML de Moodle a texto.
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
 * - **Para posts de foro**: spans preservados (enlaces clicables,
 *   negritas, cursivas).
 *
 * Esta utilidad expone tres métodos, uno para cada caso.
 *
 * ## Quién la usa
 *
 * - `CourseDetailAdapter`: para limpiar las descripciones de los
 *   módulos y las etiquetas.
 * - `AssignmentDetailFragment`: para limpiar la descripción de las
 *   tareas.
 * - `DiscussionAdapter`: para renderizar los posts de foro con
 *   formato.
 * - `GenericModuleFragment`: para limpiar la descripción de los
 *   módulos genéricos.
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
}