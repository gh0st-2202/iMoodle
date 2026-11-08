package onion.gh0st.iMoodle.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemScheduleSlotBinding
import onion.gh0st.iMoodle.domain.model.schedule.ScheduleSlot

/**
 * Item de la lista del horario.
 *
 * ## Por qué solo hay un tipo
 *
 * Al filtrar siempre por un día específico, no se necesitan
 * cabeceras (headers). Solo se muestra la lista plana de
 * asignaturas de ese día.
 *
 * ## Por qué vive en el mismo fichero que el adapter
 *
 * Porque es su único consumidor, igual que `CourseDetailItem` y
 * `CalendarItem` viven en sus respectivos adapters.
 */
sealed class ScheduleItem {

    /**
     * Bloque de asignatura con su color asignado.
     *
     * @property data Datos del slot.
     * @property colorResId Recurso de color asignado por el
     *   ViewModel (mapeo secuencial sobre la paleta).
     */
    data class Slot(
        val data: ScheduleSlot,
        @param:ColorRes val colorResId: Int,
    ) : ScheduleItem()
}

/**
 * Adapter del horario.
 *
 * ## Qué hace
 *
 * Renderiza cada franja horaria con:
 *
 * - Franja lateral de color (según la asignatura).
 * - Rango horario ("8:30 - 9:30").
 * - Nombre de la asignatura.
 * - Profesor con prefijo ("Prof.: ...").
 *
 * ## Sobre los colores
 *
 * El adapter **no decide** los colores: solo pinta el que le llega
 * en `ScheduleItem.Slot.colorResId`. El ViewModel es el que hace el
 * mapeo secuencial sobre la paleta `COLOR_PALETTE`.
 *
 * ## Sobre `@param:ColorRes`
 *
 * Se usa `@param:ColorRes` en vez de `@ColorRes` para que Kotlin no
 * avise sobre la ubicación de la anotación. Mismo caso que
 * `@param:StringRes`.
 */
class ScheduleAdapter : ListAdapter<ScheduleItem, ScheduleAdapter.SlotViewHolder>(DIFF) {

    companion object {

        /**
         * Paleta de colores para las asignaturas.
         *
         * ## Por qué 24
         *
         * Porque 24 colores son suficientes para una lista de
         * asignaturas típica (5-7 asignaturas por curso). Con más
         * asignaturas, se empiezan a repetir colores, pero es un caso
         * raro.
         *
         * ## Sobre la asignación
         *
         * El ViewModel asigna los colores de forma secuencial (no por
         * `hashCode`) para garantizar que no se repitan mientras haya
         * menos asignaturas que colores.
         */
        internal val COLOR_PALETTE = intArrayOf(
            R.color.schedule_color_0, R.color.schedule_color_1,
            R.color.schedule_color_2, R.color.schedule_color_3,
            R.color.schedule_color_4, R.color.schedule_color_5,
            R.color.schedule_color_6, R.color.schedule_color_7,
            R.color.schedule_color_8, R.color.schedule_color_9,
            R.color.schedule_color_10, R.color.schedule_color_11,
            R.color.schedule_color_12, R.color.schedule_color_13,
            R.color.schedule_color_14, R.color.schedule_color_15,
            R.color.schedule_color_16, R.color.schedule_color_17,
            R.color.schedule_color_18, R.color.schedule_color_19,
            R.color.schedule_color_20, R.color.schedule_color_21,
            R.color.schedule_color_22, R.color.schedule_color_23,
        )

        /**
         * `DiffUtil` de slots.
         *
         * Dos slots son iguales si tienen la misma asignatura y el
         * mismo `section` (orden dentro del día).
         */
        private val DIFF = object : DiffUtil.ItemCallback<ScheduleItem>() {
            override fun areItemsTheSame(
                oldItem: ScheduleItem,
                newItem: ScheduleItem,
            ): Boolean = when {
                oldItem is ScheduleItem.Slot && newItem is ScheduleItem.Slot ->
                    oldItem.data.codeSection == newItem.data.codeSection &&
                            oldItem.data.section == newItem.data.section

                else -> false
            }

            override fun areContentsTheSame(
                oldItem: ScheduleItem,
                newItem: ScheduleItem,
            ): Boolean = oldItem == newItem
        }
    }

    /**
     * Crea el `ViewHolder` de un slot.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return SlotViewHolder(ItemScheduleSlotBinding.inflate(layoutInflater, parent, false))
    }

    /**
     * Binde el slot de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val item = getItem(position)
        if (item is ScheduleItem.Slot) {
            holder.bind(item)
        }
    }

    /**
     * `ViewHolder` de un bloque de asignatura.
     */
    inner class SlotViewHolder(
        private val binding: ItemScheduleSlotBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde la franja lateral de color, el rango horario, la
         * asignatura y el profesor con prefijo.
         *
         * @param item Slot con la asignatura y el color ya resuelto.
         */
        fun bind(item: ScheduleItem.Slot) = with(binding) {
            val slot = item.data

            val color = ContextCompat.getColor(root.context, item.colorResId)
            colorIndicator.setBackgroundColor(color)

            txtTimeRange.text = slot.hoursection
            txtSubject.text = slot.subject
            txtProfessor.text = root.context.getString(
                R.string.schedule_professor_prefix,
                slot.professor,
            )
        }
    }
}