package onion.gh0st.iMoodle.ui.assignments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.util.LinkifyCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
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
import onion.gh0st.iMoodle.ui.common.FormatUtils
import onion.gh0st.iMoodle.ui.common.FragmentArgs
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import onion.gh0st.iMoodle.ui.common.applyImeBottomInset
import onion.gh0st.iMoodle.ui.common.applySystemBarsInsets
import onion.gh0st.iMoodle.ui.common.resolveColorAttr
import onion.gh0st.iMoodle.ui.common.showToast
import onion.gh0st.iMoodle.ui.files.FileDownloadDialog

/**
 * Pantalla de detalle de una tarea: enunciado, fechas, estado, nota,
 * archivos, entrega y comentarios.
 *
 * ## Qué muestra
 *
 * - Nombre, descripción con su formato (enlaces clicables), fecha
 *   límite y estado de la entrega (entregada, borrador, sin entregar o
 *   desconocido).
 * - La nota, si el profesor ya ha calificado.
 * - Los archivos adjuntos a la tarea y los que el alumno ha entregado
 *   (con confirmación antes de descargar).
 * - Los comentarios de la entrega, si la tarea tiene activado ese plugin.
 *
 * ## Qué permite hacer
 *
 * - **Entregar** (o volver a entregar, tras una confirmación) mediante
 *   [AssignmentSubmissionDialog].
 * - **Borrar la entrega**, tras una confirmación.
 * - **Escribir y borrar comentarios** (solo los propios).
 *
 * Los botones se pintan en acento o en gris según Moodle permita la
 * acción y no haya otra operación en curso ([updateButtonAppearance]).
 *
 * ## Argumentos
 *
 * Se crea con [newInstance]: identificador del curso y de la tarea.
 * Toda la lógica vive en [AssignmentDetailViewModel]; este fragmento solo
 * pinta el estado y reenvía las acciones.
 */
@AndroidEntryPoint
class AssignmentDetailFragment : Fragment(R.layout.fragment_assignment_detail) {

    private val viewModel: AssignmentDetailViewModel by viewModels()

    companion object {
        /** Nombre del plugin de entrega que habilita los comentarios. */
        private const val COMMENTS_PLUGIN = "comments"

        /**
         * Separación (en dp) que se deja entre el botón de publicar y el
         * borde superior del teclado al desplazar el formulario.
         */
        private const val COMMENT_SCROLL_MARGIN_DP = 8f

        /**
         * Crea el fragmento para una tarea concreta.
         *
         * @param courseId Identificador del curso.
         * @param assignmentId Identificador de la tarea (instancia).
         * @return El fragmento con sus argumentos ya configurados.
         */
        fun newInstance(courseId: Int, assignmentId: Int) = AssignmentDetailFragment().apply {
            arguments = Bundle().apply {
                putInt(FragmentArgs.COURSE_ID, courseId)
                putInt(FragmentArgs.ASSIGNMENT_ID, assignmentId)
            }
        }
    }

