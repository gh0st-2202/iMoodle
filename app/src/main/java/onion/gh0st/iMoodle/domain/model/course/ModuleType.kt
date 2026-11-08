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
 * - **`Resource`**: recurso descargable (uno o varios archivos).
 * - **`Folder`**: carpeta con archivos.
 * - **`Url`**: enlace externo a un sitio web.
 * - **`Label`**: etiqueta de contenido inline (no navegable).
 * - **`Other`**: cualquier otro tipo (quiz, wiki, book, page…), que
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
     * Cualquier otro tipo de módulo (`quiz`, `wiki`, `book`, `page`…).
     *
     * Se muestra con una pantalla genérica que solo muestra el nombre
     * y la descripción.
     *
     * @property module Módulo original.
     */
    data class Other(override val module: CourseModule) : ModuleType

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
            "resource" -> Resource(module)
            "folder" -> Folder(module)
            "url" -> Url(module)
            "label" -> Label(module, module.description.orEmpty())
            else -> Other(module)
        }
    }
}