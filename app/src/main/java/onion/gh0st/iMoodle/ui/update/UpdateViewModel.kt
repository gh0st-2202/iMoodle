package onion.gh0st.iMoodle.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.usecase.update.CheckForUpdates
import javax.inject.Inject

/**
 * "Cerebro" que gestiona el estado de las actualizaciones.
 *
 * ## Qué hace
 * 1. Le pide al caso de uso que compruebe si hay una versión nueva.
 * 2. Si hay una actualización disponible, guarda la información en
 *    un flujo observable.
 * 3. Si algo falla (por ejemplo, no hay internet), lo apunta en el
 *    registro interno para no molestar al usuario.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdates: CheckForUpdates,
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    /**
     * Inicia la comprobación de actualizaciones.
     *
     * Su propósito es lanzar la tarea en segundo plano para no
     * bloquear la pantalla, y actualizar el estado si se encuentra
     * algo nuevo.
     */
    fun check() {
        viewModelScope.launch {
            checkForUpdates()
                .onSuccess { update ->
                    if (update != null) _updateAvailable.value = update
                }
                .onFailure { exception ->
                    Logging.logError("UpdateViewModel", "checkForUpdates", exception)
                }
        }
    }
}