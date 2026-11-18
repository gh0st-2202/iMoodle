package onion.gh0st.iMoodle.ui.assignments

import android.app.Dialog
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.displayName
import onion.gh0st.iMoodle.core.sizeInBytes
import onion.gh0st.iMoodle.databinding.DialogAssignmentSubmissionBinding
import onion.gh0st.iMoodle.databinding.DialogSubmissionFileItemBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.ui.common.FormatUtils
import onion.gh0st.iMoodle.ui.common.showToast

/**
 * Diálogo que aparece al pulsar "Entregar" en el detalle de una tarea.
 *
 * ## Qué permite
 *
 * - Escribir un texto en línea (solo si la tarea admite entrega de texto).
 * - Añadir archivos del teléfono con el selector de documentos de Android.
 * - Quitar archivos nuevos ya elegidos o archivos que se habían entregado
 *   antes (los que se mantienen se vuelven a subir con la nueva entrega).
 *
 * ## Quién hace la entrega
 *
 * El diálogo no entrega nada por sí mismo: reúne lo que el usuario ha
 * elegido y se lo pasa a [AssignmentDetailViewModel], que comparte con el
 * fragmento de detalle que lo abrió (`requireParentFragment()`). Cuando la
 * entrega termina bien, es el fragmento quien lo cierra.
 *
 * ## Qué se conserva al rotar la pantalla
 *
 * La lista de archivos nuevos elegidos y los archivos antiguos marcados
 * para quitar se guardan en [onSaveInstanceState] y se restauran en
 * [onCreateDialog]. Antes se perdían al girar el móvil, dejando una
 * lista vacía sin que el usuario lo supiera. (El texto escrito lo
 * conserva Android por sí solo.)
 */
class AssignmentSubmissionDialog : DialogFragment() {

    private lateinit var binding: DialogAssignmentSubmissionBinding

    private val viewModel: AssignmentDetailViewModel by viewModels({ requireParentFragment() })

    /** Archivos nuevos elegidos en el teléfono, aún sin subir. */
    private val selectedFiles = mutableListOf<SelectedFile>()

    /** URLs de archivos ya entregados que el usuario ha decidido quitar. */
    private val deletedFileUrls = mutableListOf<String>()

