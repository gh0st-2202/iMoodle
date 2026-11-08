package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.data.local.TransactionRunner

/**
 * [TransactionRunner] de test que registra cuántas transacciones se
 * abrieron y permite saber si una operación ocurre **dentro** de una.
 */
class RecordingTransactionRunner : TransactionRunner {

    /** `true` mientras se ejecuta el bloque de una transacción. */
    var active: Boolean = false
        private set

    /** Número de transacciones ejecutadas. */
    var runs: Int = 0
        private set

    /**
     * Ejecuta el bloque marcando [active] durante su ejecución.
     *
     * @param block Bloque a ejecutar.
     * @return El resultado del bloque.
     */
    override suspend fun <R> run(block: suspend () -> R): R {
        runs++
        active = true
        try {
            return block()
        } finally {
            active = false
        }
    }
}