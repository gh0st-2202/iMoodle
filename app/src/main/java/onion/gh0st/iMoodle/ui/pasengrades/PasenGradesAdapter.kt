package onion.gh0st.iMoodle.ui.pasengrades

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemPasenSubjectBinding
import onion.gh0st.iMoodle.domain.model.pasen.PasenSubjectGrade

/**
 * Adapter de las asignaturas de una convocatoria de iPasen.
 *
 * ## Qué pinta
 *
 * Una fila por asignatura con su nota y, como texto secundario, los
 * campos adicionales que iPasen envíe (media de la clase, anotación,
 * etc.).
 */
class PasenGradesAdapter :
    ListAdapter<PasenSubjectGrade, PasenGradesAdapter.SubjectViewHolder>(DIFF) {

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PasenSubjectGrade>() {
            override fun areItemsTheSame(oldItem: PasenSubjectGrade, newItem: PasenSubjectGrade): Boolean =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: PasenSubjectGrade, newItem: PasenSubjectGrade): Boolean =
                oldItem == newItem
        }

        /** Claves ya pintadas de otra forma, para no duplicarlas. */
        val KNOWN_KEYS = setOf(
            "grade", "nota", "NOTA", "mark", "value", "calificacion", "calificación",
            "average", "media", "averageGrade", "mediaClase",
            "annotation", "anotacion", "anotación", "observacion", "observación", "comments",
            "subject", "name", "materia", "D_MATERIA", "asignatura",
        )
    }

    /**
     * Crea el `ViewHolder` de una asignatura.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (único).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder =
        SubjectViewHolder(
            ItemPasenSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    /**
     * Binde la asignatura de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición de la fila.
     */
    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * `ViewHolder` de una asignatura.
     *
     * @param binding Vistas de la fila.
     */
    inner class SubjectViewHolder(
        private val binding: ItemPasenSubjectBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Pinta el nombre, la nota y los campos adicionales.
         *
         * @param subject Asignatura a mostrar.
         */
        fun bind(subject: PasenSubjectGrade) = with(binding) {
            txtSubjectName.text = subject.name
            txtSubjectGrade.text = subject.grade ?: "—"
            val extras = buildList {
                subject.average?.let { average ->
                    add(root.context.getString(R.string.pasen_class_average, average))
                }
                subject.annotation?.let { annotation -> add(annotation) }
                subject.fields
                    .filter { (key, _) -> key !in KNOWN_KEYS }
                    .forEach { (key, value) -> add("$key: $value") }
            }
            txtSubjectExtra.isVisible = extras.isNotEmpty()
            if (extras.isNotEmpty()) txtSubjectExtra.text = extras.joinToString(" · ")
        }
    }
}