    /**
     * Prepara la pantalla y empieza a observar el estado.
     *
     * @param view Vista raíz ya inflada.
     * @param savedInstanceState Estado guardado, o `null`.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAssignmentDetailBinding.bind(view)
        binding.toolbarAssign.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.appBarAssign.applySystemBarsInsets()
        setupSubmitButton(binding)
        setupDeleteButton(binding)
        setupPostCommentButton(binding)
        setupKeyboardInsets(binding)
        observeUi(binding)
    }

    /**
     * Hace sitio para el teclado y desplaza el formulario de comentarios
     * hasta la zona visible al escribir.
     *
     * ## Por qué hace falta
     *
     * La pantalla dibuja de borde a borde, así que el sistema no
     * redimensiona la ventana al abrir el teclado. Sin reservar el inset
     * del IME, el teclado tapaba el campo de comentario y no se veía lo
     * que se escribía.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupKeyboardInsets(binding: FragmentAssignmentDetailBinding) {
        binding.scrollAssign.applyImeBottomInset { keyboardVisible ->
            if (keyboardVisible) scrollToCommentInput(binding)
        }
        binding.inpNewComment.setOnClickListener { scrollToCommentInput(binding) }
    }

    /**
     * Desplaza el scroll **lo mínimo** necesario para dejar el botón de
     * publicar (y, con él, el campo de comentario) por encima del
     * teclado.
     *
     * ## Por qué no basta `fullScroll`
     *
     * `fullScroll(View.FOCUS_DOWN)` baja hasta el final absoluto del
     * contenido. Como el scroll y el contenido tienen relleno inferior
     * (para no quedar pegados al borde), eso dejaba a la vista una franja
     * vacía del color del fondo justo encima del teclado. Calculando el
     * hueco real y desplazando solo esa distancia no aparece la franja.
     *
     * Se hace en un `post` para esperar a que el relleno del teclado se
     * haya aplicado: hasta entonces el área visible todavía no es la
     * definitiva.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun scrollToCommentInput(binding: FragmentAssignmentDetailBinding) {
        if (!binding.layoutSubmissionComments.isVisible) return
        val scroll = binding.scrollAssign
        scroll.post {
            val margin = (COMMENT_SCROLL_MARGIN_DP * scroll.resources.displayMetrics.density).toInt()
            val scrollLocation = IntArray(2).also { scroll.getLocationInWindow(it) }
            val buttonLocation = IntArray(2).also { binding.btnPostComment.getLocationInWindow(it) }
            val visibleBottom = scrollLocation[1] + scroll.height - scroll.paddingBottom
            val buttonBottom = buttonLocation[1] + binding.btnPostComment.height + margin
            val pendingScroll = buttonBottom - visibleBottom
            if (pendingScroll > 0) scroll.smoothScrollBy(0, pendingScroll)
        }
    }

    /**
     * Configura el botón de entregar: si ya hay una entrega enviada pide
     * antes confirmación (la nueva sustituye a la anterior).
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupSubmitButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnSubmitAssignment.setOnClickListener {
            if (viewModel.state.value.assignment?.isSubmitted == true) {
                showResubmitWarning()
            } else {
                showSubmissionDialog()
            }
        }
    }

    /**
     * Configura el botón de borrar la entrega, con confirmación previa.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupDeleteButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnDeleteSubmission.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_submission_title)
                .setMessage(R.string.dialog_delete_submission_message)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteSubmission() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    /**
     * Configura el envío de comentarios: el botón publica el texto y
     * se habilita solo cuando hay texto y no hay otra operación en curso.
     *
     * @param binding Vistas de la pantalla.
     */
    private fun setupPostCommentButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnPostComment.setOnClickListener {
            val commentText = binding.inpNewComment.text?.toString()?.trim().orEmpty()
            if (commentText.isNotBlank()) viewModel.postComment(commentText)
        }
        binding.inpNewComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePostCommentButtonState(binding)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        updatePostCommentButtonState(binding)
    }

    /**
     * Habilita o deshabilita el botón de comentar y actualiza su texto
     * ("Enviar" / "Enviando…").
     *
     * @param binding Vistas de la pantalla.
     */
    private fun updatePostCommentButtonState(binding: FragmentAssignmentDetailBinding) {
        val state = viewModel.state.value
        binding.btnPostComment.isEnabled = !binding.inpNewComment.text.isNullOrBlank() && !state.isBusy()
        binding.btnPostComment.text = getString(
            if (state.isPostingComment) R.string.posting_comment else R.string.action_post_comment,
        )
    }

    /** Pide confirmación antes de volver a entregar, porque sustituye la entrega anterior. */
    private fun showResubmitWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_resubmit_title)
            .setMessage(R.string.dialog_resubmit_message)
            .setPositiveButton(R.string.action_continue) { _, _ -> showSubmissionDialog() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Muestra el diálogo de entrega, evitando abrir dos a la vez. */
    private fun showSubmissionDialog() {
        if (childFragmentManager.findFragmentByTag(AssignmentSubmissionDialog.TAG) == null) {
            AssignmentSubmissionDialog.newInstance().show(childFragmentManager, AssignmentSubmissionDialog.TAG)
        }
    }

    /** Cierra el diálogo de entrega, si está abierto. */
    private fun dismissSubmissionDialog() {
        val dialog = childFragmentManager.findFragmentByTag(AssignmentSubmissionDialog.TAG)
        (dialog as? AssignmentSubmissionDialog)?.dismissAllowingStateLoss()
    }

    /**
     * Empieza a observar el estado y los eventos del ViewModel mientras
     * la pantalla esté visible.
     *
     * @param binding Vistas de la pantalla.
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
     * Pinta cada nuevo estado: contenido de la tarea, comentarios y
     * aspecto de los botones.
     *
     * @param binding Vistas de la pantalla.
     */
    private suspend fun observeState(binding: FragmentAssignmentDetailBinding) {
        viewModel.state.collect { state ->
            val assignment = state.assignment
            if (assignment != null) {
                renderAssignment(binding, assignment)
                renderComments(binding, assignment, state.comments, state.currentUserId)
            }

            val canInteract = !state.isBusy()
            updateButtonAppearance(
                binding.btnSubmitAssignment,
                enabled = canInteract && assignment?.canSubmit == true,
                isOutlined = false,
            )
            updateButtonAppearance(
                binding.btnDeleteSubmission,
                enabled = canInteract && assignment?.canEdit == true,
                isOutlined = true,
            )
            updatePostCommentButtonState(binding)
        }
    }

    /**
     * Indica si hay alguna operación de red en curso que deba bloquear
     * los botones.
     *
     * @receiver Estado actual de la pantalla.
     * @return `true` si se está entregando, borrando o gestionando comentarios.
     */
    private fun AssignmentDetailUiState.isBusy(): Boolean =
        isSubmitting || isDeleting || isPostingComment || isDeletingComment

    /**
     * Pinta un botón en acento (activo) o en gris (inactivo).
     *
     * - Activo, relleno: fondo de acento y texto/icono claros.
     * - Activo, con borde: fondo transparente y texto/icono/borde de acento.
     * - Inactivo: todo en gris (fondo, texto, icono y borde).
     *
     * @param button Botón a pintar.
     * @param enabled `true` si el botón debe poder pulsarse.
     * @param isOutlined `true` para el estilo de botón con borde.
     */
    private fun updateButtonAppearance(button: MaterialButton, enabled: Boolean, isOutlined: Boolean) {
        button.isEnabled = enabled
        val context = requireContext()

        if (enabled) {
            val accent = context.resolveColorAttr(R.attr.appAccent)
            val white = ContextCompat.getColor(context, R.color.texto_boton)
            val foregroundColor = if (isOutlined) accent else white
            val backgroundColor = if (isOutlined) Color.TRANSPARENT else accent

            button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            button.setTextColor(foregroundColor)
            button.iconTint = ColorStateList.valueOf(foregroundColor)
            if (isOutlined) button.strokeColor = ColorStateList.valueOf(accent)
        } else {
            val disabledBackground = ContextCompat.getColor(context, R.color.boton_deshabilitado)
            val disabledForeground = ContextCompat.getColor(context, R.color.texto_secundario)

            button.backgroundTintList = ColorStateList.valueOf(disabledBackground)
            button.setTextColor(disabledForeground)
            button.iconTint = ColorStateList.valueOf(disabledForeground)
            if (isOutlined) button.strokeColor = ColorStateList.valueOf(disabledBackground)
        }
    }

    /**
     * Reacciona a los eventos de un solo uso del ViewModel (mensajes y
     * confirmaciones).
     *
     * @param binding Vistas de la pantalla.
     */
    private suspend fun observeEvents(binding: FragmentAssignmentDetailBinding) {
        viewModel.events.collect { event ->
            when (event) {
                is AssignmentDetailUiEvent.ShowError -> showToast(event.messageResId, event.arg)
                AssignmentDetailUiEvent.SubmissionSuccess -> {
                    dismissSubmissionDialog()
                    showToast(R.string.toast_submission_success)
                }
                AssignmentDetailUiEvent.DeletionSuccess -> showToast(R.string.toast_deletion_success)
                AssignmentDetailUiEvent.CommentPosted -> binding.inpNewComment.text?.clear()
                AssignmentDetailUiEvent.CommentDeleted -> Unit
                is AssignmentDetailUiEvent.DownloadStarted ->
                    showToast(R.string.toast_downloading, event.fileName)
                AssignmentDetailUiEvent.DownloadFailed -> showToast(R.string.toast_download_error)
            }
        }
    }

    /**
     * Pinta los datos de la tarea: textos, estado, nota, botones y archivos.
     *
     * La descripción se renderiza con sus spans (negritas, enlaces…)
     * en vez de aplanarse a texto: Moodle suele enlazar el cuestionario
     * o los materiales con un texto distinto de la URL, y aplanarlo
     * dejaba esos enlaces invisibles e inutilizables.
     *
     * @param binding Vistas de la pantalla.
     * @param assignment Detalle de la tarea.
     */
    private fun renderAssignment(binding: FragmentAssignmentDetailBinding, assignment: AssignmentDetail) {
        binding.txtAssignName.text = assignment.name
        if (assignment.description.isBlank()) {
            binding.txtAssignDescription.text = getString(R.string.label_no_description)
        } else {
            binding.txtAssignDescription.text = HtmlUtils.renderHtml(assignment.description)
            binding.txtAssignDescription.movementMethod = LinkMovementMethod.getInstance()
        }
        binding.txtAssignDates.text = assignment.dueDate
            ?.let { dueDate -> getString(R.string.label_due_date, FormatUtils.formatDateTime(dueDate)) }
            ?: getString(R.string.label_no_due_date)

        renderTeacherFeedback(binding, assignment)

        binding.txtSubmissionStatus.text = when {
            assignment.statusUnknown -> getString(R.string.status_unknown)
            assignment.isSubmitted -> getString(R.string.status_submitted)
            assignment.isDraft -> getString(R.string.status_draft)
            else -> getString(R.string.status_not_submitted)
        }

        binding.txtSubmissionGrade.isVisible = assignment.submissionGraded
        if (assignment.submissionGraded) {
            binding.txtSubmissionGrade.text = getString(R.string.label_grade, gradeText(assignment))
        }

        binding.btnSubmitAssignment.isVisible = !assignment.isSubmitted
        binding.btnDeleteSubmission.isVisible = assignment.isSubmitted

        binding.containerSubmissionAttachments.removeAllViews()
        binding.layoutSubmissionAttachments.isVisible = assignment.submissionFiles.isNotEmpty()
        assignment.submissionFiles.forEach { file ->
            addFileRow(binding.containerSubmissionAttachments, file)
        }

        binding.containerAttachments.removeAllViews()
        binding.layoutAttachments.isVisible = assignment.files.isNotEmpty()
        assignment.files.forEach { file -> addFileRow(binding.containerAttachments, file) }
    }

    /**
     * Pinta el comentario del profesor y los archivos de la corrección.
     *
     * ## Qué se oculta
     *
     * Si no hay comentario, la sección entera desaparece en lugar de
     * dejar un hueco con el título vacío. Lo mismo con los archivos.
     *
     * @param binding Vistas de la pantalla.
     * @param assignment Detalle de la tarea.
     */
    private fun renderTeacherFeedback(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        val feedback = assignment.feedbackComment
        binding.layoutTeacherFeedback.isVisible = !feedback.isNullOrBlank()
        if (!feedback.isNullOrBlank()) {
            binding.txtTeacherFeedback.text = HtmlUtils.renderHtml(feedback)
            binding.txtTeacherFeedback.movementMethod = LinkMovementMethod.getInstance()
        }

        binding.containerGradedFiles.removeAllViews()
        binding.layoutGradedFiles.isVisible = assignment.gradedFiles.isNotEmpty()
        assignment.gradedFiles.forEach { file -> addFileRow(binding.containerGradedFiles, file) }
    }

    /**
     * Compone el texto de la nota: el que muestra Moodle si existe, o
     * "nota / máximo" a partir de los números.
     *
     * @param assignment Detalle de la tarea ya calificada.
     * @return El texto de la nota, o vacío si no hay ningún dato.
     */
    private fun gradeText(assignment: AssignmentDetail): String {
        val moodleDisplay = assignment.gradeDisplay?.let { display -> HtmlUtils.stripHtml(display) }
        if (moodleDisplay != null) return moodleDisplay

        val numericGrade = assignment.submissionGrade ?: return ""
        return "${FormatUtils.formatGrade(numericGrade)} / ${FormatUtils.formatGrade(assignment.maxGrade)}"
    }

    /**
     * Pinta el hilo de comentarios de la entrega, o lo oculta si no procede.
     *
     * Solo se muestra la papelera en los comentarios que el usuario puede
     * borrar ([isOwnComment]).
     *
     * @param binding Vistas de la pantalla.
     * @param assignment Detalle de la tarea.
     * @param comments Comentarios a pintar.
     * @param currentUserId Identificador del usuario actual.
     */
    private fun renderComments(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
        comments: List<SubmissionComment>,
        currentUserId: Int,
    ) {
        val submissionId = assignment.submissionId ?: 0
        val canShow = submissionId > 0 && COMMENTS_PLUGIN in assignment.enabledSubmissionPlugins
        binding.layoutSubmissionComments.isVisible = canShow
        if (!canShow) return

        binding.containerSubmissionComments.removeAllViews()
        binding.txtNoComments.isVisible = comments.isEmpty()
        comments.forEach { comment ->
            val row = ItemSubmissionCommentBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.containerSubmissionComments,
                false,
            )
            row.txtCommentAuthor.text = comment.author
            row.txtCommentDate.text = if (comment.createdAt > 0) {
                FormatUtils.formatDateTime(comment.createdAt)
            } else {
                getString(R.string.label_unknown_date)
            }
            row.txtCommentContent.text = HtmlUtils.renderHtml(comment.content)
            row.txtCommentContent.movementMethod = LinkMovementMethod.getInstance()
            LinkifyCompat.addLinks(row.txtCommentContent, Linkify.WEB_URLS)

            row.btnDeleteComment.isVisible = isOwnComment(comment, currentUserId)
            row.btnDeleteComment.setOnClickListener { confirmDeleteComment(comment) }
            binding.containerSubmissionComments.addView(row.root)
        }
    }

    /**
     * Decide si un comentario es del usuario actual.
     *
     * Se fía de lo que indica Moodle ([SubmissionComment.canDelete]) y,
     * como respaldo, compara los **identificadores** de usuario. Antes se
     * comparaban los nombres completos, lo que fallaba con homónimos o
     * si el nombre cambiaba.
     *
     * @param comment Comentario a evaluar.
     * @param currentUserId Identificador del usuario actual (`0` si se desconoce).
     * @return `true` si el comentario es propio.
     */
    private fun isOwnComment(comment: SubmissionComment, currentUserId: Int): Boolean =
        comment.canDelete || (currentUserId != 0 && comment.authorId == currentUserId)

    /**
     * Pide confirmación antes de borrar un comentario.
     *
     * @param comment Comentario que se borraría.
     */
    private fun confirmDeleteComment(comment: SubmissionComment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_delete_comment_title)
            .setMessage(R.string.dialog_delete_comment_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteComment(comment.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Añade a un contenedor una fila con el nombre, el tamaño y el icono
     * de descarga de un archivo; al pulsarla se pide confirmación para
     * descargarlo.
     *
     * @param container Contenedor al que se añade la fila.
     * @param file Archivo que representa la fila.
     */
    private fun addFileRow(container: ViewGroup, file: CourseFile) {
        val item = ItemFileBinding.inflate(LayoutInflater.from(requireContext()), container, false)
        item.txtFileName.text = file.fileName
        item.txtFileSize.text = FormatUtils.formatFileSize(file.fileSize)
        item.imgFileIcon.setImageResource(R.drawable.ic_arrow_download)
        item.root.setOnClickListener { confirmDownload(file) }
        container.addView(item.root)
    }

    /**
     * Pide confirmación y delega la descarga en el ViewModel.
     *
     * @param file Archivo a descargar.
     */
    private fun confirmDownload(file: CourseFile) {
        FileDownloadDialog.show(requireContext(), file.fileName) {
            viewModel.downloadFile(file)
        }
    }
}
