package onion.gh0st.iMoodle.ui.courses.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.databinding.FragmentCourseDetailBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.WebUrls
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast
import onion.gh0st.iMoodle.ui.files.CourseFilesFragment
import onion.gh0st.iMoodle.ui.files.FileDownloadDialog
import onion.gh0st.iMoodle.ui.forum.list.ForumFragment

/**
 * Pantalla de detalle de un curso: secciones y módulos con un filtro
 * por sección.
 *
 * Desde aquí se navega a las pantallas de detalle (tarea, carpeta,
 * foro, módulo genérico) en `R.id.detail_container`, el contenedor que
 * `MainActivity` superpone al `ViewPager2`. Los enlaces se abren en el
 * navegador del sistema y los recursos se descargan tras confirmar.
 * Se crea con [newInstance]; la lógica vive en [CourseDetailViewModel].
 */
@AndroidEntryPoint
class CourseDetailFragment : Fragment(R.layout.fragment_course_detail) {

    private var courseName: String = ""

    private val viewModel: CourseDetailViewModel by viewModels()

    companion object {
        /**
         * Crea el fragmento para un curso concreto.
         *
         * @param courseId Identificador del curso.
         * @param courseName Nombre del curso, que se muestra como título.
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(courseId: Int, courseName: String): CourseDetailFragment =
            CourseDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.COURSE_ID, courseId)
                    putString(FragmentArgs.COURSE_NAME, courseName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseName = arguments?.getString(FragmentArgs.COURSE_NAME) ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourseDetailBinding.bind(view)
        setupToolbar(binding)
        binding.appBarDetail.applySystemBarsInsets()
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeState(binding, adapter)
    }

    /**
     * Pone el nombre del curso como título de la barra superior y hace que
     * la flecha de volver cierre esta pantalla.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentCourseDetailBinding) {
        binding.toolbarDetail.title = courseName
        binding.toolbarDetail.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Crea el adaptador de la lista de módulos. Al tocar un módulo se decide
     * qué pantalla abrir con [routeModule].
     *
     * @return El adaptador de la lista.
     */
    private fun buildAdapter() = CourseDetailAdapter { moduleType -> routeModule(moduleType) }

    /**
     * Configura la lista de módulos y el gesto de arrastrar para refrescar.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador que pinta las secciones y los módulos.
     */
    private fun setupRecyclerView(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        binding.rvCourseDetail.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseDetail.adapter = adapter
        binding.swipeRefreshDetail.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Pinta cada nuevo estado (lista, indicador de carga y chips de filtro) y
     * muestra un aviso si la carga del curso falla.
     *
     * Los chips solo se reconstruyen cuando cambia la lista de secciones, y se
     * marcan según la sección que el ViewModel tiene seleccionada, de modo que
     * la pantalla sigue siendo coherente tras recrear la vista.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador al que se entrega la lista.
     */
    private fun observeState(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        var previousSections: List<String>? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        adapter.submitList(state.items)
                        binding.swipeRefreshDetail.isRefreshing = state.isLoading
                        if (previousSections != state.sections) {
                            previousSections = state.sections
                            populateChips(binding.chipGroupSections, state.sections, state.selectedSection)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            CourseDetailUiEvent.LoadFailed -> showToast(R.string.error_unexpected)
                            is CourseDetailUiEvent.DownloadStarted ->
                                showToast(R.string.toast_downloading, event.fileName)
                            CourseDetailUiEvent.DownloadFailed ->
                                showToast(R.string.toast_download_error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Reconstruye los chips de filtro: "Todos" más uno por sección.
     *
     * @param chipGroup Contenedor de los chips.
     * @param sections Nombres de las secciones del curso.
     * @param selectedSection Sección seleccionada actualmente, o `null` si se
     *   muestran todas.
     */
    private fun populateChips(chipGroup: ChipGroup, sections: List<String>, selectedSection: String?) {
        chipGroup.removeAllViews()
        chipGroup.addView(
            chip(
                text = getString(R.string.filter_all),
                selected = selectedSection == null,
                onClick = { viewModel.setSection(null) },
            ),
        )
        sections.forEach { section ->
            chipGroup.addView(
                chip(
                    text = section,
                    selected = section == selectedSection,
                    onClick = { viewModel.setSection(section) },
                ),
            )
        }
    }

    /**
     * Crea un chip seleccionable con el texto indicado.
     *
     * @param text Texto del chip.
     * @param selected `true` si debe aparecer marcado.
     * @param onClick Acción al pulsarlo.
     * @return El chip listo para añadir al grupo.
     */
    private fun chip(text: String, selected: Boolean, onClick: () -> Unit): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isCheckedIconVisible = true
            isChecked = selected
            setOnClickListener { onClick() }
        }

    /**
     * Decide qué pantalla abrir según el tipo de módulo tocado: tarea,
     * recurso, carpeta, enlace, foro o genérico. Las etiquetas no hacen nada.
     *
     * @param moduleType Módulo tocado, ya clasificado por tipo.
     */
    private fun routeModule(moduleType: ModuleType) = when (moduleType) {
        is ModuleType.Assignment -> openAssignment(moduleType.module)
        is ModuleType.Resource -> openResource(moduleType.module)
        is ModuleType.Folder -> openFolder(moduleType.module)
        is ModuleType.Url -> openUrl(moduleType.module)
        is ModuleType.Forum -> openForum(moduleType.module)
        is ModuleType.Label -> Unit
        is ModuleType.Other -> openGeneric(moduleType.module)
    }

    /**
     * Navega al detalle de la tarea usando `detail_container`.
     */
    private fun openAssignment(module: CourseModule) {
        val courseId = arguments?.getInt(FragmentArgs.COURSE_ID) ?: 0
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                AssignmentDetailFragment.newInstance(courseId, module.instance),
            )
            addToBackStack(null)
        }
    }

    /**
     * Abre un recurso. Con un solo archivo lo descarga (tras confirmar); con
     * varios lo abre como carpeta; sin archivos avisa al usuario.
     *
     * @param module Módulo de tipo recurso.
     */
    private fun openResource(module: CourseModule) = when {
        module.files.size == 1 -> confirmAndDownload(module.files.first())
        module.files.size > 1 -> openFolder(module)
        else -> showToast(R.string.error_resource_no_files)
    }

    /**
     * Navega a la pantalla de archivos usando `detail_container`.
     */
    private fun openFolder(module: CourseModule) {
        val courseId = arguments?.getInt(FragmentArgs.COURSE_ID) ?: 0
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                CourseFilesFragment.newInstance(
                    courseId = courseId,
                    moduleId = module.id,
                    moduleName = module.name,
                    moduleDescription = module.description,
                ),
            )
            addToBackStack(null)
        }
    }

