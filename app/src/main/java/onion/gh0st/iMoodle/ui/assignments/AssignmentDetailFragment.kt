package onion.gh0st.iMoodle.ui.assignments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentAssignmentDetailBinding
import onion.gh0st.iMoodle.databinding.ItemFileBinding
import onion.gh0st.iMoodle.domain.model.assignment.AssignmentDetail
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
 * Pantalla con el detalle completo de una tarea.
 *
 * ## Qué muestra
 *
 * Toda la información de una tarea de Moodle:
 *
 * - **Nombre** y **descripción**.
 * - **Fecha límite de entrega** (formateada).
 * - **Estado de la entrega** del alumno (entregado, sin entregar,
 *   borrador, desconocido).
 * - **Nota** obtenida (si está calificada), con color verde o rojo
 *   según si aprueba o no.
 * - **Archivos adjuntos** del enunciado (si los hay).
 * - **Botón de entrega** (si la tarea lo permite). Actualmente el
 *   botón muestra un toast de "en desarrollo": la app es de solo
 *   consulta.
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `AssignmentDetailViewModel`, que a su vez usa el caso
 * de uso `GetAssignmentDetail`. El Fragment solo se preocupa de
 * pintar los datos y reaccionar a eventos.
 *
 * ## Por qué el Fragment no hace lógica
 *
 * Porque el Fragment puede ser destruido y recreado (al rotar, al ir
 * a segundo plano, etc.), y si tuviera lógica, se perdería o se
 * duplicaría. Por eso toda la lógica está en el ViewModel, y el
 * Fragment solo:
 *
 * 1. Se suscribe al estado.
 * 2. Pinta lo que recibe.
 * 3. Se suscribe a eventos.
 * 4. Reacciona a ellos (toasts, navegación).
 *
 * ## Inyección de dependencias
 *
 * La anotación `@AndroidEntryPoint` es necesaria para que Hilt pueda
 * inyectar las dependencias del Fragment (en este caso,
 * `sessionRepo`).
 *
 * ## Acceso a la sesión
 *
 * El Fragment inyecta `SessionRepository` solo para verificar que
 * hay una sesión activa antes de permitir descargas. Si no hay
 * sesión, no se muestra el diálogo de descarga. Es un "guard
 * semántico": si no hay sesión, no deberíamos estar aquí, pero por
 * si acaso, no se permite la acción.
 */
@AndroidEntryPoint
class AssignmentDetailFragment : Fragment(R.layout.fragment_assignment_detail) {

    /**
     * Fuente de la sesión activa. Se usa como "guard" antes de las
     * descargas: si no hay sesión, no se permite descargar.
     */
    @Inject lateinit var sessionRepo: SessionRepository

    /** ViewModel de la pantalla, inyectado por Hilt. */
    private val viewModel: AssignmentDetailViewModel by viewModels()

