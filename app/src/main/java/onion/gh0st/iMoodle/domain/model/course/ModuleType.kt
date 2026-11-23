package onion.gh0st.iMoodle.domain.model.course

/**
 * Clasificación de un módulo de Moodle según su tipo de actividad.
 *
 * ## Qué es esto
 *
 * Es un `sealed interface` (interfaz sellada) que representa los
 * distintos tipos de módulos que puede haber en un curso. Cada tipo
 * envuelve el `CourseModule` original, para que la UI pueda acceder a
 * sus datos.
 *
 * ## Por qué es `sealed`
 *
 * Porque permite a la UI hacer un `when` **exhaustivo**: el
 * compilador obliga a contemplar todos los tipos. Si en el futuro se
 * añade un tipo nuevo (por ejemplo, `Quiz`), cualquier `when` que no
 * lo contemple dará error de compilación. Así es imposible olvidarse
 * de actualizar la UI cuando se añade un tipo.
 *
 * ## Tipos disponibles
 *
 * - **`Assignment`**: tarea entregable.
 * - **`Forum`**: foro de discusión.
 * - **`Glossary`**: glosario de términos editable.
 * - **`Wiki`**: conjunto de páginas colaborativas.
 * - **`Resource`**: recurso descargable (uno o varios archivos).
 * - **`Folder`**: carpeta con archivos.
 * - **`Url`**: enlace externo a un sitio web.
 * - **`Page`**: página de contenido (apunte) en HTML.
 * - **`Book`**: libro con capítulos en HTML.
 * - **`Label`**: etiqueta de contenido inline (no navegable).
 * - **`Other`**: cualquier otro tipo (quiz, scorm, lesson…), que
 *   se muestra con una pantalla genérica.
 *
 * ## Quién lo usa
 *
 * - `GetCourseContents` lo construye a partir de `CourseModule`.
 * - `CourseDetailViewModel` lo usa para construir los items del
 *   adapter.
 * - `CourseDetailAdapter` lo usa para decidir qué icono mostrar.
 * - `CourseDetailFragment` lo usa para decidir a qué pantalla
 *   navegar al pulsar el módulo.
 */
sealed interface ModuleType {

    /**
     * Módulo original de Moodle. Todas las implementaciones lo
     * exponen, para que la UI pueda acceder a sus datos sin tener
     * que hacer un cast.
     */
    val module: CourseModule

    /**
     * Tarea entregable (`modname = "assign"`).
     *
     * @property module Módulo original.
     */
    data class Assignment(override val module: CourseModule) : ModuleType

    /**
     * Foro de discusión (`modname = "forum"`).
     *
     * @property module Módulo original.
     */
    data class Forum(override val module: CourseModule) : ModuleType

    /**
     * Glosario de términos (`modname = "glossary"`).
     *
     * La app permite consultar sus entradas y, si el curso lo permite,
     * añadirlas, editarlas y borrarlas.
     *
     * @property module Módulo original.
     */
    data class Glossary(override val module: CourseModule) : ModuleType

    /**
     * Wiki de páginas (`modname = "wiki"`).
     *
     * La app permite leer sus páginas y editarlas cuando Moodle da
     * permiso.
     *
     * @property module Módulo original.
     */
    data class Wiki(override val module: CourseModule) : ModuleType

    /**
     * Recurso descargable (`modname = "resource"`).
     *
     * Puede tener uno o varios archivos. Si tiene uno, la UI lo
     * descarga directamente; si tiene varios, abre la pantalla de
     * archivos.
     *
     * @property module Módulo original.
     */
    data class Resource(override val module: CourseModule) : ModuleType

    /**
     * Carpeta con archivos (`modname = "folder"`).
     *
     * @property module Módulo original.
     */
    data class Folder(override val module: CourseModule) : ModuleType

    /**
     * Enlace externo a un sitio web (`modname = "url"`).
     *
     * @property module Módulo original.
     */
    data class Url(override val module: CourseModule) : ModuleType

    /**
     * Página de contenido (`modname = "page"`).
     *
     * @property module Módulo original.
     */
    data class Page(override val module: CourseModule) : ModuleType

    /**
     * Libro con capítulos (`modname = "book"`).
     *
     * @property module Módulo original.
     */
    data class Book(override val module: CourseModule) : ModuleType

    /**
     * Etiqueta de contenido inline (`modname = "label"`).
     *
     * No es navegable: se muestra como texto dentro del curso.
     *
     * @property module Módulo original.
     * @property content Contenido de la etiqueta (en HTML).
     */
    data class Label(
        override val module: CourseModule,
        val content: String,
    ) : ModuleType

    /**
     * Cualquier otro tipo de módulo (`quiz`, `book`, `page`…).
     *
     * Se muestra con una pantalla genérica que solo muestra el nombre
     * y la descripción.
     *
     * @property module Módulo original.
     */
    data class Other(override val module: CourseModule) : ModuleType

    /**
     * Devuelve una copia de este tipo con otro módulo subyacente.
     *
     * ## Para qué sirve
     *
     * Cuando el alumno marca o desmarca la finalización de una
     * actividad, el `CourseModule` cambia pero el tipo sigue siendo el
     * mismo. Este método evita tener que repetir un `when` con los
     * nueve tipos cada vez que hay que actualizar un módulo.
     *
     * @param module Módulo actualizado.
     * @return El mismo tipo, envolviendo el módulo nuevo.
     */
    fun withModule(module: CourseModule): ModuleType = when (this) {
        is Assignment -> copy(module = module)
        is Forum -> copy(module = module)
        is Glossary -> copy(module = module)
        is Wiki -> copy(module = module)
        is Resource -> copy(module = module)
        is Folder -> copy(module = module)
        is Url -> copy(module = module)
        is Page -> copy(module = module)
        is Book -> copy(module = module)
        is Label -> copy(module = module)
        is Other -> copy(module = module)
    }

    companion object {

        /**
         * Clasifica un módulo según su `modname`.
         *
         * ## Qué hace
         *
         * Mira el `modname` del módulo y devuelve el `ModuleType`
         * correspondiente. Si no reconoce el `modname`, devuelve
         * `Other`.
         *
         * ## Cuándo se llama
         *
         * - En `GetCourseContents`, al transformar `CourseContent` en
         *   `CourseSection`.
         *
         * @param module Módulo de Moodle ya parseado.
         * @return El `ModuleType` correspondiente.
         */
        fun from(module: CourseModule): ModuleType = when (module.modname) {
            "assign" -> Assignment(module)
            "forum" -> Forum(module)
            "glossary" -> Glossary(module)
            "wiki" -> Wiki(module)
            "resource" -> Resource(module)
            "folder" -> Folder(module)
            "url" -> Url(module)
            "page" -> Page(module)
            "book" -> Book(module)
            "label" -> Label(module, module.description.orEmpty())
            else -> Other(module)
        }
    }
}
