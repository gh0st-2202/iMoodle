package onion.gh0st.iMoodle.ui.book

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
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
import onion.gh0st.iMoodle.databinding.FragmentBookBinding
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla con el contenido de un libro de Moodle.
 */
@AndroidEntryPoint
class BookFragment : Fragment(R.layout.fragment_book) {

    private var moduleName: String = ""

    private val viewModel: BookViewModel by viewModels()

    companion object {
        fun newInstance(courseId: Int, instanceId: Int, moduleName: String): BookFragment =
            BookFragment().apply {
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
        val binding = FragmentBookBinding.bind(view)
        setupToolbar(binding)
        binding.appBarBook.applySystemBarsInsets()
        binding.swipeRefreshBook.setOnRefreshListener { viewModel.refresh() }
        observeUi(binding)
    }

    private fun setupToolbar(binding: FragmentBookBinding) {
        binding.toolbarBook.title = moduleName.ifBlank { getString(R.string.title_book) }
        binding.toolbarBook.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun observeUi(binding: FragmentBookBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBook.isVisible = state.isLoading
                    binding.swipeRefreshBook.isRefreshing = state.isLoading
                    val content = state.content
                    binding.txtBookContent.isVisible = content != null
                    if (content != null) {
                        val html = buildString {
                            content.intro?.let { append(it) }
                            content.chapters.forEach { chapter ->
                                append("<h2>${Html.escapeHtml(chapter.title)}</h2>")
                                append(chapter.content)
                            }
                        }
                        binding.txtBookContent.text = HtmlUtils.renderHtml(html)
                        binding.txtBookContent.movementMethod = LinkMovementMethod.getInstance()
                    }
                    if (state.hasError) showToast(R.string.error_book_load)
                }
            }
        }
    }
}
