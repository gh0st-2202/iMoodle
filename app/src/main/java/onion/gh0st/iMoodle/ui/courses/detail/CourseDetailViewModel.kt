package onion.gh0st.iMoodle.ui.courses.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.domain.model.course.CourseSection
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.domain.usecase.course.GetCourseContents
import onion.gh0st.iMoodle.domain.usecase.course.GetUrlExternalUrl
import javax.inject.Inject

/**
 * Estado observable de la pantalla de detalle de un curso.
 *
 * @property isLoading `true` mientras se están cargando los
 *   contenidos del curso desde la red (o desde la caché, si ya se
 *   habían cargado).
 * @property sections Nombres de las secciones disponibles, para el
 *   filtro de chips. Por ejemplo, `["Tema 1", "Tema 2", "Tema 3"]`.
 * @property selectedSection Sección activa en el filtro, o `null` si
 *   no hay filtro (se muestran todas).
 * @property items Lista de items ya mapeados para el adapter
 *   (cabeceras de sección + módulos + etiquetas).
 */
data class CourseDetailUiState(
    val isLoading: Boolean = true,
    val sections: List<String> = emptyList(),
    val selectedSection: String? = null,
    val items: List<CourseDetailItem> = emptyList(),
)

/**
 * ViewModel de la pantalla de detalle de un curso.
 *
 * ## Qué hace
 *
 * 1. Al construirse, carga los contenidos del curso con
 *    `GetCourseContents` (secciones + módulos).
 * 2. Transforma los módulos en items para el adapter
 *    (`CourseDetailItem.Module` y `CourseDetailItem.Label`).
 * 3. Extrae los nombres de las secciones para el filtro de chips.
 * 4. Permite filtrar los items por sección.
 * 5. Resuelve la URL externa de los módulos de tipo `url` bajo
 *    demanda.
 *
 * ## Dependencias
 *
 * Solo depende de dos casos de uso:
 *
 * - `GetCourseContents`: para cargar el curso completo.
 * - `GetUrlExternalUrl`: para resolver URLs externas.
 *
 * No depende de repositorios ni de infraestructura. Eso facilita
 * los tests y mantiene el ViewModel limpio.
 *
 * ## Caché
 *
 * El caso de uso `GetCourseContents` propaga el `forceRefresh` al
 * repositorio, que tiene caché en memoria por `courseId`. La carga
 * inicial del ViewModel usa `forceRefresh = false` (usa la caché si
 * está disponible). El pull-to-refresh usa `forceRefresh = true`
 * (ignora la caché).
 *
 * @param savedStateHandle Handle con `COURSE_ID`.
 * @param getCourseContents Caso de uso para cargar secciones y
 *   módulos.
 * @param getUrlExternalUrl Caso de uso para resolver URLs externas.
 */
