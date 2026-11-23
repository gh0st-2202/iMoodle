package onion.gh0st.iMoodle.domain.model.page

/**
 * Contenido de una página de Moodle (`mod_page`).
 *
 * ## Qué es una página
 *
 * En Moodle, una "Página" (a veces llamada "Apunte") es un módulo
 * que contiene texto enriquecido (HTML) que el profesor escribe
 * directamente en Moodle. Es la forma más sencilla de mostrar
 * información al alumno sin archivos externos.
 *
 * ## Por qué es un modelo propio
 *
 * Moodle devuelve la página dentro del listado de contenidos del
 * curso sin su cuerpo (solo el nombre y la descripción). El cuerpo
 * hay que pedirlo aparte con `mod_page_get_pages_by_courses`. Este
 * modelo encapsula el resultado de esa petición.
 *
 * @property name Nombre visible de la página.
 * @property content Cuerpo de la página en HTML.
 */
data class PageContent(
    val name: String,
    val content: String,
)
