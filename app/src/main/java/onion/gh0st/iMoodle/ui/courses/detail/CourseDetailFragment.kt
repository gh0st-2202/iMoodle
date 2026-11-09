package onion.gh0st.iMoodle.ui.courses.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import onion.gh0st.iMoodle.databinding.FragmentCourseDetailBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.common.WebUrls
import onion.gh0st.iMoodle.ui.files.CourseFilesFragment
import onion.gh0st.iMoodle.ui.files.FileDownloadHelper
import onion.gh0st.iMoodle.ui.forum.list.ForumFragment
import javax.inject.Inject

/**
 * Pantalla de detalle de un curso.
 *
 * ## Corrección (v2.2)
 * Todas las navegaciones a pantallas de detalle usan ahora
 * `R.id.detail_container` (el contenedor superpuesto al ViewPager),
 * en lugar de `R.id.fragment_container` (que ya no existe en el
 * layout principal).
 */
@AndroidEntryPoint
class CourseDetailFragment : Fragment(R.layout.fragment_course_detail) {

    @Inject lateinit var sessionRepo: SessionRepository

    private var courseName: String = ""

    private val viewModel: CourseDetailViewModel by viewModels()

    companion object {
        fun newInstance(courseId: Int, courseName: String): CourseDetailFragment =
            CourseDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt("COURSE_ID", courseId)
                    putString("COURSE_NAME", courseName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseName = arguments?.getString("COURSE_NAME") ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourseDetailBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeState(binding, adapter)
    }

    private fun setupToolbar(binding: FragmentCourseDetailBinding) {
        binding.toolbarDetail.title = courseName
        binding.toolbarDetail.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupWindowInsets(binding: FragmentCourseDetailBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarDetail) { view, insets ->
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

    private fun buildAdapter() = CourseDetailAdapter { moduleType -> routeModule(moduleType) }

    private fun setupRecyclerView(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        binding.rvCourseDetail.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseDetail.adapter = adapter
        binding.swipeRefreshDetail.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeState(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        var previousSections: List<String>? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    binding.swipeRefreshDetail.isRefreshing = state.isLoading
                    if (previousSections != state.sections) {
                        previousSections = state.sections
                        populateChips(binding.chipGroupSections, state.sections)
                    }
                }
            }
        }
    }

    private fun populateChips(chipGroup: ChipGroup, sections: List<String>) {
        chipGroup.removeAllViews()
        chipGroup.addView(
            chip(
                text = getString(R.string.filter_all),
                selected = true,
                onClick = { viewModel.setSection(null) },
            ),
        )
        sections.forEach { section ->
            chipGroup.addView(
                chip(
                    text = section,
                    selected = false,
                    onClick = { viewModel.setSection(section) },
                ),
            )
        }
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isCheckedIconVisible = true
            isChecked = selected
            setOnClickListener { onClick() }
        }

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
        val courseId = arguments?.getInt("COURSE_ID") ?: 0
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                AssignmentDetailFragment.newInstance(courseId, module.instance),
            )
            addToBackStack(null)
        }
    }

    private fun openResource(module: CourseModule) = when {
        module.files.size == 1 -> confirmAndDownload(module.files.first())
        module.files.size > 1 -> openFolder(module)
        else -> showToast(R.string.error_resource_no_files)
    }

    /**
     * Navega a la pantalla de archivos usando `detail_container`.
     */
    private fun openFolder(module: CourseModule) {
        val courseId = arguments?.getInt("COURSE_ID") ?: 0
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

    private fun openUrl(module: CourseModule) {
        viewLifecycleOwner.lifecycleScope.launch {
            val externalUrl = viewModel.getExternalUrl(module.instance)
            when {
                externalUrl.isNullOrBlank() -> showToast(R.string.error_open_url)
                !WebUrls.isSafeWebUrl(externalUrl) -> showToast(R.string.error_unsafe_url)
                else -> runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
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

    private fun confirmAndDownload(file: CourseFile) {
        if (sessionRepo.session.value == null) return
        FileDownloadHelper.showConfirmationDialog(requireContext(), file) {
            FileDownloadHelper.download(requireContext(), file)
        }
    }

    private fun showToast(@StringRes messageResId: Int, arg: String? = null) {
        val message = arg?.let { formatArg -> getString(messageResId, formatArg) }
            ?: getString(messageResId)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}