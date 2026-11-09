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
 * Adaptador de la lista de cursos.
 *
 * ## Qué hace
 * Se encarga de dibujar cada curso como una tarjeta. Cada tarjeta
 * incluye el nombre, la imagen (o un patrón generado si no hay
 * imagen), el botón de "Entrar" y la estrella de favorito.
 *
 * @param onClick Acción al pulsar la tarjeta entera.
 * @param onEnterClick Acción al pulsar el botón de "Entrar".
 * @param onFavouriteClick Acción al pulsar la estrella de favorito.
 */
class CourseAdapter(
    private val onClick: (CourseUi) -> Unit,
    private val onEnterClick: (CourseUi) -> Unit,
    private val onFavouriteClick: (CourseUi) -> Unit,
) : ListAdapter<CourseUi, CourseAdapter.VH>(DIFF) {

    /**
     * Guarda los elementos visuales de una tarjeta.
     *
     * Su propósito es mantener las referencias a los textos,
     * imágenes y botones para poder actualizarlos rápidamente.
     */
    inner class VH(
        val binding: ItemCourseBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Rellena una tarjeta con los datos del curso.
         *
         * Su propósito es tomar los datos del curso y ponerlos en
         * las etiquetas y botones correspondientes.
         *
         * @param course El curso a dibujar.
         */
        fun bind(course: CourseUi) = with(binding) {
            txtCourseName?.text = course.name
            bindBanner(course)

            imgStar?.setImageResource(
                if (course.isFavorite) R.drawable.ic_star_filled
                else R.drawable.ic_star_outline,
            )
            imgStar?.setOnClickListener { onFavouriteClick(course) }
            btnEnterCourse?.setOnClickListener { onEnterClick(course) }
            root.setOnClickListener { onClick(course) }
        }

        /**
         * Decide qué tipo de imagen mostrar en la portada.
         *
         * Su propósito es comprobar si el curso tiene una imagen real
         * o si por el contrario hay que generar un patrón de colores.
         *
         * @param course El curso a dibujar.
         */
        private fun bindBanner(course: CourseUi) {
            if (!course.imageUrl.isNullOrEmpty()) bindRealImage(course)
            else bindGeneratedPattern(course)
        }

        /**
         * Carga la imagen real del curso desde internet.
         *
         * Su propósito es descargar la foto de portada y mostrarla
         * de forma que ocupe todo el espacio disponible.
         *
         * @param course El curso con imagen real.
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
         * Genera y muestra un patrón de colores si no hay imagen.
         *
         * Su propósito es crear un fondo visual único basado en el
         * nombre del curso para que la lista no se vea vacía.
         *
         * @param course El curso sin imagen real.
         */
        private fun bindGeneratedPattern(course: CourseUi) = with(binding) {
            val isLandscape = root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            imgCourseBanner?.scaleType = if (isLandscape) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_XY
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
     * Crea la estructura visual de una tarjeta.
     *
     * Su propósito es inflar (construir) el diseño XML de la tarjeta
     * para que luego se puedan rellenar sus datos.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Pide a una tarjeta que se dibuje con los datos concretos.
     *
     * Su propósito es conectar la lista de datos con la lista visual,
     * diciendo "oye, dibuja el curso que está en esta posición".
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CourseUi>() {
            override fun areItemsTheSame(oldItem: CourseUi, newItem: CourseUi) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CourseUi, newItem: CourseUi) = oldItem == newItem
        }
    }
}