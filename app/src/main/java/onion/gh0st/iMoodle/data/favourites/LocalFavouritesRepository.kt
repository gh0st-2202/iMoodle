package onion.gh0st.iMoodle.data.favourites

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación que guarda los cursos favoritos en el propio teléfono.
 *
 * ## Cómo funciona
 *
 * Utiliza `SharedPreferences`, un pequeño fichero privado de la app.
 * Cuando el usuario marca un curso como favorito, se añade su
 * identificador al conjunto guardado; al desmarcarlo, se quita. Para
 * saber cuáles son los favoritos, se lee ese conjunto.
 *
 * ## Por qué se guarda en local
 *
 * La función oficial de Moodle para guardar favoritos no funciona de
 * forma fiable para aplicaciones externas. Al guardarlos en el teléfono,
 * la funcionalidad siempre está disponible, incluso sin conexión.
 *
 * ## Detalles de implementación
 *
 * - Las lecturas y escrituras se hacen en `Dispatchers.IO`: la primera
 *   lectura de `SharedPreferences` carga el fichero desde disco y no debe
 *   bloquear el hilo principal.
 * - Marcar o desmarcar es un ciclo *leer, modificar, escribir*. Un
 *   [Mutex] garantiza que dos cambios simultáneos no se pisen entre sí.
 * - La cancelación de la corrutina no se interpreta como un fallo.
 *
 * @param context Contexto de aplicación inyectado por Hilt.
 */
@Singleton
class LocalFavouritesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : FavouritesRepository {

    private val sharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    /** Evita que dos modificaciones simultáneas del conjunto se sobrescriban. */
    private val writeMutex = Mutex()

    /**
     * Lee los identificadores de los cursos favoritos.
     *
     * @return Un resultado con el conjunto de identificadores (vacío si no hay ninguno).
     */
    override suspend fun getFavouriteCourseIds(): Result<Set<Int>> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                readStoredIds().mapNotNull { idString -> idString.toIntOrNull() }.toSet()
            }
        }

    /**
     * Marca o desmarca un curso como favorito.
     *
     * @param courseId Identificador del curso.
     * @param isFavourite `true` para marcarlo, `false` para desmarcarlo.
     * @return Un resultado que indica si la operación fue exitosa.
     */
    override suspend fun setCourseFavourite(courseId: Int, isFavourite: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                runCatchingCancellable {
                    val updatedIds = readStoredIds().toMutableSet()
                    if (isFavourite) {
                        updatedIds.add(courseId.toString())
                    } else {
                        updatedIds.remove(courseId.toString())
                    }
                    sharedPreferences.edit { putStringSet(KEY_COURSE_IDS, updatedIds) }
                }
            }
        }

    /**
     * Lee el conjunto guardado tal cual (identificadores como texto).
     *
     * @return El conjunto guardado, o uno vacío si no hay nada.
     */
    private fun readStoredIds(): Set<String> =
        sharedPreferences.getStringSet(KEY_COURSE_IDS, emptySet()).orEmpty()

    private companion object {
        /** Nombre del fichero de preferencias. */
        const val PREFERENCES_FILE_NAME = "favourites"

        /** Clave bajo la que se guarda el conjunto de identificadores. */
        const val KEY_COURSE_IDS = "course_ids"
    }
}
