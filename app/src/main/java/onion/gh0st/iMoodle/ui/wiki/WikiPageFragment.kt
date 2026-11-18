package onion.gh0st.iMoodle.ui.wiki

import android.os.Bundle
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
import onion.gh0st.iMoodle.databinding.FragmentWikiPageBinding
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.FormatUtils
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Pantalla con el contenido de una página de wiki.
 *
 * ## Qué muestra
 *
 * El título de la página, su fecha de modificación y el contenido con
 * el formato que devuelve Moodle. Si la wiki está vacía, un aviso en
 * lugar del texto.
 *
 * ## Edición
 *
 * La acción "Editar" de la barra superior solo aparece si Moodle
 * informa de que el usuario puede modificar la página (`canEdit`). Al
 * pulsarla se abre `EditWikiPageDialog`, que pide a
 * `WikiPageViewModel` el contenido original y guarda los cambios.
 *
 * ## Navegación
 *
 * Se abre con [newInstance] desde `WikiFragment`, dentro de
 * `R.id.detail_container`.
 */
@AndroidEntryPoint
class WikiPageFragment : Fragment(R.layout.fragment_wiki_page) {

    /** Título recibido como argumento, para mostrarlo mientras carga. */
    private var initialTitle: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: WikiPageViewModel by viewModels()

    companion object {

        /**
         * Crea el fragmento para una página concreta.
         *
         * @param pageId ID de la página.
         * @param title Título de la página, usado mientras se carga el
         *   contenido.
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(pageId: Int, title: String): WikiPageFragment =
            WikiPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArgs.WIKI_PAGE_ID, pageId)
                    putString(FragmentArgs.WIKI_PAGE_TITLE, title)
                }
            }
    }

    /**
     * Recupera el título de los argumentos.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialTitle = arguments?.getString(FragmentArgs.WIKI_PAGE_TITLE).orEmpty()
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentWikiPageBinding.bind(view)
        setupToolbar(binding)
        binding.appBarWikiPage.applySystemBarsInsets()
        binding.swipeRefreshWikiPage.setOnRefreshListener { viewModel.refresh() }
        observeUi(binding)
    }

    /**
     * Configura la barra superior: título, flecha de volver y acción de
     * edición.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupToolbar(binding: FragmentWikiPageBinding) {
        binding.toolbarWikiPage.title = initialTitle
        binding.toolbarWikiPage.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbarWikiPage.inflateMenu(R.menu.menu_wiki_page)
        binding.toolbarWikiPage.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId != R.id.action_wiki_edit) return@setOnMenuItemClickListener false
            showEditDialog()
            true
        }
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras
     * la pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun observeUi(binding: FragmentWikiPageBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Refleja el estado del ViewModel en la UI.
     *
     * @param binding Vistas de la pantalla.
     */
    private suspend fun observeState(binding: FragmentWikiPageBinding) {
        viewModel.state.collect { state ->
            binding.swipeRefreshWikiPage.isRefreshing = state.isLoading
            binding.toolbarWikiPage.menu.findItem(R.id.action_wiki_edit).isVisible =
                state.page?.canEdit == true
            state.page?.let { page -> bindPage(binding, page) }
        }
    }

    /**
     * Pinta el contenido de la página.
     *
     * Antes de renderizarlo se quita el índice y los enlaces
     * «[Editar]» que Moodle añade para su versión web: en la app no
     * llevan a ningún sitio y ensucian los títulos.
     *
     * @param binding Vistas de la pantalla.
     * @param page Página cargada.
     */
    private fun bindPage(binding: FragmentWikiPageBinding, page: WikiPage) {
        binding.toolbarWikiPage.title = page.title.ifBlank { initialTitle }
        binding.txtWikiContentTitle.text = page.title
        binding.txtWikiContentModified.isVisible = page.modifiedAt > 0
        binding.txtWikiContentModified.text = getString(
            R.string.label_wiki_modified,
            FormatUtils.formatDateTime(page.modifiedAt),
        )

        val content = HtmlUtils.stripWikiChrome(page.content)
        val hasContent = content.isNotBlank()
        binding.txtWikiContent.isVisible = hasContent
        binding.txtWikiContentEmpty.isVisible = !hasContent
        if (hasContent) {
            binding.txtWikiContent.text = HtmlUtils.renderHtml(content)
            binding.txtWikiContent.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /**
     * Consume los eventos de un solo uso.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is WikiPageUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                WikiPageUiEvent.PageSaved -> {
                    dismissEditDialog()
                    showToast(R.string.toast_wiki_saved)
                }
            }
        }
    }

    /**
     * Abre el editor con el contenido original de la página.
     *
     * Se descarta cualquier borrador anterior para que el editor pida
     * siempre el contenido que Moodle tiene en ese momento.
     */
    private fun showEditDialog() {
        if (childFragmentManager.findFragmentByTag(EditWikiPageDialog.TAG) != null) return
        viewModel.discardDraft()
        EditWikiPageDialog.newInstance().show(childFragmentManager, EditWikiPageDialog.TAG)
    }

    /**
     * Cierra el editor si sigue abierto.
     */
    private fun dismissEditDialog() {
        val dialog = childFragmentManager.findFragmentByTag(EditWikiPageDialog.TAG)
        (dialog as? EditWikiPageDialog)?.dismissAllowingStateLoss()
    }
}
