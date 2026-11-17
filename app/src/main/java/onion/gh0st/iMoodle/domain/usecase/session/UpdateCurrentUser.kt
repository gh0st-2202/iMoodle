package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: actualizar el usuario de la sesión activa.
 *
 * ## Cuándo se usa
 *
 * Cuando la pantalla de perfil consigue datos más completos (por
 * ejemplo, los campos personales de Pasen) y quiere que el resto de
 * la app vea la versión enriquecida sin volver a pedirla.
 *
 * ## Por qué no es `suspend`
 *
 * Porque solo escribe un `StateFlow` en memoria: no hay red ni disco
 * que esperar. La persistencia de la sesión la decide el repositorio.
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class UpdateCurrentUser @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Publica el usuario indicado como el usuario actual.
     *
     * @param user Usuario actualizado (identidad + campos de Pasen).
     */
    operator fun invoke(user: User) {
        sessionRepo.updateUser(user)
    }
}
