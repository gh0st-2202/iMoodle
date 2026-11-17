package onion.gh0st.iMoodle.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.domain.model.session.SessionEvent
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSessionEvents
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

    /**
     * La sesión ha dejado de ser válida (token no refrescable). La UI
     * debe volver al login limpiando la pila.
     *
     * ## Por qué es un evento y no solo estado
     *
     * Porque el cierre puede ocurrir con la Activity en segundo plano:
     * el `SharedFlow` de sesión no tiene `replay` y el evento se
     * perdería. El estado [MainViewModel.isSessionActive] cubre ese
     * caso al volver a primer plano; este evento avisa en el momento
     * exacto cuando la app está visible.
     */
    data object SessionExpired : MainUiEvent
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
 * ## Único dueño del permiso de alarmas exactas
 *
 * Es la **única** pieza de la app que decide abrir los ajustes de
 * alarmas exactas. Antes también lo hacía `CalendarViewModel`, con su
 * propio flag, y el usuario podía ver la misma pantalla dos veces (al
 * arrancar y al entrar en la Agenda), incluso después de haber
 * concedido el permiso. Centralizarlo aquí elimina la duplicación y
 * permite reprogramar al volver de los ajustes.
 *
 * ## Cuándo se instancia
 *
 * Solo cuando hay sesión activa. `MainActivity` comprueba la sesión
 * en `onCreate` y, si no hay, redirige al login. Si hay, es cuando
 * instancia este ViewModel.
 *
 * ## Idempotencia
 *
 * La sincronización inicial se ejecuta **una sola vez** por instancia
 * del ViewModel. El flag `started` garantiza que, si el usuario rota
 * la pantalla (y el ViewModel sobrevive), no se vuelve a lanzar. Si
 * el usuario vuelve a abrir la app más tarde (nueva instancia del
 * ViewModel), sí se lanza de nuevo. Las reprogramaciones explícitas
 * ([rescheduleReminders]) no miran ese flag.
 *
 * @param syncReminders Caso de uso que refresca el calendario y
 *   reprograma las alarmas.
 * @param observeSession Caso de uso para saber si hay sesión activa.
 * @param observeSessionEvents Caso de uso para enterarse de las
 *   expiraciones en el momento en que ocurren.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncReminders: SyncReminders,
    observeSession: ObserveSession,
    observeSessionEvents: ObserveSessionEvents,
) : ViewModel() {

    /**
     * `true` mientras haya una sesión activa.
     *
     * ## Por qué se combina estado y evento
     *
     * El `StateFlow` solo dice si hay sesión ahora. Al volver de
     * segundo plano, si la sesión expiró mientras la Activity no
     * escuchaba, el `SharedFlow` de eventos ya no puede entregar el
     * aviso; este estado sí, porque conserva el último valor.
     */
    val isSessionActive: StateFlow<Boolean> = observeSession.session
        .map { session -> session != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = observeSession.session.value != null,
        )

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<MainUiEvent>(Channel.BUFFERED)

    /**
     * Eventos de un solo uso, expuestos como `Flow`.
     *
     * ## Por qué se filtra por `Expired`
     *
     * El flujo de sesión también emite `TokenRefreshed`, que es un
     * refresco transparente y **no** debe sacar al usuario de la
     * pantalla. Solo una sesión caducada se convierte en
     * [MainUiEvent.SessionExpired].
     */
    val events: Flow<MainUiEvent> = merge(
        _events.receiveAsFlow(),
        observeSessionEvents.events
            .filterIsInstance<SessionEvent.Expired>()
            .map { MainUiEvent.SessionExpired },
    )

    /** `true` si la sincronización inicial ya se ha lanzado. */
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
     * Comprueba el flag `started` y delega en [rescheduleReminders],
     * que ejecuta `SyncReminders` y avisa si falta el permiso.
     */
    fun syncRemindersOnce() {
        if (started) return
        started = true
        rescheduleReminders()
    }

    /**
     * Ejecuta la sincronización y reprogramación aunque ya se haya
     * hecho la inicial.
     *
     * ## Cuándo se llama
     *
     * - Al volver de los ajustes de alarmas exactas, para programar
     *   las alarmas que se quedaron pendientes mientras faltaba el
     *   permiso.
     * - Cuando `MainActivity` detecta que el permiso ya está concedido
     *   y solo hace falta reprogramar.
     *
     * ## Por qué existe `promptIfDenied`
     *
     * Si el usuario vuelve de los ajustes **sin** conceder el
     * permiso, la sincronización devuelve `ExactAlarmDenied` otra vez.
     * Con `promptIfDenied = false` no se vuelve a abrir la pantalla de
     * ajustes, lo que evita un bucle: ajustes → volver → ajustes.
     *
     * @param promptIfDenied Si es `true`, emite
     *   [MainUiEvent.NeedExactAlarmPermission] cuando falta el
     *   permiso. Si es `false`, se limita a no programar nada.
     */
    fun rescheduleReminders(promptIfDenied: Boolean = true) {
        viewModelScope.launch {
            val result = syncReminders()
            if (promptIfDenied && result == ScheduleResult.ExactAlarmDenied) {
                _events.send(MainUiEvent.NeedExactAlarmPermission)
            }
        }
    }
}
