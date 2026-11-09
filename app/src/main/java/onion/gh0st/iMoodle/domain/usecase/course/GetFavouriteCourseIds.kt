package onion.gh0st.iMoodle.domain.usecase.course

import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import javax.inject.Inject

/**
 * Caso de uso para obtener los cursos favoritos.
 *
 * ## Qué hace
 * Pide al repositorio de datos la lista de identificadores de los
 * cursos que el usuario ha marcado con una estrella.
 */
class GetFavouriteCourseIds @Inject constructor(
    private val favouritesRepository: FavouritesRepository,
) {

    /**
     * Ejecuta la obtención.
     *
     * @return Un resultado con el conjunto de identificadores.
     */
    suspend operator fun invoke(): Result<Set<Int>> =
        favouritesRepository.getFavouriteCourseIds()
}