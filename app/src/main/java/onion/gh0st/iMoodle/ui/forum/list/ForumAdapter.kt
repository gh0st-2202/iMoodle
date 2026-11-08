package onion.gh0st.iMoodle.ui.forum.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.forum.ForumDiscussion
import onion.gh0st.iMoodle.databinding.ItemForumDiscussionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter de la lista de discusiones de un foro.
 *
 * ## Qué muestra
 *
 * Una fila por discusión, con:
 *
 * - Nombre de la discusión.
 * - Autor y fecha.
 * - Número de respuestas (oculto si es 0).
 *
 * ## Sobre el formato de fecha
 *
 * Se cachea un `SimpleDateFormat` en el constructor. Se crea una
 * sola vez con el locale del dispositivo, así que si el usuario
 * cambia el idioma, la Activity se recrea y el adapter también. Solo
 * se usa en el UI thread, así que no hay riesgo de concurrencia.
 *
 * ## Sobre el plural de respuestas
 *
 * Se usa `getQuantityString` (plurals) para que muestre "1
 * respuesta" o "N respuestas" según corresponda. Es más correcto
 * gramaticalmente que un "N respuestas" fijo.
 *
 * @param onClick Callback al pulsar una discusión.
 */
class ForumAdapter(
    private val onClick: (ForumDiscussion) -> Unit,
) : ListAdapter<ForumDiscussion, ForumAdapter.VH>(DIFF) {

    /**
     * Formateador de fecha, cacheado.
     *
     * ## Por qué cachearlo
     *
     * Crear un `SimpleDateFormat` en cada `bind` es costoso. Se crea
     * una vez y se reutiliza, mejorando el rendimiento.
     *
     * ## Sobre la seguridad de hilos
     *
     * `SimpleDateFormat` no es thread-safe, pero solo se usa en el
     * UI thread (desde `VH.bind`), así que no hay riesgo.
     */
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * `ViewHolder` de una discusión.
     */
    inner class VH(
        val binding: ItemForumDiscussionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde una discusión en la fila.
         *
         * ## Comportamiento
         *
         * - **Nombre**: siempre se muestra.
         * - **Autor + fecha**: se combinan en un solo string con
         *   `label_forum_author_date`.
         * - **Contador de respuestas**: se muestra solo si
         *   `numReplies > 0`.
         *
         * @param discussion Discusión a mostrar.
         */
        fun bind(discussion: ForumDiscussion) = with(binding) {
            txtDiscussionName.text = discussion.name

            val formattedDate = dateFormatter.format(Date(discussion.created * 1000))
            txtDiscussionAuthor.text = root.context.getString(
                R.string.label_forum_author_date,
                discussion.author,
                formattedDate,
            )

            if (discussion.numReplies > 0) {
                txtDiscussionReplies.isVisible = true
                txtDiscussionReplies.text = root.resources.getQuantityString(
                    R.plurals.label_forum_replies,
                    discussion.numReplies,
                    discussion.numReplies,
                )
            } else {
                txtDiscussionReplies.isVisible = false
            }

            root.setOnClickListener { onClick(discussion) }
        }
    }

    /**
     * Crea el `ViewHolder` de una fila de discusión.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemForumDiscussionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde la discusión de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` de discusiones.
         *
         * Dos discusiones son iguales si tienen el mismo `id`.
         */
        private val DIFF = object : DiffUtil.ItemCallback<ForumDiscussion>() {
            override fun areItemsTheSame(
                oldItem: ForumDiscussion,
                newItem: ForumDiscussion,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ForumDiscussion,
                newItem: ForumDiscussion,
            ) = oldItem == newItem
        }
    }
}