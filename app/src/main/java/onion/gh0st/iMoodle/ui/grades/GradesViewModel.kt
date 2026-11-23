package onion.gh0st.iMoodle.ui.grades

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
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
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.course.CourseSection
import onion.gh0st.iMoodle.domain.model.grade.CourseGrade
import onion.gh0st.iMoodle.domain.model.grade.GradeItem
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseContents
import onion.gh0st.iMoodle.domain.usecase.grade.GetCourseGrades
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado observable de la pantalla de notas.
 *
 * @property isLoading `true` mientras se piden las notas a Moodle.
 * @property items Lista de items a pintar (cabeceras de sección y
 *   notas).
 * @property hasError `true` si la última carga falló.
 */
data class GradesUiState(
    val isLoading: Boolean = true,
    val items: List<GradesListItem> = emptyList(),
    val hasError: Boolean = false,
)

/**
 * Eventos de un solo uso de la pantalla de notas.
 */
sealed interface GradesUiEvent {
    /** No se pudieron cargar las notas. */
    data class ShowError(@param:StringRes val messageResId: Int) : GradesUiEvent
}

/**
 * ViewModel de la pantalla de notas de un curso.
 *
 * ## Qué hace
 *
 * Pide las notas al caso de uso [GetCourseGrades] y, en paralelo, los
 * contenidos del curso ([GetCourseContents]) para poder agrupar las
 * actividades por sección (Unidad 1, Unidad 2…) igual que en el
 * detalle del curso. Descarta los totales de categoría y ordena las
 * actividades según su posición real en el curso.
 *
 * ## Argumentos
 *
 * Recibe el curso por `SavedStateHandle` ([FragmentArgs.COURSE_ID]).
 *
 * @param savedStateHandle Argumentos de la pantalla.
 * @param getCourseGrades Caso de uso de notas.
 * @param getCourseContents Caso de uso de contenidos del curso.
 */
@HiltViewModel
class GradesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCourseGrades: GetCourseGrades,
    private val getCourseContents: GetCourseContents,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>(FragmentArgs.COURSE_ID) ?: 0

    private val _state = MutableStateFlow(GradesUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    private val _events = Channel<GradesUiEvent>(Channel.BUFFERED)

    /** Eventos de un solo uso. */
    val events: Flow<GradesUiEvent> = _events.receiveAsFlow()

    init {
        loadGrades()
    }

    /** Vuelve a pedir las notas a Moodle. */
    fun refresh() = loadGrades()

    /**
     * Lanza la carga de notas y contenidos en `viewModelScope`.
     *
     * Si falla la carga de notas, conserva la lista anterior y avisa
     * con [GradesUiEvent.ShowError]. Si solo falla la carga de
     * contenidos, se muestran las notas sin agrupar por sección.
     */
    private fun loadGrades() = viewModelScope.launch {
        _state.update { currentState -> currentState.copy(isLoading = true) }
        val gradeResult = runCatchingCancellable { getCourseGrades(courseId) }
        val contentsResult = runCatchingCancellable { getCourseContents(courseId) }

        gradeResult
            .onSuccess { loadedGrade ->
                val sections = contentsResult.getOrNull().orEmpty()
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        items = buildItems(loadedGrade, sections),
                        hasError = false,
                    )
                }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getCourseGrades(courseId=$courseId)", exception)
                _state.update { currentState -> currentState.copy(isLoading = false, hasError = true) }
                _events.send(GradesUiEvent.ShowError(R.string.error_grades_load))
            }
    }

    /**
     * Agrupa las notas por sección, en el mismo orden en que aparecen
     * las secciones en el curso (Unidad 1, Unidad 2…), igual que en el
     * detalle del curso.
     *
     * Las filas que no correspondan a una actividad (por ejemplo, el
     * total del curso) se colocan al principio sin cabecera. Los totales
     * de categoría («Cálculo total de Unidad 1»…) se descartan porque
     * duplican la información de las cabeceras.
     *
     * @param grade Notas cargadas.
     * @param sections Secciones del curso en su orden natural.
     * @return Lista de items lista para el adapter.
     */
    private fun buildItems(grade: CourseGrade, sections: List<CourseSection>): List<GradesListItem> {
        val items = mutableListOf<GradesListItem>()
        val total = grade.total
        if (total != null) items.add(GradesListItem.Grade(total))

        val activities = grade.activities.filter { activity -> activity.itemType != "category" }
        val sectionNamesInOrder = sections.map { section -> section.name }.distinct()
        val moduleOrderByCmId = sections.flatMapIndexed { sectionIndex, section ->
            section.modules.mapIndexed { moduleIndex, module ->
                module.module.id to (sectionIndex * 1000 + moduleIndex)
            }
        }.toMap()
        val activitiesBySection = activities.groupBy { activity ->
            activity.cmid?.let { cmid ->
                sections.firstOrNull { section -> section.modules.any { module -> module.module.id == cmid } }?.name
            } ?: ""
        }
        val emittedSections = mutableSetOf<String>()
        sectionNamesInOrder.forEach { sectionName ->
            val sectionActivities = activitiesBySection[sectionName].orEmpty()
            if (sectionActivities.isEmpty()) return@forEach
            items.add(GradesListItem.SectionHeader(sectionName))
            sectionActivities
                .sortedBy { activity -> moduleOrderByCmId[activity.cmid] ?: Int.MAX_VALUE }
                .forEach { activity -> items.add(GradesListItem.Grade(activity)) }
            emittedSections.add(sectionName)
        }

        val leftover = activitiesBySection.entries
            .filter { (sectionName, _) -> sectionName !in emittedSections && sectionName !in sectionNamesInOrder }
            .flatMap { (_, acts) -> acts }
            .sortedBy { activity -> moduleOrderByCmId[activity.cmid] ?: Int.MAX_VALUE }
        leftover.forEach { activity -> items.add(GradesListItem.Grade(activity)) }

        return items
    }

    private companion object {
        const val TAG = "GradesViewModel"
    }
}
