package onion.gh0st.iMoodle.data.favourites

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación que guarda los favoritos en el propio teléfono.
 *
 * ## Cómo funciona
 * Utiliza una herramienta de Android llamada `SharedPreferences`, que
 * es como un pequeño cuaderno de notas privado para la aplicación.
 * Cuando el usuario marca un curso como favorito, apuntamos su
 * identificador en ese cuaderno. Cuando quiere saber cuáles son sus
 * favoritos, leemos el cuaderno.
 *
 * ## Por qué se hace así
 * La función oficial de Moodle para guardar favoritos no funciona de
 * forma fiable para aplicaciones externas. Al guardarlos en el
 * teléfono, nos aseguramos de que la funcionalidad siempre funcione,
 * incluso si no hay conexión a internet.
 */
@Singleton
class LocalFavouritesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : FavouritesRepository {

    private val sharedPreferences =
        context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    /**
     * Lee la lista de identificadores de cursos favoritos del cuaderno.
     *
     * @return Un resultado con el conjunto de identificadores.
     */
    override suspend fun getFavouriteCourseIds(): Result<Set<Int>> = runCatching {
        sharedPreferences.getStringSet("course_ids", emptySet())
            ?.mapNotNull { idString -> idString.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Guarda o borra un curso de la lista de favoritos en el cuaderno.
     *
     * @param courseId El identificador del curso.
     * @param isFavourite `true` para guardarlo, `false` para borrarlo.
     * @return Un resultado que indica si la operación fue exitosa.
     */
    override suspend fun setCourseFavourite(courseId: Int, isFavourite: Boolean): Result<Unit> = runCatching {
        val currentIds = sharedPreferences
            .getStringSet("course_ids", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

        if (isFavourite) {
            currentIds.add(courseId.toString())
        } else {
            currentIds.remove(courseId.toString())
        }

        sharedPreferences.edit().putStringSet("course_ids", currentIds).apply()
        Unit
    }
}