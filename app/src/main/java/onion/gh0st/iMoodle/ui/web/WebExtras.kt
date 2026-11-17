package onion.gh0st.iMoodle.ui.web

/**
 * Nombres de los datos extra (`Intent` extras) que reciben las pantallas
 * de navegador integrado ([WebActivity] y [MoodleWebViewActivity]).
 *
 * Centralizarlos evita repetir los textos a mano en quien lanza la
 * pantalla y en quien la recibe.
 */
object WebExtras {

    /** URL que se debe cargar. */
    const val URL = "URL"

    /** Título que se muestra en la barra superior. */
    const val TITLE = "TITLE"
}