    /**
     * Abre un módulo de tipo enlace en el navegador del sistema.
     *
     * La URL real se pide al ViewModel; solo se abre si es una URL web segura
     * ([WebUrls.isSafeWebUrl]). Si no hay URL, no es segura o no hay navegador,
     * se avisa con un mensaje.
     *
     * @param module Módulo de tipo enlace.
     */
    private fun openUrl(module: CourseModule) {
        viewLifecycleOwner.lifecycleScope.launch {
            val externalUrl = viewModel.getExternalUrl(module.instance)
            when {
                externalUrl.isNullOrBlank() -> showToast(R.string.error_open_url)
                !WebUrls.isSafeWebUrl(externalUrl) -> showToast(R.string.error_unsafe_url)
                else -> runCatchingCancellable {
                    startActivity(Intent(Intent.ACTION_VIEW, externalUrl.toUri()))
                }.onFailure {
                    showToast(R.string.error_no_navigator)
                }
            }
        }
    }

    /**
     * Navega al foro usando `detail_container`.
     */
    private fun openForum(module: CourseModule) {
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                ForumFragment.newInstance(module.instance, module.name),
            )
            addToBackStack(null)
        }
    }

    /**
     * Navega a la pantalla genérica usando `detail_container`.
     */
    private fun openGeneric(module: CourseModule) {
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                GenericModuleFragment.newInstance(module),
            )
            addToBackStack(null)
        }
    }

    /**
     * Pide confirmación y delega la descarga en el ViewModel.
     *
     * El ViewModel comprueba la sesión y encola la descarga; la
     * pantalla solo muestra el diálogo y los avisos.
     *
     * @param file Archivo a descargar.
     */
    private fun confirmAndDownload(file: CourseFile) {
        FileDownloadDialog.show(requireContext(), file.fileName) {
            viewModel.downloadFile(file)
        }
    }

}