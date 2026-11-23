package onion.gh0st.iMoodle.ui.courses.list

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentCoursesBinding
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast
import onion.gh0st.iMoodle.ui.courses.detail.CourseDetailFragment

/**
 * Pantalla del listado de cursos.
 *
 * ## Posición de la lista al marcar favoritos
 * Cuando se marca un curso como favorito, cambia de posición en la
 * lista. Para evitar que la pantalla haga un salto automático
 * siguiendo al curso movido, se guarda la posición exacta del primer
 * elemento visible y se restaura después de actualizar la lista.
 *
 * La posición se mide en el eje en el que se desplaza la lista: el
 * vertical en pantalla normal y el horizontal en horizontal. Usar
 * siempre la coordenada vertical hacía que, en horizontal, la lista se
 * recolocase en otro sitio (y diera un salto) cada vez que llegaba un
 * estado nuevo.
 */
@AndroidEntryPoint
class CoursesFragment : Fragment(R.layout.fragment_courses) {

    private val viewModel: CoursesViewModel by viewModels()

    /**
     * Prepara la pantalla cuando se va a mostrar.
     *
     * Su propósito es enlazar las vistas, configurar los márgenes de
     * la pantalla, crear la lista y empezar a escuchar los cambios
     * de datos.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCoursesBinding.bind(view)
        requireNotNull(binding.appBar) { "El layout debe tener un AppBarLayout" }.applySystemBarsInsets()
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Crea el adaptador que dibuja los cursos y le dice qué hacer
     * cuando el usuario pulsa una tarjeta o una estrella.
     */
    private fun buildAdapter() = CourseAdapter(
        onClick = { course -> openCourse(course) },
        onEnterClick = { course -> openCourse(course) },
        onFavouriteClick = { course -> viewModel.toggleFavourite(course) },
    )

    /**
     * Conecta la lista visual (RecyclerView) con el adaptador y
     * activa la acción de recargar al deslizar hacia abajo.
     */
    private fun setupRecyclerView(binding: FragmentCoursesBinding, adapter: CourseAdapter) {
        binding.rvCourses.layoutManager = layoutManagerForCurrentOrientation()
        binding.rvCourses.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Decide si la lista debe mostrarse en vertical o en horizontal
     * dependiendo de cómo esté sujetando el usuario el teléfono.
     */
    private fun layoutManagerForCurrentOrientation(): LinearLayoutManager {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        else LinearLayoutManager(requireContext())
    }

    /**
     * Empieza a escuchar los cambios del "cerebro" (ViewModel)
     * para actualizar la pantalla y los mensajes de error.
     */
    private fun observeUi(binding: FragmentCoursesBinding, adapter: CourseAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Actualiza la lista visual cuando los datos cambian.
     *
     * Implementa el truco para que la pantalla no salte cuando un
     * curso cambia de posición al marcarlo como favorito.
     */
    private suspend fun observeState(binding: FragmentCoursesBinding, adapter: CourseAdapter) {
        viewModel.uiState.collect { state ->
            val layoutManager = binding.rvCourses.layoutManager as? LinearLayoutManager
            val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: -1
            val firstVisibleOffset = if (layoutManager != null && firstVisiblePosition >= 0) {
                firstVisibleStartOffset(layoutManager, firstVisiblePosition)
            } else {
                0
            }

            adapter.submitList(state.courses) {
                if (layoutManager != null && firstVisiblePosition >= 0) {
                    layoutManager.scrollToPositionWithOffset(firstVisiblePosition, firstVisibleOffset)
                }
            }

            binding.swipeRefresh.isRefreshing = state.isLoading && state.courses.isNotEmpty()
            binding.emptyState.isVisible = !state.isLoading && state.courses.isEmpty()
            binding.rvCourses.isVisible = state.courses.isNotEmpty()
        }
    }

    /**
     * Mide la distancia desde el borde de inicio del listado hasta el
     * primer elemento visible.
     *
     * ## Por qué no basta con la coordenada vertical
     *
     * En horizontal la lista se desplaza en el eje X. Si se restaura la
     * posición con un valor medido en el eje Y (que en horizontal es
     * prácticamente siempre el mismo), la lista se recoloca en otro
     * punto y da un salto visible. Por eso se elige el eje según la
     * orientación del gestor de la lista.
     *
     * @param layoutManager Gestor de la lista (vertical u horizontal).
     * @param position Posición del primer elemento visible.
     * @return Coordenada de inicio (arriba o izquierda) del elemento,
     *   relativa al listado. Cero si el elemento ya no está adjunto.
     */
    private fun firstVisibleStartOffset(layoutManager: LinearLayoutManager, position: Int): Int {
        val firstView = layoutManager.findViewByPosition(position) ?: return 0
        return if (layoutManager.orientation == LinearLayoutManager.HORIZONTAL) {
            layoutManager.getDecoratedLeft(firstView) + layoutManager.getLeftDecorationWidth(firstView)
        } else {
            layoutManager.getDecoratedTop(firstView) + layoutManager.getTopDecorationHeight(firstView)
        }
    }

    /**
     * Muestra un aviso flotante cuando hay un error.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is CoursesUiEvent.ShowError ->
                    showToast(event.messageResId, event.arg, longDuration = true)
            }
        }
    }

    /**
     * Navega a la pantalla de detalle de un curso.
     *
     * Su propósito es abrir una nueva capa visual por encima de
     * la lista con la información del curso seleccionado.
     */
    private fun openCourse(course: CourseUi) {
        parentFragmentManager.commit {
            replace(R.id.detail_container, CourseDetailFragment.newInstance(course.id, course.name))
            addToBackStack(null)
        }
    }
}