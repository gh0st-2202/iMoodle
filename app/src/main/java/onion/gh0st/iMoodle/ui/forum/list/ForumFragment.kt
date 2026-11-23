package onion.gh0st.iMoodle.ui.forum.list

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
import onion.gh0st.iMoodle.databinding.FragmentForumBinding
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast
import onion.gh0st.iMoodle.ui.forum.ForumPostDialog
import onion.gh0st.iMoodle.ui.forum.discussion.DiscussionFragment

/**
 * Pantalla de listado de discusiones de un foro.
 *
 * Al tocar una discusión se abre su detalle en `R.id.detail_container`,
 * el contenedor que `MainActivity` superpone al `ViewPager2`.
 * Se crea con [newInstance]; la lógica vive en [ForumViewModel].
 */
@AndroidEntryPoint
class ForumFragment : Fragment(R.layout.fragment_forum) {

    private var forumName: String = ""

    private val viewModel: ForumViewModel by viewModels()

    companion object {
        /**
         * Crea el fragmento para un foro concreto.
         *
         * @param forumId Identificador del foro.
         * @param forumName Nombre del foro, que se muestra como título.
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(forumId: Int, forumName: String): ForumFragment =
            ForumFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.FORUM_ID, forumId)
                    putString(FragmentArgs.FORUM_NAME, forumName)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forumName = arguments?.getString(FragmentArgs.FORUM_NAME) ?: ""
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentForumBinding.bind(view)
        setupToolbar(binding)
        binding.appBarForum.applySystemBarsInsets()
        setupFab(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Pone el nombre del foro como título y hace que la flecha de volver
     * cierre esta pantalla.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentForumBinding) {
        binding.toolbarForum.title = forumName
        binding.toolbarForum.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Crea el adaptador de la lista de discusiones. Al tocar una se abre su
     * detalle con [openDiscussion].
     *
     * @return El adaptador de la lista.
     */
    private fun buildAdapter() = ForumAdapter { discussion ->
        openDiscussion(discussion.id, discussion.name)
    }

    /**
     * Configura el botón flotante para abrir un tema nuevo.
     *
     * El botón solo se muestra si Moodle permite crear discusiones; esa
     * decisión la toma el ViewModel y llega en el estado.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupFab(binding: FragmentForumBinding) {
        binding.fabNewDiscussion.setOnClickListener {
            ForumPostDialog.show(
                context = requireContext(),
                titleRes = R.string.forum_new_discussion,
                showSubject = true,
                initialSubject = null,
            ) { subject, message -> viewModel.createDiscussion(subject, message) }
        }
    }

    /**
     * Configura la lista de discusiones y el gesto de arrastrar para refrescar.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun setupRecyclerView(
        binding: FragmentForumBinding,
        adapter: ForumAdapter,
    ) {
        binding.rvForum.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForum.adapter = adapter
        binding.swipeRefreshForum.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras la
     * pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun observeUi(binding: FragmentForumBinding, adapter: ForumAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Pinta cada nuevo estado: lista de discusiones e indicador de carga.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private suspend fun observeState(binding: FragmentForumBinding, adapter: ForumAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.discussions)
            binding.swipeRefreshForum.isRefreshing = state.isLoading
            binding.fabNewDiscussion.isVisible = state.canCreateDiscussion && !state.isPosting
        }
    }

    /**
     * Muestra los mensajes de error que emite el ViewModel.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is ForumUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                ForumUiEvent.DiscussionCreated -> showToast(R.string.toast_forum_posted)
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

}
