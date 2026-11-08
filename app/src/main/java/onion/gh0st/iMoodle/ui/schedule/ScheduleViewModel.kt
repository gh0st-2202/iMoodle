package onion.gh0st.iMoodle.ui.schedule

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import java.util.Calendar
import javax.inject.Inject

/**
 * Estado observable de la pantalla de horario.
 *
 * @property isLoading `true` mientras se carga el horario.
 * @property items Lista de items (slots) para el adapter.
 * @property days Días disponibles para el filtro de chips.
 * @property selectedDay Día seleccionado actualmente.
 */
data class ScheduleUiState(
    val isLoading: Boolean = true,
    val items: List<ScheduleItem> = emptyList(),
    val days: List<String> = emptyList(),
    val selectedDay: String = "",
)

/**
 * Eventos de un solo uso consumidos por `ScheduleFragment`.
 */
sealed interface ScheduleUiEvent {

    /**
     * Muestra un toast de error.
     *
     * @property messageResId ID del recurso de string.
     */
    data class ShowError(@param:StringRes val messageResId: Int) : ScheduleUiEvent
}

/**
 * ViewModel de la pantalla de horario.
 *
 * ## Qué hace
 *
 * 1. Al construirse, carga el horario con `ScheduleRepository`.
 * 2. Asigna un color único a cada asignatura (mapeo secuencial).
 * 3. Extrae los días disponibles para el filtro de chips.
 * 4. Selecciona el día actual (o el primero con clases) por
 *    defecto.
 * 5. Permite filtrar los items por día.
 *
 * ## Caché
 *
 * La carga inicial usa `forceRefresh = false`, para aprovechar la
 * caché si el usuario ya había visitado el horario. El
 * pull-to-refresh usa `forceRefresh = true`.
 *
 * ## Sobre los colores
 *
 * Cada asignatura recibe un color único. La asignación es
 * secuencial (por orden alfabético de asignaturas), no por
 * `hashCode`, para evitar colisiones cuando hay pocas asignaturas.
 *
 * @param scheduleRepository Repositorio con caché por usuario.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(ScheduleUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /** Canal interno de eventos de un solo uso. */
    private val _events = Channel<ScheduleUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso, expuestos como `Flow`. */
    val events: Flow<ScheduleUiEvent> = _events.receiveAsFlow()

    /** Caché de todos los slots para filtrados rápidos. */
    private var allSlots: List<ScheduleItem.Slot> = emptyList()

    /**
     * Bloque de inicialización.
     *
     * Lanza la primera carga con `forceRefresh = false`.
     */
    init {
        loadSchedule(forceRefresh = false)
    }

    /**
     * Vuelve a cargar el horario forzando la red.
     *
     * ## Cuándo se llama
     *
     * Desde el pull-to-refresh del Fragment.
     */
    fun refresh() = loadSchedule(forceRefresh = true)

    /**
     * Filtra la lista por día.
     *
     * @param day Día a mostrar.
     */
    fun setDay(day: String) {
        if (_state.value.selectedDay == day) return
        _state.update { it.copy(selectedDay = day, items = filterByDay(allSlots, day)) }
    }

    /**
     * Lanza la carga del horario en `viewModelScope`.
     *
     * @param forceRefresh Si `true`, ignora la caché del repositorio.
     */
    private fun loadSchedule(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        runCatching { scheduleRepository.getSchedule(forceRefresh) }
            .onSuccess { slots ->
                val subjectColors = generateUniqueColorMapping(slots.map { it.subject })

                allSlots = slots.map { slot ->
                    val colorResId = subjectColors[slot.subject] ?: ScheduleAdapter.COLOR_PALETTE[0]
                    ScheduleItem.Slot(slot, colorResId)
                }

                val availableDays = extractDays(slots.map { it.day })

                val currentSelectedDay = _state.value.selectedDay
                val dayToSelect = if (currentSelectedDay.isNotEmpty() &&
                    availableDays.contains(currentSelectedDay)
                ) {
                    currentSelectedDay
                } else {
                    getDefaultDay(availableDays)
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        days = availableDays,
                        selectedDay = dayToSelect,
                        items = filterByDay(allSlots, dayToSelect),
                    )
                }
            }
            .onFailure { exception ->
                Logging.logError(
                    TAG,
                    "loadSchedule(forceRefresh=$forceRefresh)",
                    exception,
                )
                _state.update { it.copy(isLoading = false) }
                _events.send(ScheduleUiEvent.ShowError(R.string.error_schedule_load))
            }
    }

    /**
     * Genera un mapa que asigna un color único a cada asignatura.
     *
     * ## Cómo lo hace
     *
     * 1. Extrae las asignaturas únicas.
     * 2. Las ordena alfabéticamente (para que el orden sea estable).
     * 3. Les asigna los colores de la paleta de forma secuencial
     *    (índice % tamaño de la paleta).
     *
     * ## Por qué secuencial y no por `hashCode`
     *
     * Porque el `hashCode` puede colisionar, y dos asignaturas
     * distintas podrían tener el mismo color. Con el mapeo
     * secuencial, mientras haya menos asignaturas que colores en la
     * paleta, no hay colisiones.
     *
     * @param subjects Lista de todas las asignaturas (con posibles
     *   duplicados).
     * @return Mapa de `Nombre de asignatura → Recurso de color`.
     */
    private fun generateUniqueColorMapping(subjects: List<String>): Map<String, Int> {
        val uniqueSubjects = subjects.distinct().sorted()
        val colorPalette = ScheduleAdapter.COLOR_PALETTE

        return uniqueSubjects.mapIndexed { index, subject ->
            val colorIndex = index % colorPalette.size
            subject to colorPalette[colorIndex]
        }.toMap()
    }

    /**
     * Extrae los días únicos ordenados cronológicamente.
     *
     * @param days Lista de días con posibles duplicados.
     * @return Lista de días únicos en orden (Lunes, Martes, …).
     */
    private fun extractDays(days: List<String>): List<String> {
        val dayOrder = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves",
            "Viernes", "Sábado", "Domingo",
        )
        return days.distinct()
            .filter { day -> day in dayOrder }
            .sortedBy { day -> dayOrder.indexOf(day) }
    }

    /**
     * Determina qué día seleccionar por defecto.
     *
     * ## Comportamiento
     *
     * - Si hay clases hoy, selecciona hoy.
     * - Si no, selecciona el primer día que tenga clases.
     * - Si no hay días (lista vacía), devuelve cadena vacía.
     *
     * @param availableDays Días con clases.
     * @return El día por defecto a mostrar.
     */
    private fun getDefaultDay(availableDays: List<String>): String {
        if (availableDays.isEmpty()) return ""

        val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayName = when (todayIndex) {
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> ""
        }

        return if (availableDays.contains(todayName)) todayName else availableDays.first()
    }

    /**
     * Filtra los slots para mostrar solo los del día indicado.
     *
     * @param slots Lista de slots a filtrar.
     * @param day Día específico a mostrar.
     * @return Lista de items para el adapter, ordenados por
     *   `section`.
     */
    private fun filterByDay(slots: List<ScheduleItem.Slot>, day: String): List<ScheduleItem> {
        return slots
            .filter { slot -> slot.data.day == day }
            .sortedBy { slot -> slot.data.section }
    }

    private companion object {
        /** Etiqueta de logcat para `Logging.logError`. */
        const val TAG = "ScheduleViewModel"
    }
}