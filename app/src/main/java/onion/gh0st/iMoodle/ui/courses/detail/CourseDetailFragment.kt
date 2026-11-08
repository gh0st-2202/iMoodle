package onion.gh0st.iMoodle.ui.courses.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentCourseDetailBinding
import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.domain.model.course.ModuleType
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.ui.assignments.AssignmentDetailFragment
import onion.gh0st.iMoodle.ui.common.WebUrls
import onion.gh0st.iMoodle.ui.files.CourseFilesFragment
import onion.gh0st.iMoodle.ui.files.FileDownloadHelper
import onion.gh0st.iMoodle.ui.forum.list.ForumFragment
import javax.inject.Inject

/**
 * Pantalla de detalle de un curso.
 *
 * ## Qué muestra
 *
 * El curso se organiza en secciones (temas). Cada sección agrupa
 * módulos (tareas, foros, recursos, enlaces…). La pantalla muestra
 * esta estructura con:
 *
 * - Una cabecera por sección.
 * - Una fila por módulo, con su icono, nombre y descripción
 *   opcional.
 * - Las etiquetas (módulos `label`) como texto inline.
 *
 * Además, tiene un filtro de chips en la parte superior para
 * mostrar solo una sección concreta.
 *
 * ## Cómo navega al pulsar un módulo
 *
 * Según el `ModuleType`:
 *
 * - **Assignment**: navega al detalle de la tarea.
 * - **Resource**: si tiene un archivo, lo descarga; si tiene varios,
 *   abre la pantalla de archivos.
 * - **Folder**: abre la pantalla de archivos.
 * - **Url**: resuelve la URL externa y la abre con `ACTION_VIEW`.
 * - **Forum**: navega al listado de discusiones del foro.
 * - **Label**: no hace nada (no es navegable).
 * - **Other**: abre una pantalla genérica.
 *
 * ## Por qué el Fragment no hace lógica
 *
 * Porque el Fragment puede ser destruido y recreado (al rotar, al ir
 * a segundo plano, etc.), y si tuviera lógica, se perdería o se
 * duplicaría. Por eso toda la lógica está en el ViewModel, y el
 * Fragment solo:
 *
 * 1. Configura la vista.
 * 2. Observa el estado.
 * 3. Reacciona a clicks.
 *
 * ## Acceso a la sesión
 *
 * El Fragment inyecta `SessionRepository` solo para verificar que
 * hay una sesión activa antes de permitir descargas. Si no hay
 * sesión, no se muestra el diálogo de descarga.
 */
@AndroidEntryPoint
class CourseDetailFragment : Fragment(R.layout.fragment_course_detail) {

    /**
     * Fuente de la sesión activa. Se usa como "guard" antes de las
     * descargas: si no hay sesión, no se permite descargar.
     */
    @Inject lateinit var sessionRepo: SessionRepository

    /** Nombre del curso, recibido como argumento. */
    private var courseName: String = ""

    /** ViewModel de la pantalla. */
    private val viewModel: CourseDetailViewModel by viewModels()

