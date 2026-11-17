package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.model.session.ActiveSession
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: intentar recuperar la sesión guardada al arrancar.
 *
 * ## Qué hace
 *
 * Lee la sesión persistida, valida el token contra Moodle y, si sigue
 * siendo válido, reconstruye la sesión viva y la publica.
 *
 * ## Resultado
 *
 * - `Result.success(sesión)` si se ha recuperado.
 * - `Result.success(null)` si no había sesión guardada o ya no es
 *   válida. **No es un error**: es el caso normal de un primer
 *   arranque o de un logout previo.
 * - `Result.failure(...)` si ocurrió un fallo inesperado.
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class TryAutoLogin @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Intenta el auto-login.
     *
     * @return `Result` con la sesión recuperada, con `null` si no hay,
     *   o con el error.
     */
    suspend operator fun invoke(): Result<ActiveSession?> = sessionRepo.tryAutoLogin()
}
