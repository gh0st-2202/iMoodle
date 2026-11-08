package onion.gh0st.iMoodle.ui.courses.list

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.decode.SvgDecoder
import coil.load
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemCourseBinding
import onion.gh0st.iMoodle.ui.common.MoodleGeoPattern

/**
 * Adapter de la lista de cursos.
 *
 * ## Qué hace
 *
 * Renderiza una tarjeta por curso. Cada tarjeta tiene:
 *
 * - Una imagen de portada (o un patrón SVG generado si no hay).
 * - El nombre del curso.
 * - Un icono de favorito (placeholder).
 * - Un botón "Entrar".
 *
 * ## Sobre la imagen de portada
 *
 * Hay dos casos:
 *
 * 1. **El curso tiene imagen real** (`imageUrl != null`): se carga
 *    con Coil, usando `CENTER_CROP`.
 * 2. **El curso no tiene imagen**: se genera un patrón SVG con
 *    `MoodleGeoPattern` (determinista a partir del nombre y el ID
 *    del curso) y se carga con Coil usando `SvgDecoder`.
 *
 * ## Sobre el `scaleType` del banner
 *
 * Cambia según la orientación:
 *
 * - **Portrait**: `FIT_XY` para el patrón SVG, porque el patrón está
 *   pensado para rellenar exactamente el espacio disponible.
 * - **Landscape**: `CENTER_CROP` para el patrón SVG, porque la celda
 *   es más ancha que alta y `FIT_XY` deformaría el patrón.
 *
 * Para la imagen real, siempre `CENTER_CROP` (es la forma estándar
 * de mostrar imágenes de portada sin deformarlas).
 *
 * @param onClick Callback al pulsar la tarjeta entera.
 * @param onEnterClick Callback al pulsar el botón "Entrar" (navega
 *   al detalle del curso).
 * @param onLongClick Callback al mantener pulsado (reservado para
 *   "favorito", pendiente de implementar).
 */
class CourseAdapter(
    private val onClick: (CourseUi) -> Unit,
    private val onEnterClick: (CourseUi) -> Unit,
    private val onLongClick: (CourseUi) -> Unit,
) : ListAdapter<CourseUi, CourseAdapter.VH>(DIFF) {

    /**
     * `ViewHolder` de una tarjeta de curso.
     */
    inner class VH(
        val binding: ItemCourseBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde una tarjeta completa.
         *
         * @param course Curso a mostrar.
         */
        fun bind(course: CourseUi) = with(binding) {
            txtCourseName?.text = course.name
            bindBanner(course)
            imgStar?.setImageResource(
                if (course.isFavorite) R.drawable.ic_star_filled
                else R.drawable.ic_star_outline,
            )
            btnEnterCourse?.setOnClickListener { onEnterClick(course) }
            root.setOnClickListener { onClick(course) }
            root.setOnLongClickListener { onLongClick(course); true }
        }

        /**
         * Decide si pinta la imagen real o el patrón generado.
         *
         * @param course Curso a mostrar.
         */
        private fun bindBanner(course: CourseUi) {
            if (!course.imageUrl.isNullOrEmpty()) {
                bindRealImage(course)
            } else {
                bindGeneratedPattern(course)
            }
        }

        /**
         * Carga la portada real del curso con Coil.
         *
         * ## Caché
         *
         * Se usan claves de caché basadas en el ID del curso
         * (`course_img_<id>`), para que se reutilicen entre visitas.
         *
         * ## Tamaño
         *
         * Se pre-decodifica al tamaño de la celda (`1200x800`) para
         * evitar bitmaps grandes en memoria.
         *
         * @param course Curso con `imageUrl` no vacía.
         */
        private fun bindRealImage(course: CourseUi) = with(binding) {
            imgCourseBanner?.scaleType = ImageView.ScaleType.CENTER_CROP
            imgCourseBanner?.load(course.imageUrl) {
                placeholder(R.color.verde_junta_suave)
                error(R.color.verde_junta_suave)
                crossfade(true)
                memoryCacheKey("course_img_${course.id}")
                diskCacheKey("course_img_${course.id}")
                size(1200, 800)
            }
        }

        /**
         * Genera y carga el patrón SVG en memoria.
         *
         * ## Qué hace
         *
         * 1. Obtiene el SVG del curso con `MoodleGeoPattern.svgBytes`.
         * 2. Lo carga con Coil usando `SvgDecoder.Factory()` para que
         *    se decodifique como SVG.
         *
         * ## Caché
         *
         * Se usan claves de caché basadas en el ID del curso
         * (`geo_<id>`), para que se reutilicen entre visitas. El
         * patrón es determinista, así que no cambia.
         *
         * @param course Curso sin `imageUrl`.
         */
        private fun bindGeneratedPattern(course: CourseUi) = with(binding) {
            val isLandscape = root.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            imgCourseBanner?.scaleType = if (isLandscape) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_XY
            }
            val svgBytes = MoodleGeoPattern.svgBytes(course.name, course.id)
            imgCourseBanner?.load(svgBytes) {
                decoderFactory(SvgDecoder.Factory())
                size(1000, 500)
                memoryCacheKey("geo_${course.id}")
                diskCacheKey("geo_${course.id}")
                placeholder(R.color.verde_junta_suave)
                crossfade(true)
            }
        }
    }

    /**
     * Crea el `ViewHolder` de una tarjeta.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde el curso de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` que decide cuándo dos cursos son iguales.
         *
         * ## Comportamiento
         *
         * - **Items iguales**: mismo `id`.
         * - **Contenido igual**: todos los campos son iguales (`==`).
         *
         * Así, si un curso cambia (por ejemplo, el progreso), se
         * redibuja. Si no, no.
         */
        private val DIFF = object : DiffUtil.ItemCallback<CourseUi>() {
            override fun areItemsTheSame(
                oldItem: CourseUi,
                newItem: CourseUi,
            ) = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: CourseUi,
                newItem: CourseUi,
            ) = oldItem == newItem
        }
    }
}