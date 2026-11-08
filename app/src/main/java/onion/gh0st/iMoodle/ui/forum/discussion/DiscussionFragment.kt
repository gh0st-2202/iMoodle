package onion.gh0st.iMoodle.ui.forum.discussion

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentDiscussionBinding

/**
 * Pantalla con los posts de una discusión de foro.
 *
 * ## Qué muestra
 *
 * Una lista de posts de la discusión, cada uno con:
 *
 * - Autor.
 * - Fecha.
 * - Contenido (HTML con enlaces clicables).
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `DiscussionViewModel`.
 *
 * ## Manejo de errores
 *
 * Los errores se consumen como eventos de un solo uso
 * (`DiscussionUiEvent.ShowError`).
 */
@AndroidEntryPoint
class DiscussionFragment : Fragment(R.layout.fragment_discussion) {

    /** Título de la discusión, recibido como argumento. */
    private var discussionName: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: DiscussionViewModel by viewModels()

    companion object {

        /**
         * Construye una instancia del Fragment.
         *
         * @param discussionId ID de la discusión.
         * @param discussionName Título visible de la discusión.
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(discussionId: Int, discussionName: String): DiscussionFragment =
            DiscussionFragment().apply {
                arguments = Bundle().apply {
                    putInt("DISCUSSION_ID", discussionId)
                    putString("DISCUSSION_NAME", discussionName)
                }
            }
    }

    /**
     * Recupera el título de la discusión de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discussionName = arguments?.getString("DISCUSSION_NAME") ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDiscussionBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        val adapter = DiscussionAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Configura el toolbar con el título y botón atrás.
     */
    private fun setupToolbar(binding: FragmentDiscussionBinding) {
        binding.toolbarDiscussion.title = discussionName
        binding.toolbarDiscussion.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     */
    private fun setupWindowInsets(binding: FragmentDiscussionBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarDiscussion) { view, insets ->
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
     * Configura el `RecyclerView` y el pull-to-refresh.
     */
    private fun setupRecyclerView(
        binding: FragmentDiscussionBinding,
        adapter: DiscussionAdapter,
    ) {
        binding.rvDiscussion.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiscussion.adapter = adapter
        binding.swipeRefreshDiscussion.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     */
    private fun observeUi(binding: FragmentDiscussionBinding, adapter: DiscussionAdapter) {
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
    private suspend fun observeState(
        binding: FragmentDiscussionBinding,
        adapter: DiscussionAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.posts)
            binding.swipeRefreshDiscussion.isRefreshing = state.isLoading
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is DiscussionUiEvent.ShowError -> showToast(event.messageResId, event.arg)
            }
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