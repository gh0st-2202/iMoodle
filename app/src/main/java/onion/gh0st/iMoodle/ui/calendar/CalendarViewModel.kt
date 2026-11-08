package onion.gh0st.iMoodle.ui.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.notification.ScheduleResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Estado observable de la pantalla de la agenda.
 *
 * @property isLoading `true` mientras se están cargando los eventos
 *   desde la red.
 * @property items Lista de items ya agrupados por día (cabeceras +
 *   eventos), lista para el adapter.
 */
data class CalendarUiState(
    val isLoading: Boolean = true,
    val items: List<CalendarItem> = emptyList(),
)

/**
 * Eventos de un solo uso consumidos por `CalendarFragment`.
 *
 * @property NeedExactAlarmPermission El usuario debe conceder el
 *   permiso de alarmas exactas en los ajustes del sistema.
 * @property ShowError Muestra un toast de error genérico.
 */
sealed interface CalendarUiEvent {

    /** El usuario debe conceder el permiso de alarmas exactas. */
    data object NeedExactAlarmPermission : CalendarUiEvent

    /** Muestra un toast de error genérico. */
    data class ShowError(@param:StringRes val messageResId: Int) : CalendarUiEvent
}

/**
 * ViewModel de la pantalla de la agenda.
 *
 * ## Qué hace
 *
 * 1. Al construirse, se suscribe a la base de datos local
 *    (`CalendarRepository.getEventsFlow`) para recibir los eventos
 *    que haya cacheados.
 * 2. Lanza un refresco contra Moodle
 *    (`CalendarRepository.refreshEvents`) para actualizar la base de
 *    datos.
 * 3. Cuando llegan eventos nuevos (desde Room), los agrupa por día
 *    e inserta cabeceras ("Hoy", "Mañana", "Lunes, 15 de enero"...).
 * 4. Programa las alarmas para las tareas próximas a entregar
 *    (`NotificationScheduler.schedule`).
 *
 * ## Qué NO hace
 *
 * - **No gestiona la base de datos**: solo observa y pide refrescos.
 * - **No construye los datos de la agenda desde la red**: la red
 *   solo actualiza Room; la UI siempre lee de Room.
 * - **No navega a otras pantallas**: eso lo hace el Fragment cuando
 *   el usuario pulsa un evento.
 *
 * ## Estrategia offline-first
 *
 * La agenda sigue el patrón offline-first:
 *
 * - La UI **nunca** depende de la red para pintarse.
 * - La red solo **actualiza** los datos locales.
 * - Si la red falla, la UI sigue mostrando los eventos anteriores.
 *
 * Esto se traduce en:
 *
 * - Si hay red: la UI se actualiza con datos frescos.
 * - Si no hay red: la UI se actualiza con los datos cacheados (o se
 *   queda vacía si no había).
 *
 * ## Sobre el rango de fechas
 *
 * La agenda cubre siempre los **próximos 30 días** desde que se
 * creó el ViewModel. El rango se fija en el constructor: no se puede
 * navegar fuera de él. Si en el futuro se quiere navegar (por
 * ejemplo, hacia atrás en el tiempo), habrá que añadir métodos para
 * cambiar el rango.
 *
 * @param calendarRepository Fuente de eventos del calendario.
 * @param notificationScheduler Programador de alarmas.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    /**
     * Inicio del rango, en segundos. Es "ahora" en el momento de
     * crear el ViewModel.
     */
    private val startTime: Long = System.currentTimeMillis() / 1000

    /** Fin del rango, en segundos (inicio + 30 días). */
    private val endTime: Long = startTime + THIRTY_DAYS_SECONDS

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(CalendarUiState(isLoading = true))

    /** Estado observable de la pantalla. */
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _uiEvents = Channel<CalendarUiEvent>(Channel.BUFFERED)

    /**
     * Eventos de un solo uso, expuestos como `Flow`.
     *
     * La UI los consume con `collect`.
     */
    val uiEvents: Flow<CalendarUiEvent> = _uiEvents.receiveAsFlow()

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * 1. Observa los eventos (de Room) y los pinta.
     * 2. Lanza un refresco inicial contra Moodle.
     */
    init {
        observeEvents()
        refresh()
    }

    /**
     * Refresca los eventos de la agenda.
     *
     * ## Qué hace
     *
     * Lanza una corrutina en `viewModelScope` que:
     *
     * 1. Pone `isLoading = true`.
     * 2. Pide al repositorio que refresque los eventos del rango
     *    desde Moodle.
     * 3. Si va bien, el `Flow` de eventos se actualizará solo.
     * 4. Si falla, emite un `ShowError`.
     * 5. En cualquier caso, apaga `isLoading`.
     *
     * ## Cuándo se llama
     *
     * - Desde el `init` (primera carga).
     * - Desde el Fragment, en pull-to-refresh.
     *
     * @return Job de la corrutina (útil para tests).
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                calendarRepository.refreshEvents(startTime, endTime)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Logging.logError(TAG, "refreshEvents", exception)
                _uiEvents.send(CalendarUiEvent.ShowError(R.string.error_unexpected))
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Observa la base de datos local y pinta los eventos agrupados
     * por día.
     *
     * ## Qué hace
     *
     * Se suscribe al `Flow` de eventos del rango. Cada vez que Room
     * emite una nueva lista:
     *
     * 1. Agrupa los eventos por día (con `groupByDate`).
     * 2. Publica la lista de items en el estado.
     * 3. Programa las alarmas para las tareas próximas a entregar.
     *
     * ## Por qué dentro de `viewModelScope`
     *
     * Porque el `Flow` se debe recolectar mientras viva el ViewModel.
     * Al cancelarse el `viewModelScope`, se cancela la recolección.
     */
    private fun observeEvents() {
        viewModelScope.launch {
            calendarRepository.getEventsFlow(startTime, endTime).collect { events ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = groupByDate(events),
                    )
                }
                programAlarms(events)
            }
        }
    }

    /**
     * Evita spamear el diálogo de permiso de alarmas exactas.
     *
     * ## Qué es este flag
     *
     * Es un booleano que se pone a `true` la primera vez que se
     * pide el permiso, para no volver a pedirlo en cada emisión de
     * Room. Si el usuario lo concede, se resetea a `false` (ver
     * `programAlarms`).
     *
     * ## Por qué es necesario
     *
     * Porque `programAlarms` se ejecuta en cada emisión de Room. Si
     * no hubiera un flag, el diálogo de permiso se abriría una y
     * otra vez, lo cual sería una experiencia de usuario horrible.
     */
    private var exactAlarmPromptSent = false

    /**
     * Programa las alarmas para las tareas próximas a entregar.
     *
     * ## Comportamiento
     *
     * - Si el permiso está concedido, programa las alarmas y
     *   resetea el flag.
     * - Si no está concedido y el flag está a `false`, emite
     *   `NeedExactAlarmPermission` (la UI abrirá los ajustes).
     * - Si no está concedido y el flag está a `true`, no hace nada
     *   (ya se pidió antes).
     *
     * @param events Lista de eventos del rango.
     */
    private suspend fun programAlarms(events: List<CalendarEvent>) {
        when (notificationScheduler.schedule(events)) {
            ScheduleResult.Success -> exactAlarmPromptSent = false
            ScheduleResult.ExactAlarmDenied ->
                if (!exactAlarmPromptSent) {
                    exactAlarmPromptSent = true
                    _uiEvents.send(CalendarUiEvent.NeedExactAlarmPermission)
                }
        }
    }

    /**
     * Agrupa los eventos por día, insertando cabeceras.
     *
     * ## Qué hace
     *
     * 1. Ordena los eventos por `timestart`.
     * 2. Recorre la lista. Cada vez que cambia el día, inserta una
     *    cabecera.
     * 3. Devuelve una lista plana de items (cabeceras + eventos).
     *
     * ## Cómo detecta el cambio de día
     *
     * Compara la clave de fecha (`yyyyMMdd`) del evento actual con
     * la del anterior. Si cambia, es un día nuevo.
     *
     * ## Por qué se usa una clave de fecha y no `Calendar`
     *
     * Porque comparar `Calendar` es engorroso (hay que comparar
     * año, mes y día por separado). Una clave `yyyyMMdd` como
     * `String` es más simple y más robusta.
     *
     * @param events Lista de eventos.
     * @return Lista de items con cabeceras y eventos.
     */
    private fun groupByDate(events: List<CalendarEvent>): List<CalendarItem> {
        val items = mutableListOf<CalendarItem>()
        var currentDateKey = ""
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val keyFormatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        events.sortedBy { event -> event.timestart }.forEach { event ->
            val eventDate = Date(event.timestart * 1000)
            val eventDateKey = keyFormatter.format(eventDate)
            if (eventDateKey != currentDateKey) {
                currentDateKey = eventDateKey
                items.add(CalendarItem.Header(headerFor(eventDate, today, tomorrow)))
            }
            items.add(CalendarItem.Event(event))
        }
        return items
    }

    /**
     * Decide qué cabecera usar para un día concreto.
     *
     * @param date Fecha del día.
     * @param today Instante de hoy.
     * @param tomorrow Instante de mañana.
     * @return La cabecera correspondiente.
     */
    private fun headerFor(date: Date, today: Calendar, tomorrow: Calendar): CalendarHeader =
        when {
            date.isSameDayAs(today) -> CalendarHeader.Today
            date.isSameDayAs(tomorrow) -> CalendarHeader.Tomorrow
            else -> CalendarHeader.FormattedDate(date)
        }

    /**
     * Comprueba si una fecha cae en el mismo día que un `Calendar`.
     *
     * @receiver Fecha a comprobar.
     * @param other `Calendar` de referencia.
     * @return `true` si es el mismo día.
     */
    private fun Date.isSameDayAs(other: Calendar): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = this@isSameDayAs.time }
        return calendar.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }

    private companion object {
        /** Treinta días en segundos. */
        const val THIRTY_DAYS_SECONDS = 30L * 24 * 60 * 60

        /** Etiqueta de logcat. */
        const val TAG = "CalendarViewModel"
    }
}