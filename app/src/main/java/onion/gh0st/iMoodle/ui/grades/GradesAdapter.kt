package onion.gh0st.iMoodle.ui.grades

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemGradeBinding
import onion.gh0st.iMoodle.databinding.ItemGradeTotalBinding
import onion.gh0st.iMoodle.domain.model.grade.GradeItem
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.common.resolveColorAttr

/**
 * Adapter de la lista de notas de un curso.
 *
 * ## Qué pinta
 *
 * - Cabeceras de sección (Unidad 1, Unidad 2…) en verde.
 * - La fila del total del curso con una tarjeta destacada.
 * - Una fila por actividad con su nombre, su nota y el comentario de
 *   retroalimentación del profesor si lo hay. Las actividades de tipo
 *   tarea son pulsables para abrir su detalle.
 */
class GradesAdapter(
    private val onGradeClick: (GradeItem) -> Unit,
) : ListAdapter<GradesListItem, RecyclerView.ViewHolder>(DIFF) {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TOTAL = 1
        const val TYPE_ACTIVITY = 2

        val DIFF = object : DiffUtil.ItemCallback<GradesListItem>() {
            override fun areItemsTheSame(oldItem: GradesListItem, newItem: GradesListItem): Boolean =
                when {
                    oldItem is GradesListItem.SectionHeader && newItem is GradesListItem.SectionHeader ->
                        oldItem.title == newItem.title
                    oldItem is GradesListItem.Grade && newItem is GradesListItem.Grade ->
                        oldItem.item.id == newItem.item.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: GradesListItem, newItem: GradesListItem): Boolean =
                oldItem == newItem
        }
    }

    /**
     * Devuelve el tipo de vista de una fila.
     *
     * @param position Posición de la fila.
     * @return `TYPE_HEADER`, `TYPE_TOTAL` o `TYPE_ACTIVITY`.
     */
    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is GradesListItem.SectionHeader -> TYPE_HEADER
        is GradesListItem.Grade -> if (item.item.isCourseTotal) TYPE_TOTAL else TYPE_ACTIVITY
    }

    /**
     * Crea el `ViewHolder` correspondiente.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de fila.
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater, parent)
            TYPE_TOTAL -> TotalViewHolder(ItemGradeTotalBinding.inflate(inflater, parent, false))
            else -> ActivityViewHolder(ItemGradeBinding.inflate(inflater, parent, false))
        }
    }

    /**
     * Binde la fila de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición de la fila.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GradesListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item.title)
            is GradesListItem.Grade -> when (holder) {
                is TotalViewHolder -> holder.bind(item.item)
                is ActivityViewHolder -> holder.bind(item.item, onGradeClick)
            }
        }
    }

    /**
     * `ViewHolder` de cabecera de sección, con el mismo estilo que en
     * el detalle del curso (verde, negrita).
     *
     * @param inflater Inflater de vistas.
     * @param parent Contenedor padre.
     */
    inner class HeaderViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(
            android.widget.TextView(parent.context).apply {
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(parent.context.resolveColorAttr(R.attr.appAccent))
                val res = resources
                setPadding(
                    res.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                    res.getDimensionPixelSize(R.dimen.list_header_top_padding),
                    res.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                    res.getDimensionPixelSize(R.dimen.list_header_bottom_padding),
                )
            },
        ) {

        /** `TextView` de la cabecera. */
        private val titleTextView = itemView as android.widget.TextView

        /**
         * Binde el título de la sección.
         *
         * @param title Nombre de la sección.
         */
        fun bind(title: String) {
            titleTextView.text = title
        }
    }

    /**
     * `ViewHolder` de la tarjeta del total del curso.
     *
     * @param binding Vistas de la tarjeta.
     */
    inner class TotalViewHolder(
        private val binding: ItemGradeTotalBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Pinta el total del curso.
         *
         * @param item Fila del total.
         */
        fun bind(item: GradeItem) = with(binding) {
            txtGradeTotalLabel.text = root.context.getString(R.string.grades_total)
            txtGradeTotalValue.text = item.displayValue()
        }
    }

    /**
     * `ViewHolder` de una actividad calificable.
     *
     * @param binding Vistas de la fila.
     */
    inner class ActivityViewHolder(
        private val binding: ItemGradeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Pinta el nombre, la retroalimentación y la nota.
         *
         * La retroalimentación se oculta si el profesor no dejó
         * ningún comentario, para no dejar un hueco vacío en la fila.
         *
         * @param item Fila de la actividad.
         * @param onClick Callback al pulsar la fila.
         */
        fun bind(item: GradeItem, onClick: (GradeItem) -> Unit) = with(binding) {
            txtGradeName.text = item.name
            txtGradeValue.text = item.displayValue()

            root.isClickable = item.itemModule == "assign"
            root.setOnClickListener {
                if (item.itemModule == "assign") onClick(item)
            }
        }
    }

    /**
     * Compone el texto de la nota que se muestra al alumno.
     *
     * @receiver Fila del libro de calificaciones.
     * @return La nota formateada por Moodle, o un guion si no se puede
     *   ver o si todavía no hay nota.
     */
    private fun GradeItem.displayValue(): String = when {
        isHidden -> "—"
        !gradeFormatted.isNullOrBlank() -> HtmlUtils.stripHtml(gradeFormatted)
        gradeRaw != null -> gradeRaw.toString()
        else -> "—"
    }
}
