package onion.gh0st.iMoodle.ui.glossary

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemGlossaryEntryBinding
import onion.gh0st.iMoodle.domain.model.glossary.GlossaryEntry
import onion.gh0st.iMoodle.ui.common.FormatUtils
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Adapter de la lista de entradas de un glosario.
 *
 * ## Qué muestra
 *
 * Una tarjeta por entrada con:
 *
 * - El concepto, en negrita.
 * - La definición, con su formato HTML y enlaces clicables.
 * - El autor y la fecha.
 * - Dos acciones: editar y borrar.
 *
 * ## Sobre las acciones
 *
 * Los botones se muestran siempre. La app no conoce de antemano qué
 * entradas puede modificar el usuario (Moodle no lo informa en el
 * listado), así que es el servidor quien rechaza la operación si no hay
 * permiso y la pantalla enseña el motivo. Es preferible a esconder
 * acciones que sí están permitidas.
 *
 * ## Sobre el formato de fecha
 *
 * Se usa `FormatUtils.formatDateTime`, que trabaja con `java.time` y es
 * seguro entre hilos, en lugar de crear un `SimpleDateFormat` por fila.
 *
 * @param onEdit Acción al pulsar "editar".
 * @param onDelete Acción al pulsar "borrar".
 */
class GlossaryAdapter(
    private val onEdit: (GlossaryEntry) -> Unit,
    private val onDelete: (GlossaryEntry) -> Unit,
) : ListAdapter<GlossaryEntry, GlossaryAdapter.VH>(DIFF) {

    /**
     * `ViewHolder` de una entrada.
     */
    inner class VH(
        val binding: ItemGlossaryEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde una entrada en la tarjeta.
         *
         * @param entry Entrada a mostrar.
         */
        fun bind(entry: GlossaryEntry) = with(binding) {
            txtGlossaryConcept.text = entry.concept
            txtGlossaryDefinition.text = HtmlUtils.renderHtml(entry.definition)
            txtGlossaryDefinition.movementMethod = LinkMovementMethod.getInstance()

            val formattedDate = FormatUtils.formatDateTime(entry.createdAt)
            txtGlossaryAuthor.text = root.context.getString(
                R.string.label_glossary_author_date,
                entry.author,
                formattedDate,
            )

            btnEditEntry.setOnClickListener { onEdit(entry) }
            btnDeleteEntry.setOnClickListener { onDelete(entry) }
        }
    }

    /**
     * Crea el `ViewHolder` de una entrada.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGlossaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde la entrada de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` de entradas.
         *
         * Dos entradas son iguales si tienen el mismo `id`; su contenido
         * se compara con `==` porque son `data class`.
         */
        private val DIFF = object : DiffUtil.ItemCallback<GlossaryEntry>() {
            override fun areItemsTheSame(
                oldItem: GlossaryEntry,
                newItem: GlossaryEntry,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: GlossaryEntry,
                newItem: GlossaryEntry,
            ) = oldItem == newItem
        }
    }
}
