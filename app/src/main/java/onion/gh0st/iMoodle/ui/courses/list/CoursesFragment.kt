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
 * Pantalla de listado de cursos del usuario logueado.
 *
 * ## Qué muestra
 *
 * La lista de cursos en los que el usuario está matriculado, en
 * formato de tarjetas. Cada tarjeta muestra:
 *
 * - Una imagen de portada (o un patrón SVG generado si no hay).
 * - El nombre del curso.
 * - Un icono de favorito (placeholder, pendiente de implementar).
 * - Un botón "Entrar".
 *
 * ## Orientación
 *
 * La lista se muestra en formato vertical (portrait) u horizontal
 * (landscape), según la orientación del dispositivo. El
 * `LayoutManager` se ajusta automáticamente.
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `CoursesViewModel`, que a su vez observa Room
 * (offline-first). El Fragment solo:
 *
 * 1. Configura el `RecyclerView` y el `SwipeRefreshLayout`.
 * 2. Observa el estado.
 * 3. Reacciona a eventos (errores).
 * 4. Navega al detalle del curso al pulsar una tarjeta.
 *
 * ## Errores
 *
 * Los errores se consumen como eventos de un solo uso
 * (`CoursesUiEvent.ShowError`), no desde el estado. Así el toast no
 * se repite al volver al Fragment ni al re-suscribirse.
 */
@AndroidEntryPoint
class CoursesFragment : Fragment(R.layout.fragment_courses) {

    /** ViewModel de la pantalla. */
    private val viewModel: CoursesViewModel by viewModels()

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCoursesBinding.bind(view)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * ## Fail-fast
     *
     * Se lanza una excepción si el layout no tiene el `AppBarLayout`
     * esperado. Es un error de XML, no de runtime: si falta, es un
     * bug que debe detectarse en desarrollo.
     *
     * @param binding Binding de la vista.
     * @throws IllegalStateException si no hay `AppBarLayout` en el
     *   layout.
     */
    private fun setupWindowInsets(binding: FragmentCoursesBinding) {
        val appBarLayout = requireNotNull(binding.appBar) {
            "El layout de fragment_courses debe tener un AppBarLayout con id=app_bar"
        }
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }
    }

    /**
     * Construye el adapter de cursos con sus callbacks.
     *
     * ## Qué callbacks recibe
     *
     * - `onClick`: pulsar la tarjeta entera → abrir curso.
     * - `onEnterClick`: pulsar el botón "Entrar" → abrir curso.
     * - `onLongClick`: mantener pulsado → reservado para
     *   "favorito" (pendiente de implementar).
     *
     * @return Adapter listo para el `RecyclerView`.
     */
    private fun buildAdapter() = CourseAdapter(
        onClick = { course -> openCourse(course) },
        onEnterClick = { course -> openCourse(course) },
        onLongClick = { },
    )

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
     */
    private fun setupRecyclerView(binding: FragmentCoursesBinding, adapter: CourseAdapter) {
        binding.rvCourses.layoutManager = layoutManagerForCurrentOrientation()
        binding.rvCourses.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Devuelve el `LayoutManager` adecuado según la orientación.
     *
     * ## Comportamiento
     *
     * - **Landscape**: lista horizontal (las tarjetas se desplazan
     *   de izquierda a derecha).
     * - **Portrait**: lista vertical (las tarjetas se desplazan de
     *   arriba a abajo).
     *
     * @return `LinearLayoutManager` configurado.
     */
    private fun layoutManagerForCurrentOrientation(): LinearLayoutManager {
        val isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * ## Por qué dos corrutinas
     *
     * Porque `state` y `events` son `Flow`s independientes. Si se
     * recolectaran en la misma corrutina, habría que elegir cuál
     * primero, y el otro se quedaría sin procesar.
     *
     * ## Por qué `repeatOnLifecycle(STARTED)`
     *
     * Porque pausa la recolección cuando el Fragment no está visible
     * (por ejemplo, al ir a otra pantalla). Así no se procesan
     * eventos en background.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
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
     * Refleja el estado del ViewModel en la UI.
     *
     * ## Comportamiento
     *
     * - Actualiza la lista del adapter.
     * - Muestra la rueda de refresh solo si hay cursos y está
     *   cargando (evita que la rueda se quede girando sobre una lista
     *   vacía).
     * - Muestra el estado vacío si no está cargando y no hay cursos.
     * - Muestra la lista solo si hay cursos.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private suspend fun observeState(binding: FragmentCoursesBinding, adapter: CourseAdapter) {
        viewModel.uiState.collect { state ->
            adapter.submitList(state.courses)
            binding.swipeRefresh.isRefreshing =
                state.isLoading && state.courses.isNotEmpty()
            binding.emptyState.isVisible =
                !state.isLoading && state.courses.isEmpty()
            binding.rvCourses.isVisible = state.courses.isNotEmpty()
        }
    }

    /**
     * Consume los eventos de un solo uso del ViewModel.
     *
     * Actualmente solo hay `ShowError`: se resuelve con
     * `getString(messageResId, arg)` (o `getString(messageResId)` si
     * no hay argumento) y se muestra como toast.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is CoursesUiEvent.ShowError -> {
                    val message = event.arg?.let { formatArg ->
                        getString(event.messageResId, formatArg)
                    } ?: getString(event.messageResId)
                    showToast(message)
                }
            }
        }
    }

    /**
     * Navega al detalle del curso.
     *
     * @param course Curso pulsado.
     */
    private fun openCourse(course: CourseUi) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                CourseDetailFragment.newInstance(course.id, course.name),
            )
            addToBackStack(null)
        }
    }

    /**
     * Muestra un toast largo.
     *
     * @param message Texto ya resuelto a mostrar.
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}