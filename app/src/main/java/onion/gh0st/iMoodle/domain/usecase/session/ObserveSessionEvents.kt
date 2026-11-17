package onion.gh0st.iMoodle.domain.usecase.session

import kotlinx.coroutines.flow.SharedFlow
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: escuchar los eventos puntuales de la sesión.
 *
 * ## Qué eventos llegan
 *
 * - `SessionEvent.Expired`: la sesión ha dejado de ser válida y la UI
 *   debe volver al login.
 * - `SessionEvent.TokenRefreshed`: el token de Moodle se ha renovado.
 *
 * ## Por qué no vale solo con [ObserveSession]
 *
 * Porque el estado (`null` / no `null`) dice **qué** hay ahora, pero
 * no **por qué** cambió. La UI necesita distinguir un cierre
 * voluntario (logout, que ya navega por su cuenta) de una expiración
 * involuntaria (que debe redirigir aunque el usuario esté en otra
 * pantalla). Los eventos son de un solo uso: un `SharedFlow` sin
 * `replay`, para no repetir navegaciones al rotar.
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class ObserveSessionEvents @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Flujo de eventos de sesión.
     *
     * Es el mismo `SharedFlow` del repositorio, sin transformar: quien
     * lo colecciona decide qué eventos le interesan.
     */
    val events: SharedFlow<SessionEvent> = sessionRepo.events
}
