package onion.gh0st.iMoodle.domain.repository.schedule

import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot

/**
 * Contrato de acceso al horario del alumno.
 *
 * ## Qué es el horario
 *
 * Es la lista de asignaturas que el alumno tiene cada día de la
 * semana, con sus horas y profesores. Por ejemplo:
 *
 * ```
 * Lunes 8:30 - 9:30: Acceso a datos (García, Juan)
 * Lunes 9:30 - 10:30: Acceso a datos (García, Juan)
 * Lunes 10:30 - 11:30: Desarrollo de interfaces (Pérez, Ana)
 * ...
 * ```
 *
 * ## De dónde vienen los datos
 *
 * Del endpoint `get-horario` de Pasen. El horario es **recurrente**:
 * no cambia de una semana a otra (salvo cambios puntuales que el
 * centro comunique). Por eso la app lo cachea: la primera vez que el
 * usuario entra en la pestaña, se pide a Pasen; las siguientes, se
 * lee de la caché.
 *
 * ## Estrategia cache-first
 *
 * El parámetro [getSchedule] `forceRefresh` controla si se usa la
 * caché:
 *
 * - **`forceRefresh = false`** (lo normal): si hay caché, se
 *   devuelve. Si no, se pide a Pasen y se guarda en caché.
 * - **`forceRefresh = true`** (pull-to-refresh): se ignora la caché y
 *   se pide a Pasen siempre.
 *
 * ## Quién la implementa
 *
 * - **Producción**: `PasenScheduleRepository` (con caché).
 * - **Tests**: `FakeScheduleRepository` (devuelve datos
 *   controlados).
 *
 * ## Quién la consume
 *
 * - `ScheduleViewModel`.
 */
interface ScheduleRepository {

    /**
     * Devuelve el horario del usuario autenticado.
     *
     * ## Comportamiento
     *
     * - Si `forceRefresh = false` y hay caché, la devuelve.
     * - Si no hay caché, o `forceRefresh = true`, va a Pasen y
     *   actualiza la caché.
     *
     * ## Cuándo lanza excepción
     *
     * - Si no hay credenciales guardadas (el usuario no ha hecho
     *   login).
     * - Si falla el login a Pasen.
     * - Si falla la petición del horario.
     * - Si el JSON no se puede parsear.
     *
     * @param forceRefresh Si `true`, ignora la caché y va a la red.
     * @return Lista de slots del horario.
     */
    suspend fun getSchedule(forceRefresh: Boolean = false): List<ScheduleSlot>
}