    companion object {

        /**
         * Construye una instancia del Fragment con los argumentos
         * necesarios.
         *
         * ## Qué argumentos recibe
         *
         * - `courseId`: ID del curso al que pertenece la tarea.
         * - `assignmentId`: ID (o `cmid`) de la tarea.
         *
         * Se pasan como `Bundle` porque los Fragments no deben tener
         * constructor con parámetros: Android necesita poder
         * recrearlos sin argumentos.
         *
         * @param courseId ID del curso.
         * @param assignmentId ID (o `cmid`) de la tarea.
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(courseId: Int, assignmentId: Int): AssignmentDetailFragment =
            AssignmentDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt("COURSE_ID", courseId)
                    putInt("ASSIGNMENT_ID", assignmentId)
                }
            }
    }

    /**
     * Ciclo de vida de la vista.
     *
     * ## Qué hace
     *
     * 1. Infla el binding.
     * 2. Configura el toolbar (con botón atrás).
     * 3. Aplica los insets de barras del sistema.
     * 4. Configura el botón de entrega.
     * 5. Observa el estado y los eventos del ViewModel.
     *
     * ## Por qué `onViewCreated` y no `onCreate`
     *
     * Porque `onCreate` no tiene vista (el Fragment aún no se ha
     * inflado). `onViewCreated` sí. Aquí es donde se enlazan las
     * vistas y se configuran.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo (no usado
     *   directamente: el estado está en el ViewModel).
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAssignmentDetailBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        setupSubmitButton(binding)
        observeUi(binding)
    }

    /**
     * Configura el toolbar con botón atrás.
     *
     * ## Qué hace
     *
     * - Configura el botón de navegación para volver atrás.
     * - El título ya viene del layout.
     *
     * @param binding Binding de la vista.
     */
    private fun setupToolbar(binding: FragmentAssignmentDetailBinding) {
        binding.toolbarAssign.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * ## Qué son los insets
     *
     * Los insets son los márgenes que ocupan las barras del sistema
     * (barra de estado, barra de navegación). Si no se aplican, el
     * contenido se dibuja detrás de las barras y queda tapado.
     *
     * ## Qué hace
     *
     * Añade padding al `AppBarLayout` para que no se dibuje debajo de
     * las barras. El padding original se mantiene (se suma).
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentAssignmentDetailBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarAssign) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom,
            )
            insets
        }
    }

    /**
     * Configura el botón de entrega.
     *
     * ## Comportamiento actual
     *
     * Muestra un toast de "en desarrollo". La app es de solo
     * consulta: no implementa la entrega de tareas.
     *
     * ## Cuándo se muestra
     *
     * Solo cuando la tarea lo permite (ver [renderSubmitVisibility]).
     *
     * @param binding Binding de la vista.
     */
    private fun setupSubmitButton(binding: FragmentAssignmentDetailBinding) {
        binding.btnSubmitAssignment.setOnClickListener {
            showToast(R.string.toast_upload_in_development)
        }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * ## Qué hace
     *
     * Lanza dos corrutinas dentro de un `repeatOnLifecycle(STARTED)`:
     *
     * 1. Una para el estado (que se re-emite cada vez que cambia).
     * 2. Otra para los eventos (que se emiten una sola vez).
     *
     * ## Por qué `repeatOnLifecycle(STARTED)`
     *
     * Porque pausa la recolección cuando el Fragment no está
     * visible (por ejemplo, cuando el usuario va a otra pantalla).
     * Así no se procesan eventos en background, y no hay fugas.
     *
     * ## Por qué dos corrutinas y no una
     *
     * Porque `state` y `events` son `Flow`s independientes. Si se
     * recolectaran en la misma corrutina, habría que elegir cuál
     * primero, y el otro se quedaría sin procesar. Con dos corrutinas
     * en paralelo, ambos se procesan a la vez.
     *
     * @param binding Binding de la vista.
     */
    private fun observeUi(binding: FragmentAssignmentDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Refleja el estado del ViewModel en la UI.
     *
     * ## Qué hace
     *
     * Cuando llega un `AssignmentDetail`, lo pinta en el binding con
     * `renderAssignment`.
     *
     * ## Por qué no se hace nada si es `null`
     *
     * Porque mientras carga, el `AssignmentDetail` es `null`. No hay
     * nada que pintar. La UI muestra el layout vacío con el spinner
     * del `isLoading`.
     *
     * @param binding Binding de la vista.
     */
    private suspend fun observeState(binding: FragmentAssignmentDetailBinding) {
        viewModel.state.collect { state ->
            state.assignment?.let { assignment ->
                renderAssignment(binding, assignment)
            }
        }
    }

    /**
     * Consume los eventos de un solo uso del ViewModel.
     *
     * @param binding Binding de la vista (no usado directamente, pero
     *   necesario para mantener la simetría con otros Fragments).
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is AssignmentDetailUiEvent.ShowError -> showToast(
                    event.messageResId,
                    event.arg,
                )
            }
        }
    }

    /**
     * Renderiza el detalle completo de una tarea.
     *
     * ## Qué hace
     *
     * Delega en sub-renderizadores especializados:
     *
     * - [renderBasicInfo]: nombre y descripción.
     * - [renderDates]: fechas.
     * - [renderStatus]: estado de la entrega.
     * - [renderGrade]: nota.
     * - [renderSubmitVisibility]: visibilidad del botón de entrega.
     * - [renderAttachments]: archivos adjuntos.
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderAssignment(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        renderBasicInfo(binding, assignment)
        renderDates(binding, assignment)
        renderStatus(binding, assignment)
        renderGrade(binding, assignment)
        renderSubmitVisibility(binding, assignment)
        renderAttachments(binding, assignment.files)
    }

    /**
     * Renderiza nombre y descripción.
     *
     * ## Sobre la descripción
     *
     * La descripción viene con formato HTML tal cual la envía
     * Moodle. Aquí se limpia con `HtmlUtils.stripHtml` para mostrar
     * texto plano, porque el `TextView` de esta pantalla no tiene
     * `LinkMovementMethod` ni formateo enriquecido.
     *
     * ## Qué pasa si la descripción está vacía
     *
     * Se muestra un texto genérico ("Sin descripción") en lugar de
     * dejar el campo en blanco. Es más claro para el usuario.
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderBasicInfo(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        binding.txtAssignName.text = assignment.name
        binding.txtAssignDescription.text = if (assignment.description.isBlank()) {
            getString(R.string.label_no_description)
        } else {
            HtmlUtils.stripHtml(assignment.description)
        }
    }

    /**
     * Renderiza la fecha de entrega formateada, o "Sin fecha" si no
     * hay.
     *
     * ## Formato de la fecha
     *
     * Se usa `dd/MM/yyyy HH:mm`, que es el formato habitual en
     * España. La fecha viene en segundos (epoch), así que se
     * multiplica por 1000 para pasarla a milisegundos.
     *
     * ## Sobre `SimpleDateFormat`
     *
     * Se crea en cada render. No es óptimo, pero esta pantalla no se
     * re-renderiza muchas veces (solo cuando llega el detalle), así
     * que no merece la pena cachearlo.
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderDates(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        binding.txtAssignDates.text = if (assignment.dueDate != null) {
            val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(assignment.dueDate * 1000))
            getString(R.string.label_due_date, formattedDate)
        } else {
            getString(R.string.label_no_due_date)
        }
    }

    /**
     * Renderiza el estado de la entrega.
     *
     * ## Cómo se decide qué texto mostrar
     *
     * Se evalúan los casos en este orden:
     *
     * 1. **`statusUnknown = true`**: "Estado desconocido". Significa
     *    que no se pudo consultar el estado (por un error de red, por
     *    ejemplo). No se puede asumir "sin entregar": sería
     *    engañoso.
     * 2. **`submissionStatus == "submitted"`**: "Entregado".
     * 3. **`submissionStatus == "draft"`**: "Borrador (no
     *    entregado)".
     * 4. **Cualquier otro caso**: "Sin entregar".
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderStatus(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        binding.txtSubmissionStatus.text = when {
            assignment.statusUnknown -> getString(R.string.status_unknown)
            assignment.submissionStatus == "submitted" -> getString(R.string.status_submitted)
            assignment.submissionStatus == "draft" -> getString(R.string.status_draft)
            else -> getString(R.string.status_not_submitted)
        }
    }

    /**
     * Renderiza la nota de la tarea.
     *
     * ## Comportamiento
     *
     * - Si no está calificada (o no se pudo consultar), oculta el
     *   campo.
     * - Si está calificada, muestra la nota con color verde (si
     *   aprueba) o rojo (si suspende).
     *
     * ## Cómo se decide el color
     *
     * Delega en `passingColor`, que a su vez usa
     * `resolveGradeDisplay` y `inferGradeColorFromDisplay`.
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderGrade(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        val gradeDisplay = resolveGradeDisplay(assignment)
        if (gradeDisplay == null) {
            binding.txtSubmissionGrade.visibility = View.GONE
            return
        }
        binding.txtSubmissionGrade.visibility = View.VISIBLE
        binding.txtSubmissionGrade.text = getString(R.string.label_grade, gradeDisplay)
        passingColor(assignment)?.let { resolvedColor ->
            binding.txtSubmissionGrade.setTextColor(resolvedColor)
        }
    }

    /**
     * Resuelve la nota a mostrar.
     *
     * ## Comportamiento
     *
     * - Si no está calificada, devuelve `null`.
     * - Si hay una nota formateada por Moodle
     *   (`gradeDisplay`), la usa tal cual (limpiando el HTML).
     * - Si no, compone la nota numérica con la máxima:
     *   `"7.5 / 10"`.
     *
     * @param assignment Detalle de la tarea.
     * @return La nota a mostrar, o `null` si no aplica.
     */
    private fun resolveGradeDisplay(assignment: AssignmentDetail): String? {
        if (!assignment.submissionGraded) return null
        return assignment.gradeDisplay?.let { gradeDisplay ->
            HtmlUtils.stripHtml(gradeDisplay)
        } ?: assignment.submissionGrade?.let { numericGrade ->
            "${FormatUtils.formatGrade(numericGrade)} / " +
                    FormatUtils.formatGrade(assignment.maxGrade)
        }
    }

    /**
     * Decide el color de la nota.
     *
     * ## Comportamiento
     *
     * - Si hay una nota numérica, calcula si aprueba (nota >= 50%
     *   de la máxima) y devuelve verde o rojo.
     * - Si no hay nota numérica, infiere el color a partir del texto
     *   de la nota (por ejemplo, "Apto" → verde, "No apto" → rojo).
     * - Si no se puede inferir, devuelve `null` (se queda con el
     *   color por defecto del tema).
     *
     * @param assignment Detalle de la tarea.
     * @return El color, o `null` si no se puede inferir.
     */
    private fun passingColor(assignment: AssignmentDetail): Int? {
        assignment.submissionGrade?.let { numericGrade ->
            val isPassing = numericGrade >= assignment.maxGrade * 0.5
            return resolveGradeColor(isPassing = isPassing)
        }
        val normalizedDisplay = assignment.gradeDisplay
            ?.let { gradeDisplay -> HtmlUtils.stripHtml(gradeDisplay) }
            ?.lowercase()
            ?: return null
        return inferGradeColorFromDisplay(normalizedDisplay)
    }

    /**
     * Resuelve el color de aprobado o suspenso.
     *
     * @param isPassing `true` para verde, `false` para rojo.
     * @return El color resuelto.
     */
    private fun resolveGradeColor(isPassing: Boolean): Int {
        val colorResId = if (isPassing) R.color.color_acento else android.R.color.holo_red_dark
        return ContextCompat.getColor(requireContext(), colorResId)
    }

    /**
     * Infiere el color de la nota a partir de su texto.
     *
     * ## Cómo funciona
     *
     * - Primero comprueba si el texto contiene alguna palabra de
     *   "suspenso" (`"no apto"`, `"suspenso"`, `"insuficiente"`,
     *   etc.). Si es así, rojo.
     * - Si no, comprueba si contiene alguna palabra de "aprobado"
     *   (`"apto"`, `"sobresaliente"`, `"notable"`, etc.). Si es así,
     *   verde.
     * - Si no coincide ninguna, devuelve `null`.
     *
     * ## Por qué se comprueba "suspenso" antes que "aprobado"
     *
     * Porque algunas palabras de suspenso contienen la de aprobado:
     * "No apto" contiene "apto". Si comprobáramos primero "apto",
     * "No apto" se clasificaría como aprobado, que es incorrecto.
     *
     * @param normalizedDisplay Texto de la nota, en minúsculas y sin
     *   HTML.
     * @return El color inferido, o `null` si no se puede.
     */
    private fun inferGradeColorFromDisplay(normalizedDisplay: String): Int? {
        val failingKeywords = listOf(
            "no apto", "suspenso", "insuficiente", "no superad",
            "muy deficiente", "deficiente",
        )
        val passingKeywords = listOf(
            "apto", "aprobad", "sobresaliente", "notable",
            "bien", "excelente", "matrícula", "matricula", "superad",
        )
        return when {
            failingKeywords.any { keyword -> normalizedDisplay.contains(keyword) } ->
                resolveGradeColor(isPassing = false)

            passingKeywords.any { keyword -> normalizedDisplay.contains(keyword) } ->
                resolveGradeColor(isPassing = true)

            else -> null
        }
    }

    /**
     * Decide si el botón de entrega debe mostrarse u ocultarse.
     *
     * ## Cuándo se muestra
     *
     * Solo si el alumno puede entregar (`canSubmit = true`) **y** no
     * ha entregado ya (`submissionStatus != "submitted"`). Si ya ha
     * entregado, no tiene sentido mostrar el botón.
     *
     * @param binding Binding de la vista.
     * @param assignment Detalle de la tarea.
     */
    private fun renderSubmitVisibility(
        binding: FragmentAssignmentDetailBinding,
        assignment: AssignmentDetail,
    ) {
        val isVisible = assignment.canSubmit && assignment.submissionStatus != "submitted"
        binding.btnSubmitAssignment.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    /**
     * Renderiza la lista de archivos adjuntos del enunciado.
     *
     * ## Comportamiento
     *
     * - Si no hay archivos, oculta la sección de adjuntos.
     * - Si hay, la muestra y añade una fila por archivo.
     *
     * @param binding Binding de la vista.
     * @param files Lista de archivos.
     */
    private fun renderAttachments(
        binding: FragmentAssignmentDetailBinding,
        files: List<CourseFile>,
    ) {
        binding.containerAttachments.removeAllViews()
        if (files.isEmpty()) {
            binding.layoutAttachments.visibility = View.GONE
            return
        }
        binding.layoutAttachments.visibility = View.VISIBLE
        files.forEach { file -> addAttachmentView(binding, file) }
    }

    /**
     * Infla una fila de adjunto y la añade al contenedor.
     *
     * @param binding Binding de la vista.
     * @param file Archivo a mostrar.
     */
    private fun addAttachmentView(
        binding: FragmentAssignmentDetailBinding,
        file: CourseFile,
    ) {
        val itemBinding = ItemFileBinding.inflate(
            LayoutInflater.from(requireContext()),
            binding.containerAttachments,
            false,
        )
        itemBinding.txtFileName.text = file.fileName
        itemBinding.txtFileSize.text = FormatUtils.formatFileSize(file.fileSize)
        itemBinding.imgFileIcon.setImageResource(R.drawable.ic_arrow_download)
        itemBinding.root.setOnClickListener { confirmDownload(file) }
        binding.containerAttachments.addView(itemBinding.root)
    }

    /**
     * Pide confirmación al usuario y lanza la descarga del adjunto.
     *
     * ## Comportamiento
     *
     * - Si no hay sesión activa, no hace nada (guard semántico).
     * - Si hay sesión, muestra un diálogo de confirmación. Si el
     *   usuario confirma, lanza la descarga.
     *
     * @param file Archivo a descargar.
     */
    private fun confirmDownload(file: CourseFile) {
        if (sessionRepo.session.value == null) return
        FileDownloadHelper.showConfirmationDialog(requireContext(), file) {
            FileDownloadHelper.download(requireContext(), file)
        }
    }

    /**
     * Muestra un toast corto resolviendo el string resource.
     *
     * @param messageResId ID del recurso de string.
     * @param arg Argumento opcional para formatear.
     */
    private fun showToast(@StringRes messageResId: Int, arg: String? = null) {
        val message = arg?.let { formatArg -> getString(messageResId, formatArg) }
            ?: getString(messageResId)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}