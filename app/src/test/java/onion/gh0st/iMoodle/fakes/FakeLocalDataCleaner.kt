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
    override suspend fun clear() {
        clearCalls++
    }
}