    companion object {

        /**
         * Construye una instancia del Fragment con los argumentos
         * necesarios.
         *
         * @param courseId ID del curso.
         * @param courseName Nombre visible del curso (para el
         *   toolbar).
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(courseId: Int, courseName: String): CourseDetailFragment =
            CourseDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt("COURSE_ID", courseId)
                    putString("COURSE_NAME", courseName)
                }
            }
    }

    /**
     * Ciclo de vida de creación del Fragment.
     *
     * Recupera el nombre del curso de los argumentos. Se hace en
     * `onCreate` (no en `onViewCreated`) para que el nombre
     * sobreviva a una recreación de la vista sin volver a leer el
     * `Bundle`.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        courseName = arguments?.getString("COURSE_NAME") ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCourseDetailBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        val adapter = buildAdapter()
        setupRecyclerView(binding, adapter)
        observeState(binding, adapter)
    }

    /**
     * Configura el toolbar con el nombre del curso y botón atrás.
     *
     * @param binding Binding de la vista.
     */
    private fun setupToolbar(binding: FragmentCourseDetailBinding) {
        binding.toolbarDetail.title = courseName
        binding.toolbarDetail.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentCourseDetailBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarDetail) { view, insets ->
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
     * Construye el adapter con el callback de enrutado por tipo de
     * módulo.
     *
     * @return El adapter listo.
     */
    private fun buildAdapter() = CourseDetailAdapter { moduleType -> routeModule(moduleType) }

    /**
     * Configura el `RecyclerView` y el pull-to-refresh.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter ya construido.
     */
    private fun setupRecyclerView(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        binding.rvCourseDetail.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseDetail.adapter = adapter
        binding.swipeRefreshDetail.setOnRefreshListener { viewModel.refresh() }
    }

    /**
     * Observa el estado del ViewModel y lo refleja en la UI.
     *
     * ## Qué hace
     *
     * 1. Actualiza la lista del adapter.
     * 2. Actualiza el estado del pull-to-refresh.
     * 3. Repuebla los chips de filtro si han cambiado las secciones.
     *
     * ## Por qué se cachea `lastSections`
     *
     * Porque `populateChips` reconstruye todos los chips, lo cual es
     * costoso. Si las secciones no han cambiado, no hace falta
     * hacerlo.
     *
     * @param binding Binding de la vista.
     * @param adapter Adapter al que enviar la lista.
     */
    private fun observeState(
        binding: FragmentCourseDetailBinding,
        adapter: CourseDetailAdapter,
    ) {
        var previousSections: List<String>? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    binding.swipeRefreshDetail.isRefreshing = state.isLoading
                    if (previousSections != state.sections) {
                        previousSections = state.sections
                        populateChips(binding.chipGroupSections, state.sections)
                    }
                }
            }
        }
    }

    /**
     * Reconstruye los chips de filtro por sección.
     *
     * ## Qué hace
     *
     * 1. Borra todos los chips anteriores.
     * 2. Añade un chip "Todas" (seleccionado por defecto).
     * 3. Añade un chip por cada sección.
     *
     * ## Cuándo se llama
     *
     * Cuando el usuario cambia el filtro. El chip seleccionado
     * determina qué sección se muestra.
     *
     * @param chipGroup Grupo de chips.
     * @param sections Lista de nombres de sección.
     */
    private fun populateChips(chipGroup: ChipGroup, sections: List<String>) {
        chipGroup.removeAllViews()
        chipGroup.addView(
            chip(
                text = getString(R.string.filter_all),
                selected = true,
                onClick = { viewModel.setSection(null) },
            ),
        )
        sections.forEach { section ->
            chipGroup.addView(
                chip(
                    text = section,
                    selected = false,
                    onClick = { viewModel.setSection(section) },
                ),
            )
        }
    }

    /**
     * Construye un chip individual.
     *
     * @param text Texto a mostrar.
     * @param selected `true` si el chip debe estar seleccionado.
     * @param onClick Callback al pulsar el chip.
     * @return El chip configurado.
     */
    private fun chip(text: String, selected: Boolean, onClick: () -> Unit): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isCheckedIconVisible = true
            isChecked = selected
            setOnClickListener { onClick() }
        }

    /**
     * Enruta el click de un módulo a la pantalla correspondiente.
     *
     * ## Por qué el `when` es exhaustivo
     *
     * Porque `ModuleType` es un `sealed interface`. El compilador
     * obliga a contemplar todos los tipos. Si se añade uno nuevo, este
     * `when` dará error de compilación hasta que se actualice.
     *
     * @param moduleType Tipo de módulo pulsado.
     */
    private fun routeModule(moduleType: ModuleType) = when (moduleType) {
        is ModuleType.Assignment -> openAssignment(moduleType.module)
        is ModuleType.Resource -> openResource(moduleType.module)
        is ModuleType.Folder -> openFolder(moduleType.module)
        is ModuleType.Url -> openUrl(moduleType.module)
        is ModuleType.Forum -> openForum(moduleType.module)
        is ModuleType.Label -> Unit
        is ModuleType.Other -> openGeneric(moduleType.module)
    }

    /**
     * Navega al detalle de la tarea.
     *
     * @param module Módulo de tipo `Assignment`.
     */
    private fun openAssignment(module: CourseModule) {
        val courseId = arguments?.getInt("COURSE_ID") ?: 0
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                AssignmentDetailFragment.newInstance(courseId, module.instance),
            )
            addToBackStack(null)
        }
    }

    /**
     * Descarga un recurso de un solo archivo o abre la carpeta.
     *
     * ## Comportamiento
     *
     * - **Un solo archivo**: pide confirmación y descarga.
     * - **Varios archivos**: abre la pantalla de archivos.
     * - **Sin archivos**: muestra un toast de error.
     *
     * @param module Módulo de tipo `Resource`.
     */
    private fun openResource(module: CourseModule) = when {
        module.files.size == 1 -> confirmAndDownload(module.files.first())
        module.files.size > 1 -> openFolder(module)
        else -> showToast(R.string.error_resource_no_files)
    }

    /**
     * Navega a la pantalla de archivos del módulo.
     *
     * @param module Módulo con archivos.
     */
    private fun openFolder(module: CourseModule) {
        val courseId = arguments?.getInt("COURSE_ID") ?: 0
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                CourseFilesFragment.newInstance(
                    courseId = courseId,
                    moduleId = module.id,
                    moduleName = module.name,
                    moduleDescription = module.description,
                ),
            )
            addToBackStack(null)
        }
    }

    /**
     * Resuelve la URL externa de un módulo `url` y la abre con
     * validación.
     *
     * ## Por qué se valida la URL
     *
     * Porque las URLs de los módulos `url` las escribe el profesor,
     * y podrían tener un esquema peligroso (por ejemplo,
     * `javascript:`). `WebUrls.isSafeWebUrl` comprueba que sea
     * `http://` o `https://` antes de abrirla.
     *
     * @param module Módulo de tipo `Url`.
     */
    private fun openUrl(module: CourseModule) {
        viewLifecycleOwner.lifecycleScope.launch {
            val externalUrl = viewModel.getExternalUrl(module.instance)
            when {
                externalUrl.isNullOrBlank() -> showToast(R.string.error_open_url)
                !WebUrls.isSafeWebUrl(externalUrl) -> showToast(R.string.error_unsafe_url)
                else -> runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
                }.onFailure {
                    showToast(R.string.error_no_navigator)
                }
            }
        }
    }

    /**
     * Navega al listado de discusiones del foro.
     *
     * @param module Módulo de tipo `Forum`.
     */
    private fun openForum(module: CourseModule) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                ForumFragment.newInstance(module.instance, module.name),
            )
            addToBackStack(null)
        }
    }

    /**
     * Navega a la pantalla genérica de módulo.
     *
     * @param module Módulo de tipo `Other`.
     */
    private fun openGeneric(module: CourseModule) {
        parentFragmentManager.commit {
            replace(
                R.id.fragment_container,
                GenericModuleFragment.newInstance(module),
            )
            addToBackStack(null)
        }
    }

    /**
     * Pide confirmación y descarga un archivo único.
     *
     * ## Comportamiento
     *
     * - Si no hay sesión activa, no hace nada (guard semántico).
     * - Si hay sesión, muestra un diálogo de confirmación.
     *
     * @param file Archivo a descargar.
     */
    private fun confirmAndDownload(file: CourseFile) {
        if (sessionRepo.session.value == null) return
        FileDownloadHelper.showConfirmationDialog(requireContext(), file) {
            FileDownloadHelper.download(requireContext(), file)
        }
    }

    /**
     * Muestra un toast corto.
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