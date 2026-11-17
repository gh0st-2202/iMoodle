package onion.gh0st.iMoodle.domain.usecase.web

import onion.gh0st.iMoodle.domain.model.web.WebSession
import onion.gh0st.iMoodle.domain.repository.web.WebSessionRepository
import javax.inject.Inject

/**
 * Caso de uso: obtener la sesión web para el `WebView`.
 *
 * ## Qué devuelve
 *
 * Un [WebSession] con el host de Moodle y las cookies de la sesión
 * activa, o `null` si no hay sesión.
 *
 * ## Por qué es síncrono
 *
 * Porque no hay nada que esperar: la sesión viva ya está en memoria.
 * El `WebView` necesita el host en callbacks síncronos
 * (`shouldOverrideUrlLoading`) y en `onCreate`, así que un `Flow` o un
 * `suspend` obligarían a cachear el valor en la Activity, que es
 * precisamente lo que se quiere evitar.
 *
 * ## Por qué se lee en cada llamada
 *
 * Porque el token puede refrescarse mientras la pantalla está abierta:
 * volver a preguntar garantiza que el host y las cookies sean los
 * actuales y no una copia del momento de abrir la pantalla.
 *
 * @param webSessionRepository Contrato de acceso a la sesión web.
 */
class GetWebSession @Inject constructor(
    private val webSessionRepository: WebSessionRepository,
) {

    /**
     * Lee la sesión web actual.
     *
     * @return La sesión web, o `null` si no hay sesión activa.
     */
    operator fun invoke(): WebSession? = webSessionRepository.currentSession()
}
