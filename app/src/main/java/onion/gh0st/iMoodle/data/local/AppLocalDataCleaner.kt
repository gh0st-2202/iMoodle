package onion.gh0st.iMoodle.data.local

import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.data.schedule.ScheduleCache
import onion.gh0st.iMoodle.data.session.UserProfileCache

/**
 * Limpiador que borra **todos** los datos locales del usuario al cerrar
 * sesión: la base de datos Room y las cachés de horario y perfil.
 *
 * ## Qué problema resuelve
 *
 * Hasta ahora solo se vaciaba Room. El horario, el centro, la fecha de
 * nacimiento y demás datos personales de Pasen seguían en
 * `SharedPreferences` después de cerrar sesión, aunque el README
 * prometía lo contrario. Esta clase agrupa todos los borrados detrás de
 * la misma interfaz [LocalDataCleaner], de modo que quien cierra sesión
 * (`DefaultSessionRepository`) no tiene que conocer cada almacén.
 *
 * ## Qué **no** borra
 *
 * Los cursos favoritos: son una preferencia del usuario (no datos
 * personales) y se pierden demasiado fácilmente si la sesión caduca y
 * hay que volver a entrar.
 *
 * ## Tolerancia a fallos
 *
 * Los pasos son independientes: si uno falla (por ejemplo, la base de
 * datos), se ejecutan igualmente los demás y, al final, se relanza el
 * primer error. Así un fallo en un almacén no deja datos personales de
 * otro sin borrar.
 *
 * @param databaseCleaner Limpiador de la base de datos (normalmente [RoomLocalDataCleaner]).
 * @param scheduleCache Caché del horario de iPasen.
 * @param userProfileCache Caché del perfil ampliado del alumno.
 */
class AppLocalDataCleaner(
    private val databaseCleaner: LocalDataCleaner,
    private val scheduleCache: ScheduleCache,
    private val userProfileCache: UserProfileCache,
) : LocalDataCleaner {

    /**
     * Borra la base de datos y las cachés, intentando todos los pasos
     * aunque alguno falle.
     *
     * @throws Throwable el primer error ocurrido, si lo hubo, después de
     *   haber intentado todos los pasos.
     */
    override suspend fun clear() {
        var firstFailure: Throwable? = null

        fun recordFailure(stepName: String, failure: Throwable) {
            Logging.logError(TAG, "clear:$stepName", failure)
            if (firstFailure == null) firstFailure = failure
        }

        runCatchingCancellable { databaseCleaner.clear() }
            .onFailure { failure -> recordFailure("database", failure) }
        runCatchingCancellable { scheduleCache.clearAll() }
            .onFailure { failure -> recordFailure("scheduleCache", failure) }
        runCatchingCancellable { userProfileCache.clearAll() }
            .onFailure { failure -> recordFailure("userProfileCache", failure) }

        firstFailure?.let { failure -> throw failure }
    }

    private companion object {
        const val TAG = "AppLocalDataCleaner"
    }
}
