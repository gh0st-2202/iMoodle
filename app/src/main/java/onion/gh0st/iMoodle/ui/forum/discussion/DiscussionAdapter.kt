package onion.gh0st.iMoodle.ui.forum.discussion

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemForumPostBinding
import onion.gh0st.iMoodle.domain.model.forum.ForumPost
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter de los posts de una discusión.
 *
 * ## Qué muestra
 *
 * Una fila por post, con:
 *
 * - Autor.
 * - Fecha de publicación.
 * - Cuerpo del mensaje (HTML con enlaces clicables).
 *
 * ## Sobre el renderizado del HTML
 *
 * El contenido se renderiza con `HtmlUtils.renderHtml`, que preserva
 * los spans (negritas, cursivas, enlaces). Además, el `TextView`
 * tiene `LinkMovementMethod`, lo que hace que los enlaces del
 * mensaje se puedan pulsar.
 *
 * ## Sobre el formato de fecha
 *
 * Se cachea un `SimpleDateFormat` en el constructor, igual que en
 * `ForumAdapter`. Solo se usa en el UI thread.
 *
 * ## Sobre la fecha inválida
 *
 * A diferencia de `ForumAdapter`, aquí **sí** se comprueba si la
 * fecha es válida (`created > 0`). Si no lo es (por ejemplo, porque
 * Moodle no la envía), se muestra un texto de fallback
 * (`R.string.label_unknown_date`). Es una mejora de robustez.
 */
class DiscussionAdapter : ListAdapter<ForumPost, DiscussionAdapter.VH>(DIFF) {

    /**
     * Formateador de fecha, cacheado.
     *
     * Se crea una sola vez con el locale del dispositivo. Si el
     * usuario cambia el locale, la Activity se recrea y el adapter
     * también. Solo se usa en el UI thread, así que no hay riesgo
     * de concurrencia.
     */
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * `ViewHolder` de un post.
     */
    inner class VH(
        val binding: ItemForumPostBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde autor, fecha y contenido.
         *
         * ## Sobre el contenido
         *
         * Se renderiza con `HtmlUtils.renderHtml`, que preserva los
         * spans (negritas, cursivas, enlaces). El `TextView` tiene
         * `LinkMovementMethod`, así que los enlaces son clicables.
         *
         * @param post Post a mostrar.
         */
        fun bind(post: ForumPost) = with(binding) {
            txtPostAuthor.text = post.author
            txtPostDate.text = formatDate(post.created)
            txtPostContent.text = HtmlUtils.renderHtml(post.message)
            txtPostContent.movementMethod = LinkMovementMethod.getInstance()
        }

        /**
         * Formatea el timestamp del post, o muestra un fallback.
         *
         * @param createdAt Timestamp en segundos.
         * @return Fecha formateada `dd/MM/yyyy HH:mm`, o el texto de
         *   `R.string.label_unknown_date` si el timestamp no es
         *   válido.
         */
        private fun formatDate(createdAt: Long): String =
            if (createdAt > 0) {
                dateFormatter.format(Date(createdAt * 1000))
            } else {
                binding.root.context.getString(R.string.label_unknown_date)
            }
    }

    /**
     * Crea el `ViewHolder` de un post.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemForumPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde el post de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` de posts.
         *
         * Dos posts son iguales si tienen el mismo `id`.
         */
        private val DIFF = object : DiffUtil.ItemCallback<ForumPost>() {
            override fun areItemsTheSame(
                oldItem: ForumPost,
                newItem: ForumPost,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ForumPost,
                newItem: ForumPost,
            ) = oldItem == newItem
        }
    }
}