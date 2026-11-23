package onion.gh0st.iMoodle.ui.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.Logging
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.book.BookContent
import onion.gh0st.iMoodle.domain.usecase.course.GetBookContent
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import javax.inject.Inject

/**
 * Estado observable de la pantalla de un libro.
 *
 * @property isLoading `true` mientras se carga el contenido.
 * @property content Contenido cargado, o `null` si aún no hay.
 * @property hasError `true` si la carga falló.
 */
data class BookUiState(
    val isLoading: Boolean = true,
    val content: BookContent? = null,
    val hasError: Boolean = false,
)

/**
 * ViewModel de la pantalla de un libro de Moodle.
 *
 * @param savedStateHandle Argumentos de la pantalla.
 * @param getBookContent Caso de uso de obtención del contenido.
 */
@HiltViewModel
class BookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBookContent: GetBookContent,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>(FragmentArgs.COURSE_ID) ?: 0
    private val instanceId: Int = savedStateHandle.get<Int>(FragmentArgs.MODULE_INSTANCE_ID) ?: 0

    private val _state = MutableStateFlow(BookUiState())

    /** Estado observable de la pantalla. */
    val state = _state.asStateFlow()

    init {
        load()
    }

    /** Vuelve a cargar el contenido. */
    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, hasError = false) }
        runCatchingCancellable { getBookContent(courseId, instanceId, forceRefresh) }
            .onSuccess { loaded ->
                _state.update { it.copy(isLoading = false, content = loaded, hasError = loaded == null) }
            }
            .onFailure { exception ->
                Logging.logError(TAG, "getBookContent", exception)
                _state.update { it.copy(isLoading = false, hasError = true) }
            }
    }

    private companion object {
        const val TAG = "BookViewModel"
    }
}
