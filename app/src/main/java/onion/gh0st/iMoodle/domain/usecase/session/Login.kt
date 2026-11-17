package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.model.session.CasCredentials
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: iniciar sesión con las credenciales de la Junta.
 *
 * ## Qué hace
 *
 * Delega en [SessionRepository.login], que ejecuta el flujo completo:
 * CAS → Moodle → persistencia de la sesión y las credenciales →
 * construcción de la sesión viva → carga del perfil.
 *
 * ## Manejo de errores
 *
 * Devuelve un `Result`. Los fallos esperables (credenciales
 * incorrectas, red, servidor) llegan como `AuthException` dentro del
 * `Result`; el ViewModel los traduce a mensajes para el usuario. Este
 * caso de uso no captura ni transforma nada: solo es la puerta de
 * entrada del dominio.
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class Login @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Ejecuta el login.
     *
     * @param provincia Provincia contra la que autenticarse.
     * @param credentials Credenciales de la Junta.
     * @return `Result` con la sesión activa o con el error.
     */
    suspend operator fun invoke(
        provincia: Provincia,
        credentials: CasCredentials,
    ): Result<ActiveSession> = sessionRepo.login(provincia, credentials)
}
