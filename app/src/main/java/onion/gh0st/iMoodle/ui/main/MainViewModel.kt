package onion.gh0st.iMoodle.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import onion.gh0st.iMoodle.domain.usecase.session.SyncReminders
import javax.inject.Inject

/**
 * Eventos de un solo uso que `MainActivity` debe consumir.
 */
sealed interface MainUiEvent {

    /**
     * El sistema exige el permiso `SCHEDULE_EXACT_ALARM` y no está
     * concedido. La UI debe abrir los ajustes del sistema para
     * solicitarlo.
     */
    data object NeedExactAlarmPermission : MainUiEvent
}

/**
 * ViewModel de `MainActivity`.
 *
 * ## Qué hace
 *
 * Se encarga de lanzar la sincronización de recordatorios al abrir
 * la app (`SyncReminders`). Es una operación de fondo que:
 *
 * 1. Refresca los eventos del calendario desde Moodle.
 * 2. Reprograma las alarmas de las tareas próximas a entregar.
 *
 * ## Cuándo se instancia
 *
 * Solo cuando hay sesión activa. `MainActivity` comprueba la sesión
 * en `onCreate` y, si no hay, redirige al login. Si hay, es cuando
 * instancia este ViewModel.
 *
 * ## Idempotencia
 *
 * La sincronización de recordatorios se ejecuta **una sola vez**
 * por instancia del ViewModel. El flag `started` garantiza que, si
 * el usuario rota la pantalla (y el ViewModel sobrevive), no se
 * vuelve a lanzar. Si el usuario vuelve a abrir la app más tarde
 * (nueva instancia del ViewModel), sí se lanza de nuevo.
 *
 * @param syncReminders Caso de uso que refresca el calendario y
 *   reprograma las alarmas.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncReminders: SyncReminders,
) : ViewModel() {

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<MainUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<MainUiEvent> = _events.receiveAsFlow()

    /** `true` si la sincronización ya se ha lanzado. */
    private var started = false

    /**
     * Lanza la sincronización de recordatorios **una sola vez** por
     * instancia del ViewModel.
     *
     * ## Cuándo se llama
     *
     * Desde `MainActivity.onCreate`.
     *
     * ## Qué hace
     *
     * 1. Comprueba el flag `started` para evitar ejecuciones
     *    múltiples.
     * 2. Lanza `SyncReminders` en `viewModelScope`.
     * 3. Si el resultado es `ExactAlarmDenied`, emite
     *    `NeedExactAlarmPermission` para que la UI abra los ajustes.
     */
    fun syncRemindersOnce() {
        if (started) return
        started = true
        viewModelScope.launch {
            if (syncReminders() == ScheduleResult.ExactAlarmDenied) {
                _events.send(MainUiEvent.NeedExactAlarmPermission)
            }
        }
    }
}