@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCourseContents: GetCourseContents,
    private val getUrlExternalUrl: GetUrlExternalUrl,
) : ViewModel() {

    /**
     * ID del curso, extraído de los argumentos de navegación.
     *
     * ## Por qué se usa `SavedStateHandle`
     *
     * Porque los argumentos de navegación los pasa el Fragment al
     * construirse. Al rotar la pantalla, el Fragment se recrea y los
     * argumentos se pierden. `SavedStateHandle` los conserva y los
     * expone al ViewModel.
     */
    private val courseId: Int = savedStateHandle.get<Int>("COURSE_ID") ?: 0

    /** Estado interno de la pantalla. */
    private val _state = MutableStateFlow(CourseDetailUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    /**
     * Secciones completas cargadas la última vez.
     *
     * ## Por qué se guardan aquí
     *
     * Porque cuando el usuario cambia el filtro (`setSection`), hay
     * que reconstruir la lista de items a partir de las secciones
     * originales. Si no las guardáramos, cada cambio de filtro
     * tendría que volver a cargar el curso.
     */
    private var allSections: List<CourseSection> = emptyList()

    /**
     * Bloque de inicialización. Se ejecuta al construir el
     * ViewModel.
     *
     * Lanza la primera carga de contenidos con `forceRefresh =
     * false`, para aprovechar la caché si el usuario ya había
     * visitado el curso.
     */
    init {
        loadContents(forceRefresh = false)
    }

    /**
     * Vuelve a cargar los contenidos del curso forzando la red.
     *
     * ## Cuándo se llama
     *
     * Desde el pull-to-refresh del Fragment.
     *
     * ## Por qué `forceRefresh = true`
     *
     * Porque el usuario está pidiendo explícitamente datos frescos.
     * Quiere ver el curso actualizado, aunque haya una versión
     * cacheada.
     */
    fun refresh() = loadContents(forceRefresh = true)

    /**
     * Cambia la sección activa del filtro.
     *
     * ## Qué hace
     *
     * 1. Si la sección pedida es la misma que ya está seleccionada,
     *    no hace nada (evita trabajo redundante).
     * 2. Actualiza `selectedSection` en el estado.
     * 3. Reconstruye la lista de items filtrando por la nueva
     *    sección.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa un chip del filtro de secciones.
     *
     * @param section Nombre de la sección, o `null` para mostrar
     *   todas.
     */
    fun setSection(section: String?) {
        if (_state.value.selectedSection == section) return
        _state.update {
            it.copy(selectedSection = section, items = buildItems(allSections, section))
        }
    }

    /**
     * Resuelve la URL externa de un módulo de tipo `url`.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario pulsa un módulo de tipo `url`. La URL se pide
     * en ese momento (no antes), porque hacerlo para todos los
     * módulos del curso sería costoso.
     *
     * @param instanceId ID de instancia del módulo.
     * @return La URL externa, o `null` si no se encuentra o falla.
     */
    suspend fun getExternalUrl(instanceId: Int): String? =
        getUrlExternalUrl(courseId, instanceId)

    // --- Métodos internos ---

    /**
     * Lanza la carga de contenidos del curso.
     *
     * ## Qué hace paso a paso
     *
     * 1. Pone `isLoading = true`.
     * 2. Llama al caso de uso `GetCourseContents`.
     * 3. Si va bien, publica las secciones y los items.
     * 4. Si falla, apaga `isLoading` (sin mostrar error: el
     *    `CourseDetailFragment` no tiene un canal de eventos para
     *    errores; si en el futuro se añade, se haría aquí).
     *
     * ## Por qué usa `viewModelScope`
     *
     * Porque es el `CoroutineScope` ligado al ciclo de vida del
     * ViewModel. Cuando el ViewModel se destruye, las corrutinas se
     * cancelan automáticamente.
     *
     * @param forceRefresh Si `true`, ignora la caché del repositorio.
     */
    private fun loadContents(forceRefresh: Boolean) = viewModelScope.launch {
        setLoading()
        runCatching { getCourseContents(courseId, forceRefresh) }
            .onSuccess { courseSections -> onContentsLoaded(courseSections) }
            .onFailure { stopLoading() }
    }

    /**
     * Publica las secciones cargadas y reconstruye los items.
     *
     * ## Qué hace
     *
     * 1. Guarda las secciones completas en `allSections` (para
     *    futuros cambios de filtro).
     * 2. Actualiza el estado con:
     *    - Los nombres de sección (para los chips).
     *    - Los items ya filtrados por la sección activa.
     *    - `isLoading = false`.
     *
     * ## Por qué se respeta `selectedSection`
     *
     * Porque el usuario puede haber cambiado el filtro antes de que
     * termine la carga. En ese caso, queremos mantener su filtro, no
     * resetearlo.
     *
     * @param courseSections Secciones cargadas.
     */
    private fun onContentsLoaded(courseSections: List<CourseSection>) {
        allSections = courseSections
        _state.update {
            it.copy(
                isLoading = false,
                sections = extractSectionNames(courseSections),
                items = buildItems(courseSections, it.selectedSection),
            )
        }
    }

    /**
     * Extrae los nombres de sección no vacíos.
     *
     * ## Qué hace
     *
     * 1. Filtra las secciones que no tienen módulos (no se muestran
     *    en los chips porque no tienen contenido).
     * 2. Extrae el nombre de cada una.
     * 3. Quita los nombres vacíos.
     * 4. Quita duplicados.
     *
     * ## Por qué se quitan los vacíos
     *
     * Porque algunas secciones de Moodle no tienen nombre (por
     * ejemplo, las secciones "huérfanas"). Mostrar un chip vacío no
     * tendría sentido.
     *
     * @param courseSections Secciones completas.
     * @return Lista de nombres sin duplicados.
     */
    private fun extractSectionNames(courseSections: List<CourseSection>): List<String> =
        courseSections
            .filter { section -> section.modules.isNotEmpty() }
            .mapNotNull { section -> section.name.takeIf { name -> name.isNotBlank() } }
            .distinct()

    /**
     * Construye la lista de items del adapter, filtrando por
     * sección.
     *
     * ## Qué hace
     *
     * Recorre las secciones. Para cada una:
     *
     * 1. Decide si debe mostrarse con el filtro actual.
     * 2. Si no hay filtro (se muestran todas) y la sección tiene
     *    nombre, inserta una cabecera.
     * 3. Para cada módulo de la sección, inserta su item.
     *
     * ## Qué pasa si el filtro está activo
     *
     * Solo se muestra la sección filtrada, y **no** se inserta su
     * cabecera (no tiene sentido mostrar una cabecera cuando solo
     * hay una sección).
     *
     * @param courseSections Secciones completas.
     * @param filter Nombre de la sección a mostrar, o `null` para
     *   todas.
     * @return Lista de items para el adapter.
     */
    private fun buildItems(
        courseSections: List<CourseSection>,
        filter: String?,
    ): List<CourseDetailItem> {
        val items = mutableListOf<CourseDetailItem>()
        courseSections.forEach { section ->
            if (!shouldShow(section, filter)) return@forEach
            if (filter == null && section.name.isNotBlank()) {
                items.add(CourseDetailItem.SectionHeader(section.name))
            }
            section.modules.forEach { moduleType -> items.add(itemFor(moduleType)) }
        }
        return items
    }

    /**
     * Decide si una sección debe mostrarse con el filtro dado.
     *
     * @param section Sección a evaluar.
     * @param filter Filtro activo, o `null` para mostrar todas.
     * @return `true` si la sección debe aparecer.
     */
    private fun shouldShow(section: CourseSection, filter: String?): Boolean =
        (filter == null || section.name == filter) && section.modules.isNotEmpty()

    /**
     * Convierte un `ModuleType` en su item de adapter.
     *
     * ## Por qué `Label` va a un tipo distinto
     *
     * Porque las etiquetas no son navegables: son solo texto
     * informativo dentro del curso. Los demás módulos sí son
     * navegables (al pulsarlos, se abre una pantalla). Separarlos
     * permite al adapter renderizarlos de forma distinta.
     *
     * @param moduleType Módulo clasificado.
     * @return El item correspondiente.
     */
    private fun itemFor(moduleType: ModuleType): CourseDetailItem =
        when (moduleType) {
            is ModuleType.Label -> CourseDetailItem.Label(moduleType.content)
            else -> CourseDetailItem.Module(moduleType)
        }

    /**
     * Marca el estado como "cargando".
     */
    private fun setLoading() = _state.update { it.copy(isLoading = true) }

    /**
     * Apaga el flag de carga.
     */
    private fun stopLoading() = _state.update { it.copy(isLoading = false) }
}