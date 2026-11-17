package onion.gh0st.iMoodle.fakes

import kotlinx.coroutines.CompletableDeferred
import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository

/**
 * Repositorio de favoritos en memoria para los tests.
 *
 * Guarda los identificadores en un conjunto y permite simular un fallo
 * de lectura o de escritura asignando [readError] o [writeError].
 *
 * @param initialFavourites Favoritos con los que arranca el fake.
 */
class FakeFavouritesRepository(initialFavourites: Set<Int> = emptySet()) : FavouritesRepository {

    private val favourites = initialFavourites.toMutableSet()

    /** Si no es `null`, [getFavouriteCourseIds] falla con este error. */
    var readError: Throwable? = null

    /** Si no es `null`, [setCourseFavourite] falla con este error. */
    var writeError: Throwable? = null

    /** Número de veces que se ha leído la lista de favoritos. */
    var readCalls: Int = 0
        private set

    /** Número de escrituras intentadas (aunque esperen a [writeGate]). */
    var writeCalls: Int = 0
        private set

    /**
     * Puerta opcional para retener una escritura.
     *
     * Si no es `null`, [setCourseFavourite] espera a que se complete
     * antes de modificar el conjunto. Sirve para simular una escritura
     * lenta y comprobar que una segunda pulsación no se ejecuta en
     * paralelo.
     */
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun getFavouriteCourseIds(): Result<Set<Int>> {
        readCalls++
        return readError?.let { error -> Result.failure(error) } ?: Result.success(favourites.toSet())
    }

    override suspend fun setCourseFavourite(courseId: Int, isFavourite: Boolean): Result<Unit> {
        writeCalls++
        writeGate?.await()
        writeError?.let { error -> return Result.failure(error) }
        if (isFavourite) favourites.add(courseId) else favourites.remove(courseId)
        return Result.success(Unit)
    }
}
