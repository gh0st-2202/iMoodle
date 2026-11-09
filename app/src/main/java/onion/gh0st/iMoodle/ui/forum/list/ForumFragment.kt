package onion.gh0st.iMoodle.ui.forum.list

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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentForumBinding
import onion.gh0st.iMoodle.ui.forum.discussion.DiscussionFragment

/**
 * Pantalla de listado de discusiones de un foro.
 *
 * ## Corrección (v2.2)
 * La navegación al detalle de la discusión ahora usa
 * `R.id.detail_container` (el contenedor superpuesto al ViewPager),
 * en lugar de `R.id.fragment_container`.
 */
@AndroidEntryPoint
class ForumFragment : Fragment(R.layout.fragment_forum) {

    private var forumName: String = ""

    private val viewModel: ForumViewModel by viewModels()

    companion object {
        fun newInstance(forumId: Int, forumName: String): ForumFragment =
            ForumFragment().apply {
                arguments = Bundle().apply {
                    putInt("FORUM_ID", forumId)
                    putString("FORUM_NAME", forumName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forumName = arguments?.getString("FORUM_NAME") ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentForumBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    private fun setupToolbar(binding: FragmentForumBinding) {
        binding.toolbarForum.title = forumName
        binding.toolbarForum.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupWindowInsets(binding: FragmentForumBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarForum) { view, insets ->
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

    private fun buildAdapter() = ForumAdapter { discussion ->
        openDiscussion(discussion.id, discussion.name)
    }

    private fun setupRecyclerView(
        binding: FragmentForumBinding,
        adapter: ForumAdapter,
    ) {
        binding.rvForum.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForum.adapter = adapter
        binding.swipeRefreshForum.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeUi(binding: FragmentForumBinding, adapter: ForumAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    private suspend fun observeState(binding: FragmentForumBinding, adapter: ForumAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.discussions)
            binding.swipeRefreshForum.isRefreshing = state.isLoading
        }
    }

    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is ForumUiEvent.ShowError -> showToast(event.messageResId, event.arg)
            }
        }
    }

    /**
     * Navega al detalle de la discusión usando el contenedor superpuesto.
     */
    private fun openDiscussion(discussionId: Int, discussionName: String) {
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                DiscussionFragment.newInstance(discussionId, discussionName),
            )
            addToBackStack(null)
        }
    }

    private fun showToast(@StringRes messageResId: Int, arg: String? = null) {
        val message = arg?.let { formatArg -> getString(messageResId, formatArg) }
            ?: getString(messageResId)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}