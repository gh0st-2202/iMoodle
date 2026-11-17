package onion.gh0st.iMoodle.domain.usecase.session

import kotlinx.coroutines.flow.StateFlow
import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: observar la sesión activa.
 *
 * ## Qué ofrece
 *
 * Dos formas de leer la misma información, pensadas para momentos
 * distintos:
 *
 * - [session]: un `StateFlow` para **reaccionar a los cambios** (la UI
 *   se suscribe y se entera cuando el usuario entra o sale).
 * - [currentSession] y [currentUserId]: lecturas **síncronas** del
 *   valor actual, para los pocos sitios donde no se puede esperar a
 *   una corrutina (por ejemplo, el primer `onCreate` de una Activity o
 *   el estado inicial de un ViewModel).
 *
 * ## Por qué es un caso de uso y no el repositorio
 *
 * Porque la UI y los ViewModels no deben conocer `SessionRepository`:
 * así el contrato de la capa de presentación es mínimo, se puede
 * sustituir la implementación sin tocar la UI y los tests no necesitan
 * construir un repositorio completo. El repositorio sigue siendo el
 * dueño del estado; este caso de uso solo lo expone.
 *
 * ## Qué NO hace
 *
 * No modifica la sesión. Para entrar, salir o actualizar al usuario
 * existen [Login], [Logout], [TryAutoLogin] y [UpdateCurrentUser].
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class ObserveSession @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Sesión activa, o `null` si no hay ninguna.
     *
     * Es el mismo `StateFlow` del repositorio: no se crea una copia,
     * para que el valor inicial esté disponible al instante y no haya
     * dos fuentes de verdad.
     */
    val session: StateFlow<ActiveSession?> = sessionRepo.session

    /**
     * Sesión activa en este instante, leída de forma síncrona.
     *
     * ## Cuándo usarlo
     *
     * Solo cuando no se puede observar el flujo: el primer `onCreate`
     * de una Activity o la inicialización de un ViewModel. En el resto
     * de casos, coleccionar [session] es más robusto.
     */
    val currentSession: ActiveSession?
        get() = sessionRepo.session.value

    /**
     * ID del usuario de Moodle de la sesión activa, o `null`.
     *
     * Es un atajo de [currentSession] para el patrón habitual de las
     * cachés locales, que se indexan por `userId`.
     */
    val currentUserId: Int?
        get() = currentSession?.user?.id
}