    /** Lanza el selector de archivos del sistema y registra el elegido. */
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val contentResolver = requireContext().contentResolver
            selectedFiles.add(
                SelectedFile(
                    uriString = uri.toString(),
                    name = uri.displayName(contentResolver),
                    sizeBytes = uri.sizeInBytes(contentResolver) ?: 0L,
                ),
            )
            renderNewFiles()
        }
    }

    /**
     * Construye el diálogo, restaurando antes el estado guardado si lo hay.
     *
     * El botón "Entregar" se reprograma para que **no** cierre el diálogo
     * automáticamente: se cierra solo cuando la entrega tiene éxito.
     *
     * @param savedInstanceState Estado guardado tras una rotación, o `null`.
     * @return El diálogo listo para mostrar.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        restoreSelection(savedInstanceState)

        binding = DialogAssignmentSubmissionBinding.inflate(layoutInflater)
        binding.btnAddFile.setOnClickListener { pickFileLauncher.launch(ANY_FILE_MIME_TYPE) }

        configureCommentVisibility()
        renderExistingFiles()
        renderNewFiles()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_submission_title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_submit, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        }
        observeSubmittingState(dialog)
        return dialog
    }

    /**
     * Guarda la selección de archivos para poder restaurarla tras una
     * rotación.
     *
     * @param outState Contenedor del estado a guardar.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_NEW_URIS, ArrayList(selectedFiles.map { file -> file.uriString }))
        outState.putStringArrayList(STATE_NEW_NAMES, ArrayList(selectedFiles.map { file -> file.name }))
        outState.putLongArray(STATE_NEW_SIZES, selectedFiles.map { file -> file.sizeBytes }.toLongArray())
        outState.putStringArrayList(STATE_DELETED_URLS, ArrayList(deletedFileUrls))
    }

    /**
     * Recupera del estado guardado los archivos nuevos y los archivos
     * antiguos marcados para quitar.
     *
     * @param savedInstanceState Estado guardado, o `null` si es la primera vez.
     */
    private fun restoreSelection(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val uris = savedInstanceState.getStringArrayList(STATE_NEW_URIS).orEmpty()
        val names = savedInstanceState.getStringArrayList(STATE_NEW_NAMES).orEmpty()
        val sizes = savedInstanceState.getLongArray(STATE_NEW_SIZES) ?: LongArray(0)

        selectedFiles.clear()
        uris.forEachIndexed { index, uriString ->
            selectedFiles.add(
                SelectedFile(
                    uriString = uriString,
                    name = names.getOrElse(index) { uriString.toUri().lastPathSegment.orEmpty() },
                    sizeBytes = sizes.getOrElse(index) { 0L },
                ),
            )
        }

        deletedFileUrls.clear()
        deletedFileUrls.addAll(savedInstanceState.getStringArrayList(STATE_DELETED_URLS).orEmpty())
    }

    /** Muestra el cuadro de texto solo si la tarea admite entrega de texto en línea. */
    private fun configureCommentVisibility() {
        val enabledPlugins = viewModel.state.value.assignment?.enabledSubmissionPlugins.orEmpty()
        binding.layoutComment.isVisible = ONLINE_TEXT_PLUGIN in enabledPlugins
    }

    /**
     * Bloquea los controles y cambia el texto del botón mientras la
     * entrega está en curso.
     *
     * @param dialog Diálogo cuyo botón principal se actualiza.
     */
    private fun observeSubmittingState(dialog: AlertDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val isBusy = state.isSubmitting
                    binding.inpComment.isEnabled = !isBusy
                    binding.btnAddFile.isEnabled = !isBusy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        isEnabled = !isBusy
                        text = getString(if (isBusy) R.string.submitting_assignment else R.string.action_submit)
                    }
                    dialog.setCancelable(!isBusy)
                }
            }
        }
    }

    /**
     * Comprueba que haya algo que entregar y se lo pasa al ViewModel.
     *
     * Hay algo que entregar si existe texto, algún archivo nuevo o algún
     * archivo antiguo que se conserve.
     */
    private fun submit() {
        val onlineText = if (binding.layoutComment.isVisible) {
            binding.inpComment.text?.toString()?.trim().orEmpty()
        } else {
            ""
        }
        val keptFiles = keptExistingFiles()

        if (onlineText.isEmpty() && selectedFiles.isEmpty() && keptFiles.isEmpty()) {
            showToast(R.string.toast_submission_empty)
            return
        }

        viewModel.submitAssignment(
            onlineText = onlineText,
            fileUris = selectedFiles.map { file -> file.uriString },
            existingFileUrls = keptFiles.map { file -> file.fileUrl },
        )
    }

    /**
     * Devuelve los archivos ya entregados que el usuario no ha quitado.
     *
     * @return Los archivos antiguos que se conservarán en la nueva entrega.
     */
    private fun keptExistingFiles(): List<CourseFile> =
        viewModel.state.value.assignment?.submissionFiles
            ?.filter { file -> file.fileUrl !in deletedFileUrls }
            .orEmpty()

    /** Pinta la lista de archivos ya entregados que se conservan, con un botón para quitar cada uno. */
    private fun renderExistingFiles() {
        val keptFiles = keptExistingFiles()
        binding.containerExistingFiles.removeAllViews()
        binding.layoutExistingFiles.isVisible = keptFiles.isNotEmpty()
        keptFiles.forEach { file ->
            val row = DialogSubmissionFileItemBinding.inflate(
                layoutInflater,
                binding.containerExistingFiles,
                false,
            )
            row.txtSelectedFileName.text = file.fileName
            row.btnRemoveSelectedFile.setOnClickListener {
                deletedFileUrls.add(file.fileUrl)
                renderExistingFiles()
            }
            binding.containerExistingFiles.addView(row.root)
        }
    }

    /** Pinta la lista de archivos nuevos elegidos, con su tamaño y un botón para quitar cada uno. */
    private fun renderNewFiles() {
        binding.containerSelectedFiles.removeAllViews()
        binding.txtNoFiles.isVisible = selectedFiles.isEmpty()
        selectedFiles.forEach { file ->
            val row = DialogSubmissionFileItemBinding.inflate(
                layoutInflater,
                binding.containerSelectedFiles,
                false,
            )
            row.txtSelectedFileName.text = if (file.sizeBytes > 0) {
                "${file.name} (${FormatUtils.formatFileSize(file.sizeBytes)})"
            } else {
                file.name
            }
            row.btnRemoveSelectedFile.setOnClickListener {
                selectedFiles.remove(file)
                renderNewFiles()
            }
            binding.containerSelectedFiles.addView(row.root)
        }
    }

    /**
     * Archivo nuevo elegido en el teléfono.
     *
     * @property uriString URI del archivo, como texto.
     * @property name Nombre visible del archivo.
     * @property sizeBytes Tamaño en bytes (`0` si no se conoce).
     */
    private data class SelectedFile(val uriString: String, val name: String, val sizeBytes: Long)

    companion object {
        /** Etiqueta con la que se registra el diálogo en el `FragmentManager`. */
        const val TAG = "AssignmentSubmissionDialog"

        private const val ANY_FILE_MIME_TYPE = "*/*"
        private const val ONLINE_TEXT_PLUGIN = "onlinetext"

        private const val STATE_NEW_URIS = "STATE_NEW_URIS"
        private const val STATE_NEW_NAMES = "STATE_NEW_NAMES"
        private const val STATE_NEW_SIZES = "STATE_NEW_SIZES"
        private const val STATE_DELETED_URLS = "STATE_DELETED_URLS"

        /**
         * Crea el diálogo.
         *
         * @return Una nueva instancia del diálogo de entrega.
         */
        fun newInstance() = AssignmentSubmissionDialog()
    }
}
