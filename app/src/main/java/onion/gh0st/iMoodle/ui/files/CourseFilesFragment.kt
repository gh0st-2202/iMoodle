package onion.gh0st.iMoodle.ui.files

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentCourseFilesBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.common.HtmlUtils
import javax.inject.Inject

/**
 * Pantalla con la lista de archivos de un módulo de curso.
 *
 * ## Qué muestra
 *
 * - **Toolbar**: con el nombre del módulo.
 * - **Cabecera** (opcional): con el nombre y la descripción del
 *   módulo.
 * - **Lista de archivos**: cada uno descargable con un click.
 * - **Pull-to-refresh**: para recargar la lista.
 *
 * ## Cuándo se abre
 *
 * Desde `CourseDetailFragment`, cuando el usuario pulsa un módulo con
 * varios archivos (o una carpeta).
 *
 * ## Acceso a la sesión
 *
 * El Fragment inyecta `SessionRepository` solo para verificar que
 * hay una sesión activa antes de permitir descargas. Si no hay
 * sesión, no se muestra el diálogo de descarga.
 */
@AndroidEntryPoint
class CourseFilesFragment : Fragment(R.layout.fragment_course_files) {

    /**
     * Fuente de la sesión activa. Se usa como "guard" antes de las
     * descargas: si no hay sesión, no se permite descargar.
     */
    @Inject lateinit var sessionRepo: SessionRepository

    /** Nombre del módulo, recibido como argumento. */
    private var moduleName: String? = null

    /** Descripción del módulo, recibida como argumento. */
    private var moduleDescription: String? = null

    /** ViewModel de la pantalla. */
    private val viewModel: CourseFilesViewModel by viewModels()

    companion object {

        /**
         * Construye una instancia del Fragment con los datos del
         * módulo.
         *
         * @param courseId ID del curso.
         * @param moduleId ID de la instancia del módulo (`cmid`).
         * @param moduleName Nombre visible del módulo (opcional).
         * @param moduleDescription Descripción HTML del módulo
         *   (opcional).
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(
            courseId: Int,
            moduleId: Int,
            moduleName: String? = null,
            moduleDescription: String? = null,
        ): CourseFilesFragment = CourseFilesFragment().apply {
            arguments = Bundle().apply {
                putInt("COURSE_ID", courseId)
                putInt("MODULE_ID", moduleId)
                putString("MODULE_NAME", moduleName)
                putString("MODULE_DESCRIPTION", moduleDescription)
            }
        }
    }

    /**
     * Ciclo de vida de creación del Fragment.
     *
     * Recupera el nombre y la descripción del módulo de los
     * argumentos. Se hace en `onCreate` para que sobrevivan a una
     * recreación de la vista.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleName = arguments?.getString("MODULE_NAME")
        moduleDescription = arguments?.getString("MODULE_DESCRIPTION")
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourseFilesBinding.bind(view)
        setupToolbar(binding)
        setupHeader(binding)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeUi(binding, adapter)
    }

    /**
     * Configura el toolbar con el nombre del módulo y botón atrás.
     *
     * @param binding Binding de la vista.
     */
    private fun setupToolbar(binding: FragmentCourseFilesBinding) {
        binding.toolbarFiles.title = moduleName ?: getString(R.string.title_files)
        binding.toolbarFiles.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Muestra u oculta la cabecera con nombre + descripción del
     * módulo.
     *
     * ## Cuándo se oculta
     *
     * Si el módulo no tiene ni nombre ni descripción. En ese caso,
     * mostrar una cabecera vacía sería ruido visual.
     *
     * ## Sobre la descripción
     *
     * Se limpia de HTML con `HtmlUtils.stripHtml` porque el
     * `TextView` de la cabecera no preserva estructura.
     *
     * @param binding Binding de la vista.
     */
    private fun setupHeader(binding: FragmentCourseFilesBinding) {
        if (moduleName.isNullOrBlank() && moduleDescription.isNullOrBlank()) {
            binding.layoutHeader.isVisible = false
            return
        }
        binding.layoutHeader.isVisible = true
        binding.txtModuleTitle.isVisible = !moduleName.isNullOrBlank()
        binding.txtModuleTitle.text = moduleName ?: ""
        val plainDescription = moduleDescription?.let(HtmlUtils::stripHtml).orEmpty()
        binding.txtModuleDescription.isVisible = plainDescription.isNotBlank()
        binding.txtModuleDescription.text = plainDescription
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentCourseFilesBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarFiles) { view, insets ->
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
     * Construye el adapter de archivos con el callback de descarga.
     *
     * @return Adapter listo para el `RecyclerView`.
     */
    private fun buildAdapter() = CourseFilesAdapter { file -> confirmDownload(file) }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
     */
    private fun setupRecyclerView(
        binding: FragmentCourseFilesBinding,
        adapter: CourseFilesAdapter,
    ) {
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter
        binding.swipeRefreshFiles.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private fun observeUi(binding: FragmentCourseFilesBinding, adapter: CourseFilesAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeState(binding, adapter) }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Refleja el estado del ViewModel en la UI.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private suspend fun observeState(
        binding: FragmentCourseFilesBinding,
        adapter: CourseFilesAdapter,
    ) {
        viewModel.state.collect { state ->
            adapter.submitList(state.files)
            binding.swipeRefreshFiles.isRefreshing = state.isLoading
        }
    }

    /**
     * Consume los eventos de un solo uso del ViewModel.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is CourseFilesUiEvent.ShowError -> showToast(event.messageResId, event.arg)
            }
        }
    }

    /**
     * Pide confirmación al usuario y lanza la descarga.
     *
     * ## Comportamiento
     *
     * - Si no hay sesión activa, no hace nada (guard semántico).
     * - Si hay sesión, muestra un diálogo de confirmación.
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
     * Muestra un toast largo.
     *
     * @param messageResId ID del recurso de string.
     * @param arg Argumento opcional para formatear.
     */
    private fun showToast(@StringRes messageResId: Int, arg: String? = null) {
        val message = arg?.let { formatArg -> getString(messageResId, formatArg) }
            ?: getString(messageResId)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}