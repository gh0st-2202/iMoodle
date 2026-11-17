package onion.gh0st.iMoodle.domain.usecase.session

import onion.gh0st.iMoodle.domain.model.session.User
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import javax.inject.Inject

/**
 * Caso de uso: completar el `User` con los datos personales de Pasen.
 *
 * ## Qué añade
 *
 * Centro, fecha de nacimiento, edad y sexo. No toca la identidad de
 * Moodle (nombre, correo, avatar).
 *
 * ## Estrategia de caché
 *
 * La implementación real es *cache-first*: si el usuario ya trae los
 * cuatro campos no hace red; si hay caché en disco la usa; si no,
 * inicia sesión en Pasen y guarda el resultado. Con
 * `forceRefresh = true` se ignora todo lo anterior.
 *
 * ## Manejo de errores
 *
 * Propaga la excepción. El perfil es una pantalla secundaria: un
 * fallo de Pasen no debe impedir ver la identidad de Moodle, así que
 * el ViewModel decide si lo registra y sigue con los datos básicos.
 *
 * @param userProfileRepository Contrato de enriquecimiento del perfil.
 */
class EnrichUserProfile @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
) {

    /**
     * Enriquece el usuario indicado.
     *
     * @param user Usuario base (normalmente el de Moodle).
     * @param forceRefresh `true` para ignorar campos y caché y volver a
     *   consultar Pasen.
     * @return Copia del usuario con los campos personales rellenados.
     */
    suspend operator fun invoke(user: User, forceRefresh: Boolean = false): User =
        userProfileRepository.enrich(user, forceRefresh)
}
