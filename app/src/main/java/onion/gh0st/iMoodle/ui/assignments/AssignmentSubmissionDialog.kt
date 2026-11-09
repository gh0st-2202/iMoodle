package onion.gh0st.iMoodle.ui.assignments

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.DialogAssignmentSubmissionBinding
import onion.gh0st.iMoodle.databinding.DialogSubmissionFileItemBinding
import onion.gh0st.iMoodle.ui.common.FormatUtils

/**
 * La ventana (diálogo) que aparece al pulsar "Entregar Tarea".
 *
 * Aquí es donde seleccionas los archivos y escribes el comentario.
 * Tiene un botón para abrir la galería de tu teléfono y elegir archivos.
 */
class AssignmentSubmissionDialog : DialogFragment() {

    private lateinit var binding: DialogAssignmentSubmissionBinding
    // Compartimos el mismo "cerebro" (ViewModel) que la pantalla de detrás.
    private val viewModel: AssignmentDetailViewModel by viewModels({ requireParentFragment() })

    private val selectedFiles = mutableListOf<SelectedFile>()
    private val deletedFileUrls = mutableListOf<String>()

    // El "lanzador" que abre la pantalla de archivos de Android.
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFiles.add(SelectedFile(it.toString(), resolveFileName(it), resolveFileSize(it)))
            renderNewFiles()
        }
    }

    /**
     * Construye la ventana cuando se va a mostrar.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAssignmentSubmissionBinding.inflate(LayoutInflater.from(requireContext()))
        binding.btnAddFile.setOnClickListener { pickFileLauncher.launch("*/*") } // Permite cualquier archivo.

        configureCommentVisibility()
        renderExistingFiles()
        renderNewFiles()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_submission_title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_submit, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        // Sobrescribimos el botón positivo para que no se cierre por defecto.
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() } }
        observeSubmittingState(dialog)
        return dialog
    }

    /**
     * Muestra el cuadro de texto solo si la tarea lo permite.
     */
    private fun configureCommentVisibility() {
        val enabled = viewModel.state.value.assignment?.enabledSubmissionPlugins ?: emptySet()
        binding.layoutComment.isVisible = "onlinetext" in enabled
    }

    /**
     * Bloquea los botones mientras se está subiendo algo.
     */
    private fun observeSubmittingState(dialog: AlertDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val busy = state.isSubmitting
                    binding.inpComment.isEnabled = !busy
                    binding.btnAddFile.isEnabled = !busy
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        isEnabled = !busy
                        text = if (busy) getString(R.string.submitting_assignment) else getString(R.string.action_submit)
                    }
                    dialog.setCancelable(!busy)
                }
            }
        }
    }

    /**
     * Comprueba que haya algo que entregar y llama al cerebro.
     */
    private fun submit() {
        val text = if (binding.layoutComment.isVisible) binding.inpComment.text?.toString()?.trim() ?: "" else ""
        val existing = viewModel.state.value.assignment?.submissionFiles?.filter { it.fileUrl !in deletedFileUrls } ?: emptyList()

        if (text.isEmpty() && selectedFiles.isEmpty() && existing.isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_submission_empty, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.submitAssignment(text, selectedFiles.map { it.uriString }, existing.map { it.fileUrl })
    }

    /**
     * Pinta en la lista los archivos que ya estaban entregados antes.
     */
    private fun renderExistingFiles() {
        val existing = viewModel.state.value.assignment?.submissionFiles?.filter { it.fileUrl !in deletedFileUrls } ?: emptyList()
        binding.containerExistingFiles.removeAllViews()
        binding.layoutExistingFiles.isVisible = existing.isNotEmpty()
        existing.forEach { file ->
            val row = DialogSubmissionFileItemBinding.inflate(LayoutInflater.from(requireContext()), binding.containerExistingFiles, false)
            row.txtSelectedFileName.text = file.fileName
            row.btnRemoveSelectedFile.setOnClickListener { deletedFileUrls.add(file.fileUrl); renderExistingFiles() }
            binding.containerExistingFiles.addView(row.root)
        }
    }

    /**
     * Pinta en la lista los archivos nuevos que has elegido.
     */
    private fun renderNewFiles() {
        binding.containerSelectedFiles.removeAllViews()
        binding.txtNoFiles.isVisible = selectedFiles.isEmpty()
        selectedFiles.forEach { file ->
            val row = DialogSubmissionFileItemBinding.inflate(LayoutInflater.from(requireContext()), binding.containerSelectedFiles, false)
            row.txtSelectedFileName.text = if (file.sizeBytes > 0) "${file.name} (${FormatUtils.formatFileSize(file.sizeBytes)})" else file.name
            row.btnRemoveSelectedFile.setOnClickListener { selectedFiles.remove(file); renderNewFiles() }
            binding.containerSelectedFiles.addView(row.root)
        }
    }

    private fun resolveFileName(uri: Uri): String = requireContext().contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) else "archivo"
    } ?: "archivo"

    private fun resolveFileSize(uri: Uri): Long = try {
        requireContext().contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    } catch (e: Exception) { 0L }

    private data class SelectedFile(val uriString: String, val name: String, val sizeBytes: Long)

    companion object {
        const val TAG = "AssignmentSubmissionDialog"
        fun newInstance() = AssignmentSubmissionDialog()
    }
}