package onion.gh0st.iMoodle.ui.web

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import onion.gh0st.iMoodle.domain.model.web.WebSession
import onion.gh0st.iMoodle.domain.usecase.web.GetWebSession
import javax.inject.Inject

/**
 * ViewModel de la pantalla del `WebView`.
 *
 * ## Qué aporta
 *
 * Es la única puerta de la vista web a la sesión: expone el host de
 * Moodle y las cookies a través de un modelo de dominio
 * ([WebSession]). Antes la Activity inyectaba
 * `MoodleSessionProvider`, lo que hacía que `ui/` dependiera de
 * `api/`; ahora la dependencia se corta aquí.
 *
 * ## Por qué no hay estado propio
 *
 * Porque lo que se muestra no cambia por decisión del ViewModel: la
 * sesión la mantiene el repositorio y puede refrescar el token en
 * cualquier momento. Guardar una copia en un `StateFlow` la dejaría
 * obsoleta. [currentSession] vuelve a leer el dominio en cada
 * llamada, que es barato (todo está en memoria) y siempre está
 * actualizado.
 *
 * ## Qué NO hace
 *
 * - **No manipula el `WebView`**: el JavaScript, las cookies del
 *   `CookieManager` y la navegación son plataforma y viven en la
 *   Activity.
 * - **No decide si se puede navegar**: solo ofrece el host permitido;
 *   la comparación de URLs es responsabilidad de la vista.
 *
 * @param getWebSession Caso de uso que lee la sesión web actual.
 */
@HiltViewModel
class MoodleWebViewModel @Inject constructor(
    private val getWebSession: GetWebSession,
) : ViewModel() {

    /**
     * Sesión web actual, o `null` si no hay sesión activa.
     *
     * Se lee en cada acceso para reflejar un posible refresco de token.
     * La Activity lo usa tanto en `onCreate` (para validar la URL
     * inicial e inyectar las cookies) como en
     * `shouldOverrideUrlLoading` (para validar cada navegación).
     */
    val currentSession: WebSession?
        get() = getWebSession()
}
