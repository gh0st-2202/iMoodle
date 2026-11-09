package onion.gh0st.iMoodle.ui.courses.list

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import onion.gh0st.iMoodle.ui.courses.detail.CourseDetailFragment

/**
 * Pantalla del listado de cursos.
 *
 * ## Corrección de la interfaz
 * Cuando se marca un curso como favorito, cambia de posición en la
 * lista. Para evitar que la pantalla haga un salto automático
 * siguiendo al curso movido, se guarda la posición exacta del primer
 * elemento visible y se restaura después de actualizar la lista.
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
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Ajusta los márgenes para que el contenido no quede tapado por
     * las barras del sistema (hora, batería, etc.).
     */
    private fun setupWindowInsets(binding: FragmentCoursesBinding) {
        val appBarLayout = requireNotNull(binding.appBar) { "El layout debe tener un AppBarLayout" }
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }
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
            val firstVisibleOffset = if (firstVisiblePosition >= 0) {
                val firstView = layoutManager?.findViewByPosition(firstVisiblePosition)
                if (firstView != null) {
                    layoutManager.getDecoratedTop(firstView) - layoutManager.getTopDecorationHeight(firstView)
                } else 0
            } else 0

            adapter.submitList(state.courses) {
                if (firstVisiblePosition >= 0) {
                    layoutManager?.scrollToPositionWithOffset(firstVisiblePosition, firstVisibleOffset)
                }
            }

            binding.swipeRefresh.isRefreshing = state.isLoading && state.courses.isNotEmpty()
            binding.emptyState.isVisible = !state.isLoading && state.courses.isEmpty()
            binding.rvCourses.isVisible = state.courses.isNotEmpty()
        }
    }

    /**
     * Muestra un mensaje flotante (Toast) cuando hay un error.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is CoursesUiEvent.ShowError -> {
                    val message = event.arg?.let { getString(event.messageResId, it) } ?: getString(event.messageResId)
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
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