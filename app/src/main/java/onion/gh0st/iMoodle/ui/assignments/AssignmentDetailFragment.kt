package onion.gh0st.iMoodle.ui.assignments

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentAssignmentDetailBinding
import onion.gh0st.iMoodle.databinding.ItemFileBinding
import onion.gh0st.iMoodle.databinding.ItemSubmissionCommentBinding
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
import onion.gh0st.iMoodle.domain.model.assignment.SubmissionComment
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.common.FormatUtils
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.files.FileDownloadHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * La pantalla (el "cuerpo") donde se muestra la tarea.
 *
 * Recibe órdenes del ViewModel (el "cerebro") y dibuja los botones,
 * los textos y la lista de archivos. También captura cuando el usuario
 * toca los botones y se lo comunica al cerebro.
 */
@AndroidEntryPoint
class AssignmentDetailFragment : Fragment(R.layout.fragment_assignment_detail) {

    @Inject lateinit var sessionRepo: SessionRepository
    private val viewModel: AssignmentDetailViewModel by viewModels()

    companion object {
        fun newInstance(courseId: Int, assignmentId: Int) = AssignmentDetailFragment().apply {
            arguments = Bundle().apply { putInt("COURSE_ID", courseId); putInt("ASSIGNMENT_ID", assignmentId) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAssignmentDetailBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        setupSubmitButton(binding)
        setupDeleteButton(binding)
        setupPostCommentButton(binding)
        observeUi(binding)
    }

    private fun setupToolbar(binding: FragmentAssignmentDetailBinding) {
        binding.toolbarAssign.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupWindowInsets(binding: FragmentAssignmentDetailBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarAssign) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }
    }

    /**
     * Cuando pulsas "Entregar", si ya estaba entregado te avisa, si no, abre el diálogo.
     */
    private fun setupSubmitButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnSubmitAssignment.setOnClickListener {
            if (viewModel.state.value.assignment?.submissionStatus == "submitted") showResubmitWarning()
            else showSubmissionDialog()
        }
    }

