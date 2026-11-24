package onion.gh0st.iMoodle.ui.common

/**
 * Nombres de los argumentos que se pasan a los fragmentos de detalle.
 *
 * ## Por qué existe
 *
 * Un fragmento recibe sus datos en un `Bundle` identificados por un nombre
 * (`"COURSE_ID"`, `"FORUM_ID"`…). Ese mismo nombre se escribe en
 * `newInstance(...)` y se lee después en el fragmento y en su
 * `ViewModel` (a través de `SavedStateHandle`). Con el texto repetido a
 * mano, un error tipográfico en un solo sitio hace que el dato llegue
 * como `0` sin que el compilador avise. Con estas constantes, el
 * compilador sí lo detecta.
 *
 * Los valores son exactamente los textos que ya se usaban, de modo que
 * los `SavedStateHandle` de los tests siguen funcionando.
 */
internal object FragmentArgs {

    /** Identificador del curso. */
    const val COURSE_ID = "COURSE_ID"

    /** Identificador de instancia de un módulo (page, book…). */
    const val MODULE_INSTANCE_ID = "MODULE_INSTANCE_ID"

    /** Nombre del curso (se muestra en la barra superior). */
    const val COURSE_NAME = "COURSE_NAME"

    /** Identificador del módulo del curso (`cmid`). */
    const val MODULE_ID = "MODULE_ID"

    /** Nombre del módulo. */
    const val MODULE_NAME = "MODULE_NAME"

    /** Descripción del módulo (HTML). */
    const val MODULE_DESCRIPTION = "MODULE_DESCRIPTION"

    /** URL del módulo (para cuestionarios y otros enlaces). */
    const val MODULE_URL = "MODULE_URL"

    /** Identificador de un intento de cuestionario. */
    const val ATTEMPT_ID = "ATTEMPT_ID"

    /** Identificador del foro. */
    const val FORUM_ID = "FORUM_ID"

    /** Nombre del foro. */
    const val FORUM_NAME = "FORUM_NAME"

    /** Identificador de la discusión de un foro. */
    const val DISCUSSION_ID = "DISCUSSION_ID"

    /** Nombre (asunto) de la discusión. */
    const val DISCUSSION_NAME = "DISCUSSION_NAME"

    /** Identificador de la tarea (su `id` de instancia en Moodle). */
    const val ASSIGNMENT_ID = "ASSIGNMENT_ID"

    /** Identificador de la instancia del glosario. */
    const val GLOSSARY_ID = "GLOSSARY_ID"

    /** Identificador de la instancia de la wiki. */
    const val WIKI_ID = "WIKI_ID"

    /** Identificador de una página de wiki. */
    const val WIKI_PAGE_ID = "WIKI_PAGE_ID"

    /** Título de una página de wiki (se muestra mientras carga). */
    const val WIKI_PAGE_TITLE = "WIKI_PAGE_TITLE"
}
