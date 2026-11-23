package onion.gh0st.iMoodle.ui.courses.detail

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemLabelBinding
import onion.gh0st.iMoodle.databinding.ItemModuleBinding
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Item del adapter de detalle de curso.
 *
 * ## Qué tipos hay
 *
 * - **SectionHeader**: cabecera de una sección (nombre de la
 *   sección).
 * - **Module**: módulo navegable (tarea, foro, recurso, enlace…).
 *   Al pulsarlo, se navega a una pantalla.
 * - **Label**: contenido inline del profesor (texto que aparece
 *   dentro del curso, sin acción al pulsar).
 *
 * ## Por qué `Label` es un tipo separado
 *
 * Porque los `ModuleType.Label` no son navegables: son texto
 * informativo. El ViewModel los convierte en `CourseDetailItem.Label`
 * antes de pasárselos al adapter, para que el adapter no tenga que
 * saber que Moodle tiene un tipo llamado `label`.
 */
sealed class CourseDetailItem {

    /**
     * Cabecera que agrupa los módulos de una sección.
     *
     * @property title Nombre de la sección.
     */
    data class SectionHeader(val title: String) : CourseDetailItem()

    /**
     * Módulo navegable.
     *
     * @property type Tipo de módulo (Assignment, Forum, Resource…).
     *   El adapter lo usa para decidir el icono.
     */
    data class Module(val type: ModuleType) : CourseDetailItem()

    /**
     * Contenido inline (etiqueta HTML del profesor).
     *
     * @property content Contenido de la etiqueta, en HTML.
     */
    data class Label(val content: String) : CourseDetailItem()
}

/**
 * Adapter del detalle de curso.
 *
 * ## Qué hace
 *
 * Renderiza la lista mixta de cabeceras, módulos y etiquetas. Cada
 * tipo tiene su propio `ViewHolder`:
 *
 * - **HeaderViewHolder**: usa `simple_list_item_1` del sistema para
 *   mostrar el nombre de la sección con estilo de cabecera.
 * - **ModuleViewHolder**: usa `ItemModuleBinding` para mostrar un
 *   icono, el nombre del módulo y una descripción opcional.
 * - **LabelViewHolder**: usa `ItemLabelBinding` para mostrar el
 *   contenido HTML de la etiqueta.
 *
 * ## Sobre el `when` exhaustivo en `iconFor`
 *
 * El `when` de `ModuleType` en `iconFor` es exhaustivo. Si en el
 * futuro se añade un tipo nuevo (por ejemplo, `Quiz`), el
 * compilador obligará a actualizar `iconFor`. Así es imposible
 * olvidarse de un tipo nuevo.
 *
 * ## Sobre el padding de las cabeceras
 *
 * El padding de las cabeceras se resuelve desde `R.dimen` (dp), no
 * en px hardcodeados. Esto garantiza que se vea bien en cualquier
 * densidad de pantalla.
 *
 * @param onModuleClick Callback al pulsar un módulo navegable. El
 *   Fragment decide a qué pantalla ir según `ModuleType`.
 * @param onCompletionClick Callback al pulsar el icono de finalización
 *   de un módulo marcable a mano.
 */
