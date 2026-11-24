package onion.gh0st.iMoodle.ui.page

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentPageBinding
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.web.WebLinks
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla con el contenido de una página de Moodle.
 *
 * Se abre con [newInstance] desde el detalle del curso.
 */
@AndroidEntryPoint
class PageFragment : Fragment(R.layout.fragment_page) {

    private var moduleName: String = ""

    private val viewModel: PageViewModel by viewModels()

    companion object {
        fun newInstance(courseId: Int, instanceId: Int, moduleName: String): PageFragment =
            PageFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.COURSE_ID, courseId)
                    putInt(FragmentArgs.MODULE_INSTANCE_ID, instanceId)
                    putString(FragmentArgs.MODULE_NAME, moduleName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleName = arguments?.getString(FragmentArgs.MODULE_NAME).orEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPageBinding.bind(view)
        setupToolbar(binding)
        binding.appBarPage.applySystemBarsInsets()
        binding.swipeRefreshPage.setOnRefreshListener { viewModel.refresh() }
        observeUi(binding)
    }

    private fun setupToolbar(binding: FragmentPageBinding) {
        binding.toolbarPage.title = moduleName.ifBlank { getString(R.string.title_page) }
        binding.toolbarPage.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun observeUi(binding: FragmentPageBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressPage.isVisible = state.isLoading
                    binding.swipeRefreshPage.isRefreshing = state.isLoading
                    val content = state.content
                    binding.txtPageContent.isVisible = content != null
                    if (content != null) {
                        binding.txtPageContent.text = HtmlUtils.renderHtml(content.content)
                        binding.txtPageContent.movementMethod = WebLinks.movementMethod
                    }
                    if (state.hasError) showToast(R.string.error_page_load)
                }
            }
        }
    }
}
