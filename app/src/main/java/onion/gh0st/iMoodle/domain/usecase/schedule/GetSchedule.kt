package onion.gh0st.iMoodle.domain.usecase.schedule

import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener el horario semanal.
 *
 * ## Caché
 *
 * El repositorio real guarda el horario por usuario. Con
 * `forceRefresh = false` devuelve la copia local si existe; con
 * `true` vuelve a consultar Pasen.
 *
 * ## Manejo de errores
 *
 * Propaga la excepción. El ViewModel la registra y avisa a la
 * pantalla; el horario anterior sigue visible.
 *
 * @param scheduleRepository Contrato de acceso al horario.
 */
class GetSchedule @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) {

    /**
     * Carga el horario.
     *
     * @param forceRefresh `true` para ignorar la caché y consultar la
     *   red.
     * @return Lista de franjas del horario.
     */
    suspend operator fun invoke(forceRefresh: Boolean = false): List<ScheduleSlot> =
        scheduleRepository.getSchedule(forceRefresh)
}