class CourseDetailAdapter(
    private val onModuleClick: (ModuleType) -> Unit,
    private val onCompletionClick: (CourseModule) -> Unit,
) : ListAdapter<CourseDetailItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        /** Tipo de vista de cabecera de sección. */
        private const val TYPE_HEADER = 0

        /** Tipo de vista de módulo navegable. */
        private const val TYPE_MODULE = 1

        /** Tipo de vista de etiqueta. */
        private const val TYPE_LABEL = 2

        /**
         * `DiffUtil` que decide cuándo dos items son iguales.
         *
         * ## Comportamiento
         *
         * - **Cabeceras**: iguales si tienen el mismo título.
         * - **Módulos**: iguales si tienen el mismo `module.id`
         *   (`cmid`).
         * - **Etiquetas**: iguales si tienen el mismo contenido.
         * - **Tipos cruzados**: siempre distintos.
         *
         * ## Por qué `areContentsTheSame` usa `==`
         *
         * Porque los items son `data class`, y `==` compara todos los
         * campos. Cualquier cambio cuenta como "contenido distinto".
         */
        private val DIFF = object : DiffUtil.ItemCallback<CourseDetailItem>() {
            override fun areItemsTheSame(
                oldItem: CourseDetailItem,
                newItem: CourseDetailItem,
            ): Boolean = when {
                oldItem is CourseDetailItem.SectionHeader &&
                        newItem is CourseDetailItem.SectionHeader ->
                    oldItem.title == newItem.title

                oldItem is CourseDetailItem.Module &&
                        newItem is CourseDetailItem.Module ->
                    oldItem.type.module.id == newItem.type.module.id

                oldItem is CourseDetailItem.Label &&
                        newItem is CourseDetailItem.Label ->
                    oldItem.content == newItem.content

                else -> false
            }

            override fun areContentsTheSame(
                oldItem: CourseDetailItem,
                newItem: CourseDetailItem,
            ): Boolean = oldItem == newItem
        }
    }

    /**
     * Devuelve el tipo de vista de un item.
     *
     * @param position Posición del item.
     * @return `TYPE_HEADER`, `TYPE_MODULE` o `TYPE_LABEL`.
     */
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CourseDetailItem.SectionHeader -> TYPE_HEADER
        is CourseDetailItem.Module -> TYPE_MODULE
        is CourseDetailItem.Label -> TYPE_LABEL
    }

    /**
     * Crea el `ViewHolder` correspondiente.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista.
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false),
            )
            TYPE_LABEL -> LabelViewHolder(
                ItemLabelBinding.inflate(layoutInflater, parent, false),
            )
            else -> ModuleViewHolder(
                ItemModuleBinding.inflate(layoutInflater, parent, false),
            )
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
            is CourseDetailItem.SectionHeader ->
                (holder as HeaderViewHolder).bind(item.title)

            is CourseDetailItem.Module ->
                (holder as ModuleViewHolder).bind(item.type, onModuleClick, onCompletionClick)

            is CourseDetailItem.Label ->
                (holder as LabelViewHolder).bind(item.content)
        }
    }

    /**
     * `ViewHolder` de cabecera de sección.
     *
     * ## Por qué usa `simple_list_item_1`
     *
     * Porque la cabecera solo necesita mostrar un texto, sin
     * estructura. Usar un layout propio sería excesivo.
     *
     * ## Sobre el estilo
     *
     * Se aplica manualmente: negrita, color de acento, padding. El
     * padding se resuelve desde `R.dimen` (dp), no en px
     * hardcodeados, para que se vea bien en cualquier densidad de
     * pantalla.
     */
    inner class HeaderViewHolder(headerView: View) : RecyclerView.ViewHolder(headerView) {

        /** `TextView` de la cabecera. */
        private val titleTextView = headerView as TextView

        /**
         * Binde el título de la sección con estilo de cabecera.
         *
         * @param title Nombre de la sección.
         */
        fun bind(title: String) {
            val resources = titleTextView.resources
            titleTextView.text = title
            titleTextView.textSize = 18f
            titleTextView.setTypeface(null, Typeface.BOLD)
            titleTextView.setTextColor(titleTextView.context.getColor(R.color.color_acento))
            titleTextView.setPadding(
                resources.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_top_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_horizontal_padding),
                resources.getDimensionPixelSize(R.dimen.list_header_bottom_padding),
            )
        }
    }

    /**
     * `ViewHolder` de etiqueta inline.
     *
     * ## Qué es una etiqueta
     *
     * Es un tipo de módulo de Moodle (`modname = "label"`) que
     * contiene texto con formato HTML. No es navegable: solo se
     * muestra dentro del curso.
     *
     * ## Sobre el renderizado
     *
     * El HTML se limpia con `HtmlUtils.stripHtmlPreservingLines`, que
     * preserva los saltos de línea estructurales (`<br>`, `</p>`,
     * etc.). Las etiquetas suelen tener varios párrafos, así que
     * preservar los saltos es importante para que se lean bien.
     */
    inner class LabelViewHolder(
        val binding: ItemLabelBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde el HTML de la etiqueta preservando saltos de línea.
         *
         * @param htmlContent Contenido HTML de la etiqueta.
         */
        fun bind(htmlContent: String) {
            binding.txtLabelContent.text = HtmlUtils.stripHtmlPreservingLines(htmlContent)
        }
    }

    /**
     * `ViewHolder` de módulo navegable.
     *
     * Muestra un icono (según el tipo de módulo), el nombre del
     * módulo, y una descripción opcional.
     */
    inner class ModuleViewHolder(
        val binding: ItemModuleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde nombre, descripción, icono, finalización y click del
         * módulo.
         *
         * El icono se pinta en verde de acento para los módulos que
         * tienen pantalla propia y en gris con forma de candado para
         * los que no se pueden abrir desde la app ([ModuleType.Other]).
         *
         * @param type Tipo de módulo (determina el icono).
         * @param onClick Callback al pulsar; se delega al
         *   `onModuleClick` del adapter.
         * @param onCompletionClick Callback al pulsar el icono de
         *   finalización.
         */
        fun bind(
            type: ModuleType,
            onClick: (ModuleType) -> Unit,
            onCompletionClick: (CourseModule) -> Unit,
        ) = with(binding) {
            val module = type.module
            txtModuleName.text = module.name
            bindDescription(module.description)
            imgModuleIcon.setImageResource(iconFor(type))
            imgModuleIcon.imageTintList = ColorStateList.valueOf(
                imgModuleIcon.context.getColor(
                    if (type is ModuleType.Other) R.color.texto_hint else R.color.color_acento,
                ),
            )
            bindCompletion(module, onCompletionClick)
            root.setOnClickListener { onClick(type) }
        }

        /**
         * Pinta el icono de finalización de la actividad.
         *
         * ## Cuándo se muestra
         *
         * Solo si el profesor activó el seguimiento (`completionMode`
         * distinto de `0`). Si no, el hueco queda oculto y la fila se ve
         * como siempre.
         *
         * ## Cuándo se puede pulsar
         *
         * Solo si la finalización es manual. En las automáticas el
         * icono es informativo: el alumno no puede cambiarlo.
         *
         * @param module Módulo de la fila.
         * @param onCompletionClick Callback al pulsar el icono.
         */
        private fun bindCompletion(
            module: CourseModule,
            onCompletionClick: (CourseModule) -> Unit,
        ) = with(binding.imgModuleCompletion) {
            isVisible = module.tracksCompletion
            if (!module.tracksCompletion) return@with
            setImageResource(
                if (module.isCompleted) R.drawable.ic_check_circle else R.drawable.ic_circle_outline,
            )
            setColorFilter(
                context.getColor(
                    if (module.isCompleted) R.color.color_acento else R.color.texto_hint,
                ),
            )
            isClickable = module.canToggleCompletion
            contentDescription = context.getString(
                when {
                    module.canToggleCompletion && module.isCompleted ->
                        R.string.completion_mark_pending
                    module.canToggleCompletion -> R.string.completion_mark_done
                    module.isCompleted -> R.string.completion_state_done
                    else -> R.string.completion_state_pending
                },
            )
            setOnClickListener(
                if (module.canToggleCompletion) {
                    View.OnClickListener { onCompletionClick(module) }
                } else {
                    null
                },
            )
        }

        /**
         * Muestra u oculta la descripción según si está vacía.
         *
         * ## Sobre el formato
         *
         * La descripción se limpia con `HtmlUtils.stripHtml` (una
         * sola línea), porque va debajo del nombre y no necesita
         * preservar la estructura del HTML.
         *
         * @param description HTML de descripción, o `null`.
         */
        private fun bindDescription(description: String?) {
            if (description.isNullOrBlank()) {
                binding.txtModuleDesc.visibility = View.GONE
                return
            }
            binding.txtModuleDesc.visibility = View.VISIBLE
            binding.txtModuleDesc.text = HtmlUtils.stripHtml(description)
        }

        /**
         * Devuelve el drawable correspondiente al tipo de módulo.
         *
         * Los módulos sin pantalla propia ([ModuleType.Other]) usan un
         * candado para indicar de un vistazo que no se pueden abrir
         * desde la aplicación.
         *
         * ## Por qué el `when` es exhaustivo
         *
         * Porque `ModuleType` es un `sealed interface`. El compilador
         * obliga a contemplar todos los tipos. Si se añade uno nuevo
         * (por ejemplo, `Quiz`), este método dará error de
         * compilación hasta que se actualice.
         *
         * @param type Tipo de módulo.
         * @return ID del drawable del icono.
         */
        private fun iconFor(type: ModuleType): Int = when (type) {
            is ModuleType.Assignment -> R.drawable.ic_courses
            is ModuleType.Forum -> R.drawable.ic_messages
            is ModuleType.Glossary -> R.drawable.ic_glossary
            is ModuleType.Wiki -> R.drawable.ic_wiki
            is ModuleType.Resource -> R.drawable.ic_arrow_download
            is ModuleType.Folder -> R.drawable.ic_folder
            is ModuleType.Url -> R.drawable.ic_web
            is ModuleType.Page -> R.drawable.ic_web
            is ModuleType.Book -> R.drawable.ic_courses
            is ModuleType.Label -> R.drawable.ic_courses
            is ModuleType.Other -> R.drawable.ic_lock
        }
    }
}
