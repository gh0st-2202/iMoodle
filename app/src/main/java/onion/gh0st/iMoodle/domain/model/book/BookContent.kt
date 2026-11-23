package onion.gh0st.iMoodle.domain.model.book

/**
 * Contenido de un libro de Moodle (`mod_book`).
 *
 * ## Qué es un libro
 *
 * Un libro es un conjunto de capítulos ordenados que el profesor
 * escribe en Moodle. Cada capítulo tiene su propio contenido HTML y
 * se navega en orden.
 *
 * ## Por qué es un modelo propio
 *
 * Moodle devuelve el libro dentro del listado de contenidos del curso
 * sin sus capítulos. El cuerpo hay que pedirlo aparte con
 * `mod_book_get_books_by_courses`, que devuelve la introducción y,
 * cuando el servidor lo soporta, los capítulos.
 *
 * @property name Nombre visible del libro.
 * @property intro Introducción del libro en HTML, o `null` si no tiene.
 * @property chapters Capítulos del libro, en orden. Puede estar vacío
 *   si el servidor no los devuelve; en ese caso la UI ofrece abrir
 *   el libro en Moodle.
 */
data class BookContent(
    val name: String,
    val intro: String?,
    val chapters: List<BookChapter>,
)

/**
 * Un capítulo de un libro de Moodle.
 *
 * @property title Título del capítulo.
 * @property content Cuerpo del capítulo en HTML.
 */
data class BookChapter(
    val title: String,
    val content: String,
)
