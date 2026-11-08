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
 * ## Qué muestra
 *
 * Una lista de discusiones del foro. Cada discusión muestra su
 * nombre, autor, fecha y número de respuestas. Al pulsar una, se
 * abre el detalle de la discusión (lista de posts).
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `ForumViewModel`.
 *
 * ## Manejo de errores
 *
 * Los errores se consumen como eventos de un solo uso
 * (`ForumUiEvent.ShowError`), no desde el estado. Así el toast no
 * se repite al volver al Fragment.
 */
@AndroidEntryPoint
class ForumFragment : Fragment(R.layout.fragment_forum) {

    /** Nombre del foro, recibido como argumento. */
    private var forumName: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: ForumViewModel by viewModels()

    companion object {

        /**
         * Construye una instancia del Fragment.
         *
         * @param forumId ID del foro.
         * @param forumName Nombre visible del foro.
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(forumId: Int, forumName: String): ForumFragment =
            ForumFragment().apply {
                arguments = Bundle().apply {
                    putInt("FORUM_ID", forumId)
                    putString("FORUM_NAME", forumName)
                }
            }
    }

    /**
     * Recupera el nombre del foro de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forumName = arguments?.getString("FORUM_NAME") ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentForumBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Configura el toolbar con el título del foro y botón atrás.
     */
    private fun setupToolbar(binding: FragmentForumBinding) {
        binding.toolbarForum.title = forumName
        binding.toolbarForum.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     */
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

    /**
     * Construye el adapter de discusiones con el callback de
     * navegación.
     */
    private fun buildAdapter() = ForumAdapter { discussion ->
        openDiscussion(discussion.id, discussion.name)
    }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
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
     * Observa el estado y los eventos del ViewModel en paralelo.
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
     * Refleja el estado del ViewModel en la UI.
     */
    private suspend fun observeState(binding: FragmentForumBinding, adapter: ForumAdapter) {
        viewModel.state.collect { state ->
            adapter.submitList(state.discussions)
            binding.swipeRefreshForum.isRefreshing = state.isLoading
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is ForumUiEvent.ShowError -> showToast(event.messageResId, event.arg)
            }
        }
    }

    /**
     * Abre el detalle de una discusión.
     *
     * @param discussionId ID de la discusión.
     * @param discussionName Título visible de la discusión.
     */
    private fun openDiscussion(discussionId: Int, discussionName: String) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                DiscussionFragment.newInstance(discussionId, discussionName),
            )
            addToBackStack(null)
        }
    }

    /**
     * Muestra un toast corto.
     */
    private fun showToast(@StringRes messageResId: Int, arg: String? = null) {
        val message = arg?.let { formatArg -> getString(messageResId, formatArg) }
            ?: getString(messageResId)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}