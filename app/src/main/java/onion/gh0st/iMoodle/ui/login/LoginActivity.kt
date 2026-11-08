package onion.gh0st.iMoodle.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.DeepLinkExtras
import onion.gh0st.iMoodle.domain.model.session.Provincia
import onion.gh0st.iMoodle.ui.main.MainActivity

/**
 * Pantalla de login con selección de provincia.
 *
 * ## Qué muestra
 *
 * - Un spinner para elegir la provincia.
 * - Un campo de texto para el usuario (NIF/NIE/IdEA).
 * - Un campo de texto para la contraseña (con botón de "ver/ocultar").
 * - Un botón de "Iniciar sesión".
 *
 * ## Cómo obtiene los datos
 *
 * Delega en `LoginViewModel`, que a su vez usa
 * `SessionRepository`. La Activity solo:
 *
 * 1. Configura los campos.
 * 2. Recoge los inputs del usuario.
 * 3. Observa el estado y los eventos del ViewModel.
 * 4. Navega a `MainActivity` al terminar.
 *
 * ## Auto-login
 *
 * Al construirse, el ViewModel intenta recuperar una sesión
 * guardada. Si la hay, navega directamente a `MainActivity` sin que
 * el usuario tenga que hacer nada.
 *
 * ## Sobre el deep-link
 *
 * Si `MainActivity` redirigió aquí porque el proceso murió al pulsar
 * una notificación, el `Intent` lleva los extras de deep-link
 * (`COURSE_ID` e `INSTANCE_ID`). Al navegar a `MainActivity` tras
 * el login, esos extras se reenvían con `DeepLinkExtras.copy` para
 * no perder el destino original.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    /** Campo de texto del usuario. */
    private lateinit var inpUser: EditText

    /** Campo de texto de la contraseña. */
    private lateinit var inpPass: EditText

    /** Spinner de provincias. */
    private lateinit var spinnerCentro: Spinner

    /** Botón de login. */
    private lateinit var btnLogin: Button

    /** Botón de "ver/ocultar" contraseña. */
    private lateinit var btnTogglePass: ImageView

    /** ViewModel de la pantalla. */
    private val viewModel: LoginViewModel by viewModels()

    /** Recuerda si la contraseña está visible o no. */
    private var isPasswordVisible = false

    /**
     * Ciclo de vida de la Activity.
     *
     * ## Qué hace paso a paso
     *
     * 1. Activa el modo "edge-to-edge" (dibujar detrás de las barras
     *    del sistema).
     * 2. Infla el layout.
     * 3. Aplica los insets de barras del sistema.
     * 4. Enlaza las vistas.
     * 5. Configura el spinner de provincias.
     * 6. Configura el toggle de contraseña.
     * 7. Configura los listeners de los campos.
     * 8. Observa el ViewModel.
     *
     * @param savedInstanceState Estado previo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        setupWindowInsets()
        bindViews()
        setupSpinner()
        setupPasswordToggle()
        setupListeners()
        observeViewModel()
    }

    /**
     * Aplica padding de barras del sistema al layout raíz.
     *
     * ## Qué hace
     *
     * Guarda los paddings originales del layout (los que vienen del
     * XML) y les suma los insets del sistema. Así el contenido no se
     * dibuja debajo de las barras, pero tampoco pierde el padding que
     * ya tenía.
     */
    private fun setupWindowInsets() {
        val mainLayout = findViewById<View>(R.id.lyt_main)
        val originalPaddingLeft = mainLayout.paddingLeft
        val originalPaddingTop = mainLayout.paddingTop
        val originalPaddingRight = mainLayout.paddingRight
        val originalPaddingBottom = mainLayout.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                originalPaddingLeft + systemBars.left,
                originalPaddingTop + systemBars.top,
                originalPaddingRight + systemBars.right,
                originalPaddingBottom + systemBars.bottom,
            )
            insets
        }
    }

    /**
     * Enlaza las vistas y configura el listener del botón de login.
     *
     * ## Comportamiento del botón
     *
     * 1. Lee la posición seleccionada del spinner.
     * 2. Si es 0 ("Selecciona provincia"), muestra un toast y no
     *    hace nada.
     * 3. Si es una provincia válida, oculta el teclado y delega el
     *    login en el ViewModel.
     *
     * ## Por qué se resta 1 al índice del spinner
     *
     * Porque el ítem 0 del spinner es el placeholder ("Selecciona
     * provincia"), y las provincias empiezan en el índice 1. La
     * conversión a `Provincia` se hace restando 1.
     */
    private fun bindViews() {
        inpUser = findViewById(R.id.inp_user)
        inpPass = findViewById(R.id.inp_password)
        spinnerCentro = findViewById(R.id.spinner_centro)
        btnLogin = findViewById(R.id.btn_login)
        btnTogglePass = findViewById(R.id.btn_toggle_pass)

        btnLogin.setOnClickListener {
            val selectedPosition = spinnerCentro.selectedItemPosition
            if (selectedPosition == 0) {
                Toast.makeText(
                    this,
                    getString(R.string.hint_select_province),
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }
            val provincia = Provincia.entries[selectedPosition - 1]
            hideKeyboard()
            viewModel.login(
                provincia = provincia,
                user = inpUser.text.toString().trim(),
                pass = inpPass.text.toString(),
            )
        }
    }

    /**
     * Configura el spinner de provincias.
     *
     * ## Qué hace
     *
     * 1. Construye la lista de opciones: el placeholder + las ocho
     *    provincias.
     * 2. Configura el adapter del spinner.
     * 3. Configura un listener que notifica al ViewModel cuando
     *    cambia la selección.
     * 4. Colorea los ítems del spinner manualmente (algunos temas de
     *    Android ignoran el color de texto del adapter en el ítem
     *    seleccionado).
     */
    private fun setupSpinner() {
        val provinceOptions = mutableListOf<String>()
        provinceOptions.add(getString(R.string.hint_select_province))
        provinceOptions.addAll(Provincia.entries.map { provincia -> provincia.displayName })

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            provinceOptions,
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCentro.adapter = spinnerAdapter
        spinnerCentro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                viewModel.onProvinceSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerCentro.post {
            (0 until spinnerCentro.childCount).forEach { childIndex ->
                (spinnerCentro.getChildAt(childIndex) as? TextView)
                    ?.setTextColor(ContextCompat.getColor(this, R.color.texto_principal))
            }
        }
    }

    /**
     * Alterna la visibilidad de la contraseña.
     *
     * ## Qué hace
     *
     * 1. Cambia el `transformationMethod` del `EditText` entre
     *    "texto normal" y "texto oculto" (`PasswordTransformationMethod`).
     * 2. Cambia el icono del botón.
     * 3. Mantiene el cursor al final del texto (para no perder la
     *    posición tras el cambio).
     */
    private fun setupPasswordToggle() {
        btnTogglePass.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                inpPass.transformationMethod = null
                btnTogglePass.setImageResource(R.drawable.ic_visibility)
            } else {
                inpPass.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePass.setImageResource(R.drawable.ic_visibility_off)
            }
            inpPass.setSelection(inpPass.text.length)
        }
    }

    /**
     * Añade el `TextWatcher` compartido a ambos inputs.
     *
     * ## Qué hace
     *
     * En cada pulsación, notifica al ViewModel con el estado actual
     * de ambos campos. El ViewModel decide si el botón debe
     * habilitarse (ambos con texto).
     *
     * ## Por qué se reutiliza el mismo watcher para los dos
     *
     * Porque la lógica es idéntica. Crear dos instancias sería
     * duplicar código sin aportar nada.
     */
    private fun setupListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                charSequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                viewModel.onInputChanged(inpUser.text.toString(), inpPass.text.toString())
            }

            override fun afterTextChanged(editable: Editable?) {}
        }
        inpUser.addTextChangedListener(textWatcher)
        inpPass.addTextChangedListener(textWatcher)
    }

    /**
     * Observa el estado y los eventos del ViewModel en paralelo.
     *
     * Lanza dos corrutinas dentro de `repeatOnLifecycle(STARTED)`:
     * una para el estado, otra para los eventos. Se pausan cuando la
     * Activity no está visible.
     */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeUiState() }
                launch { observeEvents() }
            }
        }
    }

    /**
     * Refleja el `LoginUiState` en las vistas.
     *
     * ## Qué actualiza
     *
     * - **Estado del botón**: habilitado o no.
     * - **Alpha del botón**: 0.5 o 1.0.
     * - **Texto del botón**: "Iniciar sesión" o "Conectando…".
     * - **Posición del spinner**: solo si difiere, para no disparar
     *   el `onItemSelectedListener` en bucle.
     */
    private suspend fun observeUiState() {
        viewModel.uiState.collect { state ->
            btnLogin.isEnabled = state.isButtonEnabled
            btnLogin.alpha = state.buttonAlpha
            btnLogin.setText(state.buttonTextResId)
            if (spinnerCentro.selectedItemPosition != state.selectedCentroIndex) {
                spinnerCentro.setSelection(state.selectedCentroIndex)
            }
        }
    }

    /**
     * Consume los eventos de un solo uso del ViewModel.
     *
     * - `ShowToast` → muestra el toast con el mensaje.
     * - `NavigateToMain` → navega a `MainActivity`.
     */
    private suspend fun observeEvents() {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.ShowToast -> showToast(event.message)
                LoginEvent.NavigateToMain -> navigateToMain()
            }
        }
    }

    /**
     * Muestra un toast de larga duración.
     *
     * @param text Texto a mostrar.
     */
    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /**
     * Navega a `MainActivity` cerrando la pila de login.
     *
     * ## Sobre el deep-link
     *
     * Si `MainActivity` nos mandó aquí (proceso muerto al pulsar una
     * notificación), reenvía el deep-link con `DeepLinkExtras.copy`
     * para no perder el destino.
     */
    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java)
        DeepLinkExtras.copy(from = intent, to = mainIntent)
        startActivity(mainIntent)
        finish()
    }

    /**
     * Oculta el teclado virtual.
     *
     * Se llama antes de lanzar el login para que el usuario no vea
     * el teclado mientras espera la respuesta de la red.
     */
    private fun hideKeyboard() {
        val inputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}