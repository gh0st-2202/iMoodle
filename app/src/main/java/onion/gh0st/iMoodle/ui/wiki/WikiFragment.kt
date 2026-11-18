package onion.gh0st.iMoodle.ui.wiki

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
import onion.gh0st.iMoodle.databinding.FragmentWikiBinding
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla de una wiki de Moodle.
 *
 * ## Qué muestra
 *
 * La lista de páginas de la wiki, con una insignia en la portada. Al
 * tocar una página se abre su contenido y el botón flotante crea una
 * página nueva.
 *
 * ## Navegación
 *
 * Se abre con [newInstance] desde `CourseDetailFragment`, dentro de
 * `R.id.detail_container`. Las páginas se abren con
 * [WikiPageFragment] en ese mismo contenedor, así que la flecha de
 * volver regresa a esta lista.
 */
@AndroidEntryPoint
class WikiFragment : Fragment(R.layout.fragment_wiki) {

    /** Nombre de la wiki, recibido como argumento. */
    private var wikiName: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: WikiViewModel by viewModels()

    companion object {

        /**
         * Crea el fragmento para una wiki concreta.
         *
         * @param wikiId ID de la instancia de la wiki.
         * @param wikiName Nombre de la wiki, que se muestra como título.
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(wikiId: Int, wikiName: String): WikiFragment =
            WikiFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.WIKI_ID, wikiId)
                    putString(FragmentArgs.MODULE_NAME, wikiName)
                }
            }
    }

    /**
     * Recupera el nombre de la wiki de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wikiName = arguments?.getString(FragmentArgs.MODULE_NAME) ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentWikiBinding.bind(view)
        setupToolbar(binding)
        binding.appBarWiki.applySystemBarsInsets()
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        binding.fabAddPage.setOnClickListener { showNewPageDialog() }
        observeUi(binding, adapter)
    }

    /**
     * Pone el nombre de la wiki como título y hace que la flecha de
     * volver cierre esta pantalla.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentWikiBinding) {
        binding.toolbarWiki.title = wikiName
        binding.toolbarWiki.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Crea el adaptador de páginas. Al tocar una se abre con
     * [openPage].
     *
     * @return El adaptador de la lista.
     */
    private fun buildAdapter() = WikiAdapter { page -> openPage(page) }

    /**
     * Configura la lista y el gesto de arrastrar para refrescar.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun setupRecyclerView(
        binding: FragmentWikiBinding,
        adapter: WikiAdapter,
    ) {
        binding.rvWiki.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWiki.adapter = adapter
        binding.swipeRefreshWiki.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras
     * la pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private fun observeUi(binding: FragmentWikiBinding, adapter: WikiAdapter) {
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
     * @param binding Vistas de la pantalla.
     * @param adapter Adaptador de la lista.
     */
    private suspend fun observeState(
        binding: FragmentWikiBinding,
        adapter: WikiAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.pages)
            binding.swipeRefreshWiki.isRefreshing = state.isLoading
            binding.fabAddPage.isEnabled = !state.isCreating
            binding.layoutWikiEmpty.isVisible = !state.isLoading && state.pages.isEmpty()
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is WikiUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                is WikiUiEvent.PageCreated -> onPageCreated(event.pageId)
            }
        }
    }

    /**
     * Cierra el diálogo de creación, avisa y abre la página recién
     * creada para que el usuario pueda leerla o seguir editándola.
     *
     * @param pageId ID de la página creada.
     */
    private fun onPageCreated(pageId: Int) {
        dismissNewPageDialog()
        showToast(R.string.toast_wiki_created)
        viewModel.refresh()
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                WikiPageFragment.newInstance(pageId, title = ""),
            )
            addToBackStack(null)
        }
    }

    /**
     * Abre el contenido de una página con [WikiPageFragment].
     *
     * @param page Página que se quiere leer.
     */
    private fun openPage(page: WikiPage) {
        parentFragmentManager.commit {
            replace(
                R.id.detail_container,
                WikiPageFragment.newInstance(page.id, page.title),
            )
            addToBackStack(null)
        }
    }

    /**
     * Abre el diálogo de creación de página.
     */
    private fun showNewPageDialog() {
        if (childFragmentManager.findFragmentByTag(NewWikiPageDialog.TAG) != null) return
        NewWikiPageDialog.newInstance().show(childFragmentManager, NewWikiPageDialog.TAG)
    }

    /**
     * Cierra el diálogo de creación si sigue abierto.
     */
    private fun dismissNewPageDialog() {
        val dialog = childFragmentManager.findFragmentByTag(NewWikiPageDialog.TAG)
        (dialog as? NewWikiPageDialog)?.dismissAllowingStateLoss()
    }
}
