package onion.gh0st.iMoodle.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Borra los datos de usuario cacheados en local.
 *
 * ## Qué borra
 *
 * Todos los datos de la base de datos local: cursos, tareas y
 * eventos de calendario.
 *
 * ## Cuándo se llama
 *
 * - **Al hacer logout**: para que el siguiente usuario del
 *   dispositivo no vea los datos del anterior.
 * - **Al invalidar la sesión**: cuando el token deja de valer y no
 *   se puede refrescar.
 *
 * ## Por qué es importante
 *
 * Las tablas `courses` y `assignments` están indexadas por `userId`,
 * así que en teoría dos usuarios no se mezclarían. Pero la tabla
 * `calendar_events` **no** está indexada por usuario (se reemplaza
 * entera en cada sincronización). Sin un borrado explícito al hacer
 * logout, el siguiente usuario vería los eventos del anterior.
 *
 * Además, aunque las tablas estuvieran indexadas, dejar datos
 * cacheados de un usuario en el dispositivo después de un logout es
 * una mala práctica de privacidad. Mejor borrarlos.
 *
 * ## Por qué es una interfaz
 *
 * Mismo motivo que `TransactionRunner`: en tests queremos sustituir
 * la implementación real (que necesita una base de datos) por una
 * falsa que solo cuente las llamadas.
 */
fun interface LocalDataCleaner {

    /**
     * Borra todos los datos de usuario cacheados en local.
     *
     * ## Comportamiento
     *
     * En la implementación real, borra **todas las tablas** de la
     * base de datos. Es un borrado total: no hay que preocuparse por
     * preservar nada, porque todo lo que hay en la base de datos es
     * datos de usuario.
     */
    suspend fun clear()
}

/**
 * Implementación real sobre Room.
 *
 * ## Cómo funciona
 *
 * Llama a `database.clearAllTables()`, una función de Room que borra
 * el contenido de todas las tablas de golpe. Es mucho más rápido que
 * hacer `DELETE FROM` tabla por tabla.
 *
 * ## Por qué se ejecuta en `Dispatchers.IO`
 *
 * Porque borrar tablas implica escribir en disco, y eso puede tardar
 * (sobre todo si hay muchos datos). Hacerlo en el hilo principal
 * congelaría la UI.
 *
 * @param database Base de datos que se va a limpiar.
 */
class RoomLocalDataCleaner(private val database: AppDatabase) : LocalDataCleaner {
    override suspend fun clear() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}