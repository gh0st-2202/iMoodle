package onion.gh0st.iMoodle.ui.grades

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
import onion.gh0st.iMoodle.databinding.FragmentGradesBinding
import onion.gh0st.iMoodle.domain.model.grade.GradeItem
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla de notas de un curso.
 *
 * ## Qué muestra
 *
 * El total del curso y una fila por actividad calificable, agrupadas
 * por sección (Unidad 1, Unidad 2…). Las tareas se pueden abrir desde
 * su fila. Se actualiza con pull-to-refresh.
 *
 * ## Navegación
 *
 * Se abre desde el menú del detalle del curso en `R.id.detail_container`.
 * Se crea con [newInstance]; la lógica vive en [GradesViewModel].
 */
@AndroidEntryPoint
class GradesFragment : Fragment(R.layout.fragment_grades) {

    private var courseName: String = ""
    private var courseId: Int = 0

    private val viewModel: GradesViewModel by viewModels()

    companion object {
        /**
         * Crea el fragmento para un curso concreto.
         *
         * @param courseId Identificador del curso.
         * @param courseName Nombre del curso, que se muestra como título.
         * @return El fragmento con sus argumentos configurados.
         */
        fun newInstance(courseId: Int, courseName: String): GradesFragment =
            GradesFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.COURSE_ID, courseId)
                    putString(FragmentArgs.COURSE_NAME, courseName)
                }
            }
    }

    /**
     * Recupera el nombre del curso de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseName = arguments?.getString(FragmentArgs.COURSE_NAME) ?: ""
        courseId = arguments?.getInt(FragmentArgs.COURSE_ID) ?: 0
    }

    /**
     * Prepara la pantalla y empieza a observar el estado.
     *
     * @param view Vista raíz ya inflada.
     * @param savedInstanceState Estado guardado.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGradesBinding.bind(view)
        setupToolbar(binding)
        binding.appBarGrades.applySystemBarsInsets()
        val adapter = GradesAdapter { gradeItem -> openAssignment(gradeItem) }
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Pone el nombre del curso como título y cierra la pantalla al volver.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentGradesBinding) {
        binding.toolbarGrades.title = courseName
        binding.toolbarGrades.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Abre el detalle de una tarea cuando el alumno pulsa su fila.
     *
     * @param gradeItem Fila de la nota pulsada.
     */
    private fun openAssignment(gradeItem: GradeItem) {
        if (gradeItem.itemModule != "assign") return
        val assignmentId = gradeItem.itemInstance ?: return
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                AssignmentDetailFragment.newInstance(courseId, assignmentId),
            )
            addToBackStack(null)
        }
    }

    /**
     * Configura la lista y el gesto de refresco.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de notas.
     */
    private fun setupRecyclerView(binding: FragmentGradesBinding, adapter: GradesAdapter) {
        binding.rvGrades.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGrades.adapter = adapter
        binding.swipeRefreshGrades.setOnRefreshListener { viewModel.refresh() }
        binding.btnGradesRetry.setOnClickListener { viewModel.refresh() }
    }

    /**
     * Observa el estado y los eventos mientras la pantalla está visible.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de notas.
     */
    private fun observeUi(binding: FragmentGradesBinding, adapter: GradesAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        adapter.submitList(state.items)
                        binding.swipeRefreshGrades.isRefreshing = state.isLoading

                        val isEmpty = !state.isLoading && state.items.isEmpty()
                        binding.emptyStateGrades.isVisible = isEmpty
                        if (isEmpty) {
                            binding.txtGradesEmpty.setText(
                                if (state.hasError) R.string.error_grades_load else R.string.empty_grades_desc,
                            )
                        }
                        binding.btnGradesRetry.isVisible = state.hasError && state.items.isEmpty()
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is GradesUiEvent.ShowError -> showToast(event.messageResId)
                        }
                    }
                }
            }
        }
    }
}
