package onion.gh0st.iMoodle.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.domain.model.calendar.CalendarEvent
import onion.gh0st.iMoodle.databinding.ItemCalendarEventBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cabecera de la agenda.
 *
 * ## Qué es una cabecera
 *
 * La agenda muestra los eventos agrupados por día. Antes de cada
 * grupo de eventos del mismo día, aparece una cabecera con el
 * nombre del día. Puede ser:
 *
 * - **"Hoy"** (si el día es el actual).
 * - **"Mañana"** (si el día es el siguiente).
 * - **"Lunes, 15 de enero"** (cualquier otro día).
 *
 * ## Por qué es un `sealed interface`
 *
 * Porque los tipos posibles son fijos y conocidos. Con un `sealed`,
 * el compilador obliga a hacer un `when` exhaustivo. Si se añade un
 * tipo nuevo (por ejemplo, "Ayer"), cualquier `when` que no lo
 * contemple dará error de compilación.
 */
sealed interface CalendarHeader {

    /** El día es hoy. */
    data object Today : CalendarHeader

    /** El día es mañana. */
    data object Tomorrow : CalendarHeader

    /**
     * Cualquier otro día.
     *
     * @property date Fecha del día.
     */
    data class FormattedDate(val date: Date) : CalendarHeader
}

/**
 * Item de la agenda.
 *
 * ## Qué tipos hay
 *
 * - **Header**: cabecera de un día.
 * - **Event**: un evento del calendario.
 *
 * La agenda es una lista plana que mezcla ambos tipos. El adapter se
 * encarga de renderizar cada uno de forma distinta.
 */
sealed class CalendarItem {

    /**
     * Cabecera de un día.
     *
     * @property header Tipo de cabecera (Hoy, Mañana, fecha
     *   formateada).
     */
    data class Header(val header: CalendarHeader) : CalendarItem()

    /**
     * Evento del calendario.
     *
     * @property data Datos del evento.
     */
    data class Event(val data: CalendarEvent) : CalendarItem()
}

/**
 * Adapter de la lista de la agenda.
 *
 * ## Qué hace
 *
 * Renderiza la lista mixta de cabeceras y eventos. Cada tipo tiene su
 * propio `ViewHolder`:
 *
 * - **HeaderViewHolder**: usa un `TextView` del sistema para mostrar
 *   el día.
 * - **EventViewHolder**: usa un layout propio con nombre, hora,
 *   curso e icono.
 *
 * ## Por qué `ListAdapter` y no `RecyclerView.Adapter`
 *
 * Porque `ListAdapter` gestiona automáticamente el `DiffUtil`, que
 * calcula qué filas han cambiado y solo redibuja esas. Es más
 * eficiente y menos propenso a errores.
 *
 * ## Sobre el formateo de fechas
 *
 * Los formatos (`HH:mm` para horas, `EEEE, d 'de' MMMM` para
 * cabeceras) se crean una sola vez en los constructores de los
 * formateadores. Así no se crean en cada `bind`, que sería costoso.
 *
 * @param onEventClick Callback al pulsar un evento. El Fragment
 *   decide a qué pantalla navegar.
 */
