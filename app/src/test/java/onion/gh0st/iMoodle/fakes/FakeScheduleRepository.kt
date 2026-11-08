package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository

/**
 * Implementación en memoria de [ScheduleRepository] para tests.
 *
 * @property slots Slots a devolver por [getSchedule].
 * @property error Si no es `null`, se lanza al llamar a [getSchedule].
 */
class FakeScheduleRepository : ScheduleRepository {

    /** Slots a devolver por [getSchedule]. */
    var slots: List<ScheduleSlot> = emptyList()

    /** Si no es `null`, se lanza al llamar a [getSchedule]. */
    var error: Throwable? = null

    /** Número de veces que se ha llamado a [getSchedule]. */
    var getScheduleCalls: Int = 0
        private set

    /** Último valor de `forceRefresh` recibido. */
    var lastForceRefresh: Boolean? = null
        private set

    /**
     * Devuelve [slots] o lanza [error].
     *
     * @param forceRefresh Si `true`, el fake lo registra en
     *   [lastForceRefresh].
     * @return La lista de slots configurada.
     * @throws Throwable si [error] no es `null`.
     */
    override suspend fun getSchedule(forceRefresh: Boolean): List<ScheduleSlot> {
        getScheduleCalls++
        lastForceRefresh = forceRefresh
        error?.let { throw it }
        return slots
    }
}