package onion.gh0st.iMoodle.data.local

import androidx.room.withTransaction

/**
 * Ejecutor de bloques como una sola transacción de base de datos.
 *
 * ## Qué es una transacción
 *
 * En una base de datos, una transacción es un conjunto de operaciones
 * que se ejecutan **como una unidad atómica**: o se hacen todas, o no
 * se hace ninguna. Si una falla, las anteriores se deshacen (rollback)
 * y la base de datos queda como estaba.
 *
 * ## Por qué hacen falta transacciones en esta app
 *
 * Algunos flujos hacen dos operaciones seguidas que deben ir juntas.
 * Por ejemplo, al refrescar el calendario:
 *
 * 1. Borrar todos los eventos anteriores.
 * 2. Insertar los nuevos.
 *
 * Si esas dos operaciones se hicieran por separado, podrían pasar dos
 * cosas malas:
 *
 * - **La UI vería un instante con la lista vacía**: porque entre el
 *   paso 1 y el 2, la base de datos no tiene eventos. La agenda
 *   parpadearía.
 * - **Si el paso 2 fallara, el usuario se quedaría sin eventos**:
 *   porque el paso 1 ya habría borrado los antiguos.
 *
 * Con una transacción, ambas operaciones se hacen como una sola. La
 * UI no ve el estado intermedio, y si algo falla, se deshace todo.
 *
 * ## Por qué es una interfaz
 *
 * Porque en tests queremos sustituir la implementación real (que usa
 * Room) por una falsa que no necesite una base de datos real. Al ser
 * una interfaz, podemos inyectar cualquiera de las dos.
 */
interface TransactionRunner {

    /**
     * Ejecuta un bloque dentro de una transacción.
     *
     * ## Comportamiento
     *
     * - Si el bloque termina bien, se hace `commit` (los cambios se
     *   guardan).
     * - Si el bloque lanza una excepción, se hace `rollback` (los
     *   cambios se descartan).
     *
     * @param block Bloque a ejecutar dentro de la transacción.
     * @return El resultado del bloque.
     */
    suspend fun <R> run(block: suspend () -> R): R
}

/**
 * Implementación para tests y fakes en memoria: ejecuta el bloque tal
 * cual, sin transacción real.
 *
 * ## Cuándo se usa
 *
 * - En tests unitarios, donde no hay una base de datos real.
 * - En el constructor por defecto de los repositorios, para que
 *   puedan instanciarse sin Room.
 *
 * ## Qué hace
 *
 * Simplemente llama al bloque y devuelve su resultado. No hay
 * `commit`, no hay `rollback`, no hay nada. Es un "no-op" (no
 * operación).
 */
object InlineTransactionRunner : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

/**
 * Implementación real sobre Room.
 *
 * ## Cómo funciona
 *
 * Delega en `database.withTransaction`, una función de extensión de
 * Room que abre una transacción, ejecuta el bloque y hace `commit` o
 * `rollback` según el resultado.
 *
 * ## Cuándo se usa
 *
 * En producción, inyectado por Hilt en `DatabaseModule`.
 *
 * @param database Base de datos sobre la que se ejecutan las
 *   transacciones.
 */
class RoomTransactionRunner(private val database: AppDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R =
        database.withTransaction { block() }
}