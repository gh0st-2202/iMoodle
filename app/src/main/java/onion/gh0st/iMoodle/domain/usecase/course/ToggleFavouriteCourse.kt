package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import javax.inject.Inject

/**
 * Caso de uso para marcar o desmarcar un curso como favorito.
 *
 * ## Qué hace
 * Pide al repositorio de datos que cambie el estado de un curso
 * concreto. Si estaba marcado, lo quita; si no lo estaba, lo añade.
 */
class ToggleFavouriteCourse @Inject constructor(
    private val favouritesRepository: FavouritesRepository,
) {

    /**
     * Ejecuta la acción de marcar o desmarcar.
     *
     * @param courseId El identificador del curso.
     * @param isFavourite El nuevo estado que se desea aplicar.
     * @return Un resultado indicando si la operación fue exitosa.
     */
    suspend operator fun invoke(courseId: Int, isFavourite: Boolean): Result<Unit> =
        favouritesRepository.setCourseFavourite(courseId, isFavourite)
}