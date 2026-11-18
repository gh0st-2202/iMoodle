package onion.gh0st.iMoodle.ui.wiki

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemWikiPageBinding
import onion.gh0st.iMoodle.domain.model.wiki.WikiPage
import onion.gh0st.iMoodle.ui.common.FormatUtils

/**
 * Adapter de la lista de páginas de una wiki.
 *
 * ## Qué muestra
 *
 * Una tarjeta por página con su título, una insignia "Portada" si es la
 * página principal y la fecha de la última modificación (oculta si
 * Moodle no la informa).
 *
 * @param onClick Acción al pulsar una página.
 */
class WikiAdapter(
    private val onClick: (WikiPage) -> Unit,
) : ListAdapter<WikiPage, WikiAdapter.VH>(DIFF) {

    /**
     * `ViewHolder` de una página.
     */
    inner class VH(
        val binding: ItemWikiPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde una página en la tarjeta.
         *
         * @param page Página a mostrar.
         */
        fun bind(page: WikiPage) = with(binding) {
            txtWikiPageTitle.text = page.title
            txtWikiPageBadge.isVisible = page.isFirstPage

            txtWikiPageModified.isVisible = page.modifiedAt > 0
            if (page.modifiedAt > 0) {
                txtWikiPageModified.text = root.context.getString(
                    R.string.label_wiki_modified,
                    FormatUtils.formatDateTime(page.modifiedAt),
                )
            }

            root.setOnClickListener { onClick(page) }
        }
    }

    /**
     * Crea el `ViewHolder` de una página.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemWikiPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde la página de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` de páginas.
         *
         * Dos páginas son iguales si tienen el mismo `id`.
         */
        private val DIFF = object : DiffUtil.ItemCallback<WikiPage>() {
            override fun areItemsTheSame(
                oldItem: WikiPage,
                newItem: WikiPage,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: WikiPage,
                newItem: WikiPage,
            ) = oldItem == newItem
        }
    }
}
