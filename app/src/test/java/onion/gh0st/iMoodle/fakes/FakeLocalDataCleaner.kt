package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.data.local.LocalDataCleaner

/**
 * [LocalDataCleaner] de test que cuenta las llamadas a [clear].
 *
 * Útil para verificar que el logout limpia los datos locales.
 */
class FakeLocalDataCleaner : LocalDataCleaner {

    /** Número de veces que se ha llamado a [clear]. */
    var clearCalls: Int = 0
        private set

    /**
     * Incrementa [clearCalls]. No borra nada real.
     */
    /** Si no es `null`, [clear] cuenta la llamada y lanza este error (simula un fallo de disco). */
    var error: Throwable? = null

    /**
     * Acción opcional que se ejecuta al final de [clear].
     *
     * Permite simular que otra corrutina (por ejemplo, un refresco de
     * token) reacciona al cierre de sesión, incluso desde un hilo
     * distinto.
     */
    var onClear: (suspend () -> Unit)? = null

    override suspend fun clear() {
        clearCalls++
        onClear?.invoke()
        error?.let { throw it }
    }
}