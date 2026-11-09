package onion.gh0st.iMoodle.domain.usecase.update

import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import javax.inject.Inject

/**
 * Caso de uso para comprobar si hay actualizaciones.
 *
 * ## Qué es un caso de uso
 * Es una acción concreta que la aplicación puede realizar por petición
 * de la interfaz de usuario. En este caso, "comprobar si hay
 * actualizaciones". Sirve de puente entre la pantalla y el repositorio
 * de datos.
 */
class CheckForUpdates @Inject constructor(
    private val updateRepository: UpdateRepository,
) {

    /**
     * Ejecuta la comprobación.
     *
     * @return El resultado de la búsqueda de actualizaciones.
     */
    suspend operator fun invoke(): Result<AppUpdate?> =
        updateRepository.checkForUpdates()
}