class CalendarAdapter(
    private val onEventClick: (CalendarEvent) -> Unit,
) : ListAdapter<CalendarItem, RecyclerView.ViewHolder>(DIFF) {

    /** Formateador de horas, cacheado. */
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Formateador de cabeceras, cacheado. */
    private val headerDateFormatter = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())

    companion object {
        /** Tipo de vista de cabecera. */
        private const val TYPE_HEADER = 0

        /** Tipo de vista de evento. */
        private const val TYPE_EVENT = 1

        /**
         * `DiffUtil` que decide cuándo dos items son iguales.
         *
         * ## Comportamiento
         *
         * - **Cabeceras**: son iguales si tienen el mismo tipo
         *   (Hoy = Hoy, FormattedDate con la misma fecha = iguales).
         * - **Eventos**: son iguales si tienen el mismo `id`.
         * - **Tipos cruzados** (cabecera vs evento): siempre son
         *   distintos.
         *
         * ## Por qué `areContentsTheSame` usa `==`
         *
         * Porque los items son `data class`, y `==` compara todos los
         * campos. Si algún campo cambia, se considera "contenido
         * distinto" y se redibuja.
         */
        private val DIFF = object : DiffUtil.ItemCallback<CalendarItem>() {
            override fun areItemsTheSame(
                oldItem: CalendarItem,
                newItem: CalendarItem,
            ): Boolean = when {
                oldItem is CalendarItem.Header && newItem is CalendarItem.Header ->
                    oldItem.header == newItem.header

                oldItem is CalendarItem.Event && newItem is CalendarItem.Event ->
                    oldItem.data.id == newItem.data.id

                else -> false
            }

            override fun areContentsTheSame(
                oldItem: CalendarItem,
                newItem: CalendarItem,
            ): Boolean = oldItem == newItem
        }
    }

    /**
     * Devuelve el tipo de vista de un item.
     *
     * @param position Posición del item.
     * @return `TYPE_HEADER` o `TYPE_EVENT`.
     */
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CalendarItem.Header -> TYPE_HEADER
        is CalendarItem.Event -> TYPE_EVENT
    }

    /**
     * Crea el `ViewHolder` correspondiente.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista.
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val headerView = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            HeaderViewHolder(headerView)
        } else {
            val eventBinding = ItemCalendarEventBinding.inflate(
                LayoutInflater.from(parent.context), parent, false,
            )
            EventViewHolder(eventBinding)
        }
    }

    /**
     * Binde el item al `ViewHolder` correspondiente.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CalendarItem.Header -> (holder as HeaderViewHolder).bind(item.header)
            is CalendarItem.Event -> (holder as EventViewHolder).bind(item.data, onEventClick)
        }
    }

    /**
     * `ViewHolder` de cabecera de día.
     *
     * ## Por qué usa `simple_list_item_1`
     *
     * Porque la cabecera solo necesita mostrar un texto, sin
     * estructura. Usar un layout propio sería excesivo.
     *
     * ## Sobre el estilo
     *
     * Se aplica manualmente: negrita, color de acento, padding. Se
     * podría haber hecho con un layout propio, pero así es más
     * compacto.
     */
    inner class HeaderViewHolder(headerView: View) : RecyclerView.ViewHolder(headerView) {

        /** `TextView` de la cabecera. */
        private val titleTextView = headerView as TextView

        /**
         * Binde el texto de la cabecera.
         *
         * @param header Cabecera a mostrar.
         */
        fun bind(header: CalendarHeader) {
            val resources = titleTextView.resources
            titleTextView.text = resolveHeaderText(header)
            titleTextView.textSize = 16f
            titleTextView.setTypeface(null, android.graphics.Typeface.BOLD)
            titleTextView.setTextColor(titleTextView.context.getColor(R.color.color_acento))
            titleTextView.setPadding(
                resources.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_top_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_bottom_padding),
            )
        }

        /**
         * Resuelve el texto de la cabecera según su tipo.
         *
         * @param header Cabecera.
         * @return El texto a mostrar.
         */
        private fun resolveHeaderText(header: CalendarHeader): String = when (header) {
            CalendarHeader.Today -> titleTextView.context.getString(R.string.label_today)
            CalendarHeader.Tomorrow -> titleTextView.context.getString(R.string.label_tomorrow)
            is CalendarHeader.FormattedDate -> headerDateFormatter
                .format(header.date)
                .replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * `ViewHolder` de un evento del calendario.
     *
     * Muestra el nombre, la hora, el curso (si lo tiene) y un icono
     * según el tipo de evento.
     */
    inner class EventViewHolder(
        val binding: ItemCalendarEventBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde los datos del evento.
         *
         * @param event Evento a mostrar.
         * @param onClick Callback al pulsar el evento.
         */
        fun bind(event: CalendarEvent, onClick: (CalendarEvent) -> Unit) = with(binding) {
            txtEventName.text = event.name
            txtEventTime.text = timeFormatter.format(Date(event.timestart * 1000))

            if (event.courseName != null) {
                txtEventCourse.text = event.courseName
                txtEventCourse.visibility = View.VISIBLE
            } else {
                txtEventCourse.visibility = View.GONE
            }

            imgEventIcon.setImageResource(iconFor(event.eventType))

            root.setOnClickListener { onClick(event) }
        }

        /**
         * Devuelve el icono correspondiente al tipo de evento.
         *
         * @param eventType Tipo de evento (`"assign"`, `"forum"`,
         *   etc.).
         * @return ID del drawable del icono.
         */
        private fun iconFor(eventType: String): Int = when (eventType) {
            "assign" -> R.drawable.ic_courses
            "quiz" -> R.drawable.ic_courses
            "forum" -> R.drawable.ic_messages
            "course" -> R.drawable.ic_folder
            else -> R.drawable.ic_calendar
        }
    }
}