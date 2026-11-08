package onion.gh0st.iMoodle.ui.courses.detail

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.FragmentGenericModuleBinding
import onion.gh0st.iMoodle.domain.model.course.CourseModule
import onion.gh0st.iMoodle.ui.common.HtmlUtils

/**
 * Fragment de "fallback" para módulos sin pantalla específica.
 *
 * ## Qué es un módulo "genérico"
 *
 * Es cualquier módulo de Moodle que no tiene una pantalla dedicada
 * en la app. Por ejemplo:
 *
 * - **Cuestionarios** (`quiz`).
 * - **Wikis** (`wiki`).
 * - **Libros** (`book`).
 * - **Páginas** (`page`).
 *
 * Moodle los soporta, pero la app no tiene una UI específica para
 * ellos. En lugar de no mostrarlos, se muestran con esta pantalla
 * genérica que solo dice el nombre y la descripción.
 *
 * ## Quién decide que un módulo es "genérico"
 *
 * `CourseDetailFragment.routeModule`, cuando el `ModuleType` es
 * `Other`. En ese momento, navega a este Fragment.
 *
 * ## Qué muestra
 *
 * - **Toolbar**: con un título genérico ("Módulo").
 * - **Nombre del módulo**: en el cuerpo.
 * - **Descripción**: si la tiene, limpiada de HTML. Si no,
 *   muestra un texto genérico.
 *
 * ## Qué NO hace
 *
 * No permite ninguna acción sobre el módulo. No se puede abrir el
 * cuestionario, ni ver la wiki, ni nada. Solo se muestra la
 * información.
 */
class GenericModuleFragment : Fragment(R.layout.fragment_generic_module) {

    /** Nombre del módulo, recibido como argumento. */
    private var moduleName: String = ""

    /** Descripción del módulo, recibida como argumento. */
    private var moduleDescription: String = ""

    companion object {

        /**
         * Construye una instancia del Fragment con los datos del
         * módulo.
         *
         * ## Qué argumentos recibe
         *
         * Solo nombre y descripción. El resto de campos de
         * `CourseModule` (id, instance, modname, url, files) no se
         * usan en esta pantalla.
         *
         * @param module Módulo crudo de Moodle.
         * @return El Fragment listo para añadir al `FragmentManager`.
         */
        fun newInstance(module: CourseModule): GenericModuleFragment =
            GenericModuleFragment().apply {
                arguments = Bundle().apply {
                    putString("MODULE_NAME", module.name)
                    putString("MODULE_DESCRIPTION", module.description)
                }
            }
    }

    /**
     * Ciclo de vida de creación del Fragment.
     *
     * Recupera el nombre y la descripción de los argumentos. Se hace
     * en `onCreate` (no en `onViewCreated`) para que sobrevivan a una
     * recreación de la vista.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleName = arguments?.getString("MODULE_NAME") ?: ""
        moduleDescription = arguments?.getString("MODULE_DESCRIPTION") ?: ""
    }

    /**
     * Ciclo de vida de la vista.
     *
     * @param view Vista raíz inflada.
     * @param savedInstanceState Estado previo.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGenericModuleBinding.bind(view)
        setupToolbar(binding)
        setupWindowInsets(binding)
        renderContent(binding)
    }

    /**
     * Configura el toolbar con título genérico y botón atrás.
     *
     * ## Por qué el título es genérico
     *
     * Porque esta pantalla es un fallback: el nombre real del módulo
     * se muestra en el cuerpo, no en el toolbar. Usar el nombre real
     * en el toolbar sería engañoso (parecería que hay una pantalla
     * específica para ese tipo de módulo).
     *
     * @param binding Binding de la vista.
     */
    private fun setupToolbar(binding: FragmentGenericModuleBinding) {
        binding.toolbarGeneric.title = getString(R.string.title_generic_module)
        binding.toolbarGeneric.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Aplica los insets de barras del sistema al `AppBarLayout`.
     *
     * @param binding Binding de la vista.
     */
    private fun setupWindowInsets(binding: FragmentGenericModuleBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarGeneric) { view, insets ->
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
     * Rellena nombre y descripción.
     *
     * ## Comportamiento
     *
     * - **Nombre**: siempre se muestra.
     * - **Descripción**: si está vacía, se muestra un texto genérico
     *   ("Sin descripción"). Si no, se limpia de HTML con
     *   `HtmlUtils.stripHtml` (porque esta pantalla no preserva
     *   estructura: no tiene sentido para quiz/wiki/book/page, que
     *   ya tienen su propia UI en Moodle web).
     *
     * @param binding Binding de la vista.
     */
    private fun renderContent(binding: FragmentGenericModuleBinding) {
        binding.txtGenericName.text = moduleName
        binding.txtGenericDescription.text = if (moduleDescription.isBlank()) {
            getString(R.string.label_no_description_module)
        } else {
            HtmlUtils.stripHtml(moduleDescription)
        }
    }
}