package onion.gh0st.iMoodle.domain.repository.course

/**
 * Contrato para gestionar los cursos favoritos del usuario.
 *
 * ## Por qué existe
 * Guardar los cursos favoritos es una regla del negocio. Esta interfaz
 * define qué se puede hacer (leer los favoritos y alternar su estado),
 * pero no cómo se guardan (en el teléfono, en internet, etc.).
 */
interface FavouritesRepository {

    /**
     * Obtiene los identificadores de los cursos favoritos.
     *
     * @return Un resultado con el conjunto de identificadores.
     */
    suspend fun getFavouriteCourseIds(): Result<Set<Int>>

    /**
     * Marca o desmarca un curso como favorito.
     *
     * @param courseId El identificador del curso.
     * @param isFavourite `true` para marcarlo como favorito, `false`
     *   para quitarlo de la lista.
     * @return Un resultado que indica si la operación fue exitosa.
     */
    suspend fun setCourseFavourite(courseId: Int, isFavourite: Boolean): Result<Unit>
}