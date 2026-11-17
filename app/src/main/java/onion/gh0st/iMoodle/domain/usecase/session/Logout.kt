package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import javax.inject.Inject

/**
 * Caso de uso: cerrar la sesión del usuario.
 *
 * ## Qué limpia
 *
 * El repositorio vacía la sesión en memoria, borra la sesión y las
 * credenciales persistidas, cancela las alarmas y limpia las cachés
 * locales (incluidas la del horario y la del perfil).
 *
 * ## Si algo falla
 *
 * [SessionRepository.logout] ejecuta **todos** los pasos antes de
 * relanzar el primer error. Por eso quien llama puede tratar el fallo
 * como un aviso (registrarlo) y navegar igualmente al login: la
 * sesión ya no existe aunque una limpieza secundaria haya fallado.
 *
 * @param sessionRepo Repositorio del ciclo de vida de la sesión.
 */
class Logout @Inject constructor(
    private val sessionRepo: SessionRepository,
) {

    /**
     * Ejecuta el cierre de sesión.
     *
     * Propaga la excepción si algún paso de limpieza falla, después de
     * haber intentado el resto.
     */
    suspend operator fun invoke() {
        sessionRepo.logout()
    }
}
