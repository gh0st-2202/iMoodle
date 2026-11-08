package onion.gh0st.iMoodle.ui.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.ItemFileBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.ui.common.FormatUtils

/**
 * Adapter de la lista de archivos descargables.
 *
 * ## Qué muestra
 *
 * Una fila por archivo, con:
 *
 * - Nombre del archivo.
 * - Tamaño formateado (`"1,5 MB"`).
 * - Icono de descarga.
 *
 * Al pulsar una fila, se delega en el callback `onFileClick`, que
 * normalmente muestra un diálogo de confirmación antes de lanzar la
 * descarga.
 *
 * ## Sobre la identidad de un archivo
 *
 * La identidad de un archivo es su `fileUrl`, no su nombre. Dos
 * archivos pueden tener el mismo nombre en módulos distintos y
 * deben ser filas distintas. Por eso el `DiffUtil` compara por
 * `fileUrl`.
 *
 * @param onFileClick Callback al pulsar un archivo.
 */
class CourseFilesAdapter(
    private val onFileClick: (CourseFile) -> Unit,
) : ListAdapter<CourseFile, CourseFilesAdapter.VH>(DIFF) {

    /**
     * `ViewHolder` de un archivo.
     */
    inner class VH(
        val binding: ItemFileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binde nombre, tamaño formateado, icono de descarga y click.
         *
         * @param file Archivo a mostrar.
         */
        fun bind(file: CourseFile) = with(binding) {
            txtFileName.text = file.fileName
            txtFileSize.text = FormatUtils.formatFileSize(file.fileSize)
            imgFileIcon.setImageResource(R.drawable.ic_arrow_download)
            root.setOnClickListener { onFileClick(file) }
        }
    }

    /**
     * Crea el `ViewHolder` de una fila de archivo.
     *
     * @param parent Contenedor padre.
     * @param viewType Tipo de vista (solo hay uno).
     * @return El `ViewHolder` listo.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /**
     * Binde el archivo de la posición dada.
     *
     * @param holder `ViewHolder` a bindear.
     * @param position Posición del item.
     */
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        /**
         * `DiffUtil` que decide cuándo dos archivos son iguales.
         *
         * ## Comportamiento
         *
         * - **Items iguales**: mismo `fileUrl` (identidad real, no
         *   por nombre).
         * - **Contenido igual**: todos los campos son iguales (`==`).
         */
        private val DIFF = object : DiffUtil.ItemCallback<CourseFile>() {
            override fun areItemsTheSame(
                oldItem: CourseFile,
                newItem: CourseFile,
            ) = oldItem.fileUrl == newItem.fileUrl

            override fun areContentsTheSame(
                oldItem: CourseFile,
                newItem: CourseFile,
            ) = oldItem == newItem
        }
    }
}