    /**
     * Cuando pulsas "Borrar entrega", te pide confirmación.
     */
    private fun setupDeleteButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnDeleteSubmission.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_submission_title)
                .setMessage(R.string.dialog_delete_submission_message)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteSubmission() }
                .setNegativeButton(R.string.action_cancel, null).show()
        }
    }

    /**
     * Configura el botón de enviar comentario.
     */
    private fun setupPostCommentButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnPostComment.setOnClickListener {
            val text = binding.inpNewComment.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) viewModel.postComment(text)
        }
        binding.inpNewComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePostCommentButtonState(binding) }
            override fun afterTextChanged(s: Editable?) {}
        })
        updatePostCommentButtonState(binding)
    }

    private fun updatePostCommentButtonState(binding: FragmentAssignmentDetailBinding) {
        val state = viewModel.state.value
        val busy = state.isSubmitting || state.isDeleting || state.isPostingComment || state.isDeletingComment
        binding.btnPostComment.isEnabled = !binding.inpNewComment.text.isNullOrBlank() && !busy
        binding.btnPostComment.text = if (state.isPostingComment) getString(R.string.posting_comment) else getString(R.string.action_post_comment)
    }

    private fun showResubmitWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_resubmit_title)
            .setMessage(R.string.dialog_resubmit_message)
            .setPositiveButton(R.string.action_continue) { _, _ -> showSubmissionDialog() }
            .setNegativeButton(R.string.action_cancel, null).show()
    }

    private fun showSubmissionDialog() {
        if (childFragmentManager.findFragmentByTag(AssignmentSubmissionDialog.TAG) == null) {
            AssignmentSubmissionDialog.newInstance().show(childFragmentManager, AssignmentSubmissionDialog.TAG)
        }
    }

    private fun dismissSubmissionDialog() {
        (childFragmentManager.findFragmentByTag(AssignmentSubmissionDialog.TAG) as? AssignmentSubmissionDialog)?.dismissAllowingStateLoss()
    }

    /**
     * Se pone a escuchar al ViewModel (al cerebro).
     */
    private fun observeUi(binding: FragmentAssignmentDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding) }
                launch { observeEvents(binding) }
            }
        }
    }

    /**
     * Pinta los datos cada vez que el cerebro dice que han cambiado.
     */
    private fun observeState(binding: FragmentAssignmentDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val assignment = state.assignment
                    assignment?.let {
                        renderAssignment(binding, it)
                        renderComments(binding, it, state.comments)
                    }
                    val busy = state.isSubmitting || state.isDeleting || state.isPostingComment || state.isDeletingComment

                    // Pintamos los botones de verde o gris según si tenemos permiso.
                    updateButtonAppearance(binding.btnSubmitAssignment, !busy && (assignment?.canSubmit ?: false), isOutlined = false)
                    updateButtonAppearance(binding.btnDeleteSubmission, !busy && (assignment?.canEdit ?: false), isOutlined = true)

                    updatePostCommentButtonState(binding)
                }
            }
        }
    }

    /**
     * Cambia los colores de un botón.
     *
     * Si el botón está habilitado, se pinta de verde.
     * Si está deshabilitado, se pinta de gris translúcido.
     */
    private fun updateButtonAppearance(button: com.google.android.material.button.MaterialButton, enabled: Boolean, isOutlined: Boolean) {
        button.isEnabled = enabled
        val context = requireContext()
        if (enabled) {
            val accent = ContextCompat.getColor(context, R.color.color_acento)
            button.backgroundTintList = ColorStateList.valueOf(if (isOutlined) android.graphics.Color.TRANSPARENT else accent)
            button.setTextColor(if (isOutlined) accent else ContextCompat.getColor(context, R.color.texto_boton))
            button.iconTint = ColorStateList.valueOf(accent)
            if (isOutlined) button.strokeColor = ColorStateList.valueOf(accent)
        } else {
            // Aquí usamos el color que faltaba en colors.xml
            val disabled = ContextCompat.getColor(context, R.color.boton_deshabilitado)
            val textDisabled = ContextCompat.getColor(context, R.color.texto_secundario)
            button.backgroundTintList = ColorStateList.valueOf(disabled)
            button.setTextColor(textDisabled)
            button.iconTint = ColorStateList.valueOf(textDisabled)
            if (isOutlined) button.strokeColor = ColorStateList.valueOf(disabled)
        }
    }

    /**
     * Escucha los eventos (como "éxito al entregar").
     */
    private suspend fun observeEvents(binding: FragmentAssignmentDetailBinding) {
        viewModel.events.collect { event ->
            when (event) {
                is AssignmentDetailUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                AssignmentDetailUiEvent.SubmissionSuccess -> { dismissSubmissionDialog(); showToast(R.string.toast_submission_success); viewModel.loadAssignment() }
                AssignmentDetailUiEvent.DeletionSuccess -> showToast(R.string.toast_deletion_success)
                AssignmentDetailUiEvent.CommentPosted -> binding.inpNewComment.text?.clear()
                AssignmentDetailUiEvent.CommentDeleted -> Unit
            }
        }
    }

    /**
     * Pinta los textos principales y los botones.
     */
    private fun renderAssignment(binding: FragmentAssignmentDetailBinding, assignment: AssignmentDetail) {
        binding.txtAssignName.text = assignment.name
        binding.txtAssignDescription.text = if (assignment.description.isBlank()) getString(R.string.label_no_description) else HtmlUtils.stripHtml(assignment.description)
        binding.txtAssignDates.text = if (assignment.dueDate != null) {
            getString(R.string.label_due_date, SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(assignment.dueDate * 1000)))
        } else getString(R.string.label_no_due_date)

        binding.txtSubmissionStatus.text = when {
            assignment.statusUnknown -> getString(R.string.status_unknown)
            assignment.submissionStatus == "submitted" -> getString(R.string.status_submitted)
            assignment.submissionStatus == "draft" -> getString(R.string.status_draft)
            else -> getString(R.string.status_not_submitted)
        }

        if (!assignment.submissionGraded) binding.txtSubmissionGrade.isVisible = false
        else {
            binding.txtSubmissionGrade.isVisible = true
            val grade = assignment.gradeDisplay?.let { HtmlUtils.stripHtml(it) } ?: (assignment.submissionGrade?.let { "${FormatUtils.formatGrade(it)} / ${FormatUtils.formatGrade(assignment.maxGrade)}" } ?: "")
            binding.txtSubmissionGrade.text = getString(R.string.label_grade, grade)
        }

        val isSubmitted = assignment.submissionStatus == "submitted"
        binding.btnSubmitAssignment.isVisible = !isSubmitted
        binding.btnDeleteSubmission.isVisible = isSubmitted

        binding.containerSubmissionAttachments.removeAllViews()
        binding.layoutSubmissionAttachments.isVisible = assignment.submissionFiles.isNotEmpty()
        assignment.submissionFiles.forEach { addSubmissionAttachmentView(binding, it) }

        binding.containerAttachments.removeAllViews()
        binding.layoutAttachments.isVisible = assignment.files.isNotEmpty()
        assignment.files.forEach { addAttachmentView(binding, it) }
    }

    /**
     * Pinta la lista de mensajes y convierte las URLs en enlaces clicables.
     */
    private fun renderComments(binding: FragmentAssignmentDetailBinding, assignment: AssignmentDetail, comments: List<SubmissionComment>) {
        val canShow = assignment.submissionId != null && assignment.submissionId > 0 && "comments" in assignment.enabledSubmissionPlugins
        binding.layoutSubmissionComments.isVisible = canShow
        if (!canShow) return

        binding.containerSubmissionComments.removeAllViews()
        binding.txtNoComments.isVisible = comments.isEmpty()
        comments.forEach { comment ->
            val row = ItemSubmissionCommentBinding.inflate(LayoutInflater.from(requireContext()), binding.containerSubmissionComments, false)
            row.txtCommentAuthor.text = comment.author
            row.txtCommentDate.text = if (comment.createdAt > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(comment.createdAt * 1000)) else getString(R.string.label_unknown_date)

            // Renderizamos el HTML y hacemos clicables los enlaces.
            row.txtCommentContent.text = HtmlUtils.renderHtml(comment.content)
            row.txtCommentContent.movementMethod = LinkMovementMethod.getInstance()
            LinkifyCompat.addLinks(row.txtCommentContent, Linkify.WEB_URLS)

            // Solo mostramos la papelera si el comentario es tuyo.
            val isOwn = comment.author == sessionRepo.session.value?.user?.fullname
            row.btnDeleteComment.isVisible = isOwn
            row.btnDeleteComment.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_delete_comment_title)
                    .setMessage(R.string.dialog_delete_comment_message)
                    .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteComment(comment.id) }
                    .setNegativeButton(R.string.action_cancel, null).show()
            }
            binding.containerSubmissionComments.addView(row.root)
        }
    }

    private fun addSubmissionAttachmentView(binding: FragmentAssignmentDetailBinding, file: CourseFile) {
        val item = ItemFileBinding.inflate(LayoutInflater.from(requireContext()), binding.containerSubmissionAttachments, false)
        item.txtFileName.text = file.fileName; item.txtFileSize.text = FormatUtils.formatFileSize(file.fileSize)
        item.imgFileIcon.setImageResource(R.drawable.ic_arrow_download)
        item.root.setOnClickListener { confirmDownload(file) }
        binding.containerSubmissionAttachments.addView(item.root)
    }

    private fun addAttachmentView(binding: FragmentAssignmentDetailBinding, file: CourseFile) {
        val item = ItemFileBinding.inflate(LayoutInflater.from(requireContext()), binding.containerAttachments, false)
        item.txtFileName.text = file.fileName; item.txtFileSize.text = FormatUtils.formatFileSize(file.fileSize)
        item.imgFileIcon.setImageResource(R.drawable.ic_arrow_download)
        item.root.setOnClickListener { confirmDownload(file) }
        binding.containerAttachments.addView(item.root)
    }

    private fun confirmDownload(file: CourseFile) {
        if (sessionRepo.session.value == null) return
        FileDownloadHelper.showConfirmationDialog(requireContext(), file) { FileDownloadHelper.download(requireContext(), file) }
    }

    private fun showToast(@StringRes resId: Int, arg: String? = null) {
        val msg = if (arg != null) getString(resId, arg) else getString(resId)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}