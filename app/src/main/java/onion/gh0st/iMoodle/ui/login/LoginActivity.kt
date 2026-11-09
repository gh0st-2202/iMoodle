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
 * Pantalla de inicio de sesión.
 *
 * ## ¿Qué ves en pantalla?
 * - Un **desplegable** para elegir la provincia.
 * - Un **campo de texto** para el usuario (NIF/NIE/IdEA).
 * - Un **campo de texto** para la contraseña, con un ojo 👁️ que
 *   permite verla u ocultarla.
 * - Un **botón** "Iniciar sesión".
 *
 * ## ¿Quién hace el trabajo de verdad?
 * Esta pantalla es solo "la cara". La lógica real (comprobar
 * credenciales, hablar con los servidores de la Junta, guardar la
 * sesión) está en `LoginViewModel`. La Activity se limita a:
 * 1. Recoger lo que el usuario escribe.
 * 2. Avisar al ViewModel: "el usuario ha pulsado el botón".
 * 3. Escuchar al ViewModel: "el login ha ido bien" o "ha fallado
 *    por esto".
 * 4. Cambiar de pantalla cuando toca.
 *
 * ## Auto-login
 * Cuando la app arranca, esta pantalla le pregunta al ViewModel:
 * "¿hay una sesión guardada de antes?". Si la hay, salta
 * directamente a la pantalla principal sin que el usuario tenga
 * que escribir nada.
 *
 * ## ¿Y si venías de una notificación?
 * Si el móvil cerró la app mientras pulsabas una notificación de
 * tarea, `MainActivity` te manda aquí para que vuelvas a
 * identificarte. Pero se lleva consigo la "nota" (ver
 * `DeepLinkExtras`) para no olvidar a qué tarea querías ir. Tras
 * el login, esta pantalla reenvía esa nota a la pantalla principal
 * para que se abra la tarea correcta.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    /** Campo de texto donde se escribe el usuario. */
    private lateinit var inpUser: EditText

    /** Campo de texto donde se escribe la contraseña. */
    private lateinit var inpPass: EditText

    /** Desplegable para elegir la provincia. */
    private lateinit var spinnerCentro: Spinner

    /** Botón de "Iniciar sesión". */
    private lateinit var btnLogin: Button

    /** Botón con forma de ojo 👁️ para ver u ocultar la contraseña. */
    private lateinit var btnTogglePass: ImageView

    /** ViewModel que contiene la lógica real del login. */
    private val viewModel: LoginViewModel by viewModels()

    /**
     * Recuerda si la contraseña está visible o no.
     * Empieza en `false` (oculta), porque es lo más seguro por
     * defecto.
     */
    private var isPasswordVisible = false

    /**
     * Punto de entrada de la pantalla.
     *
     * ## Qué hace paso a paso
     * 1. Prepara la ventana para que se dibuje también debajo de las
     *    barras del sistema (modo "edge-to-edge").
     * 2. Carga el diseño de la pantalla (el XML).
     * 3. Ajusta los márgenes para que el contenido no quede tapado
     *    por las barras del móvil.
     * 4. Enlaza los elementos del diseño con variables del código.
     * 5. Rellena el desplegable de provincias.
     * 6. Configura el ojo 👁️ de la contraseña.
     * 7. Configura los campos de texto para detectar lo que escribe
     *    el usuario.
     * 8. Empieza a escuchar al ViewModel.
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
     * Ajusta los márgenes para que el contenido respete las barras
     * del sistema (la de estado arriba y la de navegación abajo).
     *
     * ## ¿Por qué hace falta?
     * En el modo "edge-to-edge" la app dibuja **por debajo** de las
     * barras del móvil. Eso queda bonito, pero el texto se metería
     * debajo de la barra de estado. Esta función suma al margen
     * original el alto de las barras para que el contenido siempre
     * quede visible.
     *
     * ## Cómo funciona
     * 1. Guarda los márgenes originales del diseño (los que vienen
     *    del XML).
     * 2. Se pone a "escuchar" los cambios en las barras del sistema
     *    (aparecer, desaparecer, cambiar de tamaño).
     * 3. Cada vez que cambian, vuelve a calcular los márgenes
     *    sumando los originales + el tamaño de las barras.
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
     * Enlaza los elementos del diseño con variables del código y
     * prepara el botón de login.
     *
     * ## Comportamiento del botón
     * Cuando el usuario pulsa "Iniciar sesión":
     * 1. Se mira qué provincia está seleccionada en el desplegable.
     * 2. Si es la opción 0 ("Selecciona provincia"), se muestra un
     *    aviso por pantalla y **no se hace nada más**. No se puede
     *    iniciar sesión sin provincia.
     * 3. Si hay una provincia válida, se oculta el teclado (para
     *    que no estorbe mientras se conecta) y se le pasa la
     *    petición al ViewModel con la provincia, el usuario y la
     *    contraseña.
     *
     * ## ¿Por qué se resta 1 a la posición del desplegable?
     * Porque la opción 0 es el texto "Selecciona provincia", y las
     * provincias de verdad empiezan en la posición 1. El código
     * `Provincia.entries[selectedPosition - 1]` coge la provincia
     * correcta.
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
     * Rellena el desplegable de provincias.
     *
     * ## Qué hace paso a paso
     * 1. Crea una lista de opciones: primero el texto "Selecciona
     *    provincia", y detrás las ocho provincias andaluzas.
     * 2. Le dice al desplegable que use esa lista como opciones.
     * 3. Configura un "aviso" para que cada vez que el usuario
     *    elija una provincia, se lo comunique al ViewModel.
     * 4. Pinta los textos del desplegable con el color correcto.
     *    (Algunos temas de Android ignoran el color configurado
     *    por el adapter y hay que forzarlo a mano; por eso el
     *    `post { ... }` que recorre los hijos del desplegable y
     *    les cambia el color).
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
     * Configura el ojo 👁️ para mostrar u ocultar la contraseña.
     *
     * ## Qué hace
     * Cada vez que pulsas el ojo:
     * 1. Cambia entre "texto normal" y "texto oculto" (puntos
     *    negros, como en cualquier campo de contraseña).
     * 2. Cambia el dibujo del botón: un ojo abierto o un ojo tachado.
     * 3. Coloca el cursor al final del texto para que el usuario no
     *    pierda la posición donde estaba escribiendo.
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
     * Avisa al ViewModel cada vez que el usuario escribe en los
     * campos de usuario o contraseña.
     *
     * ## ¿Para qué?
     * Para que el botón "Iniciar sesión" solo se pueda pulsar
     * cuando ambos campos tienen texto. Si cualquiera está vacío,
     * el botón se queda gris y deshabilitado. Esto evita que el
     * usuario pulse "Iniciar sesión" sin haber escrito nada.
     *
     * ## Cómo
     * Se crea un "espía" (`TextWatcher`) que se coloca en los dos
     * campos. Cada vez que cambia el texto de cualquiera de ellos,
     * avisa al ViewModel con el contenido actual de los dos.
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
     * Empieza a escuchar lo que dice el ViewModel.
     *
     * ## Qué hace
     * Lanza dos "tareas en paralelo":
     * - Una **escucha el estado** (¿el botón está habilitado? ¿qué
     *   texto muestra?).
     * - Otra **escucha los eventos** (avisos: "muestra este toast",
     *   "navega a la pantalla principal").
     *
     * Estas tareas se pausan cuando la pantalla deja de estar
     * visible, para no malgastar batería.
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
     * Refleja el estado del ViewModel en los elementos visuales.
     *
     * ## Qué actualiza
     * - **¿El botón está habilitado?** (verdadero o falso).
     * - **Transparencia del botón**: 0.5 si está deshabilitado, 1.0
     *   si está habilitado.
     * - **Texto del botón**: "Iniciar sesión" o "Conectando…".
     * - **Provincia seleccionada**: solo cambia si de verdad ha
     *   cambiado (para no disparar el aviso de "cambio de provincia"
     *   en bucle).
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
     * Reacciona a los avisos del ViewModel.
     *
     * - **"Muestra este mensaje"**: lanza un toast con el texto.
     * - **"El login ha ido bien"**: navega a la pantalla principal.
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
     * Muestra un mensaje breve en pantalla durante unos segundos.
     *
     * @param text El texto a mostrar.
     */
    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /**
     * Salta a la pantalla principal (`MainActivity`) y cierra esta.
     *
     * ## Sobre la "nota" de la notificación
     * Si esta pantalla se abrió porque el móvil cerró la app mientras
     * pulsabas una notificación, llevamos encima la "nota" (ver
     * `DeepLinkExtras`). Antes de saltar, se copia esa nota al nuevo
     * sobre para que la pantalla principal sepa a qué tarea debe ir.
     *
     * ## Sobre `destination`
     * El parámetro se llama `destination` (no `to`) para no chocar
     * con una palabra especial de Kotlin (ver explicación en
     * `DeepLinkExtras.copy`).
     */
    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java)
        DeepLinkExtras.copy(
            from = intent,
            destination = mainIntent,
        )
        startActivity(mainIntent)
        finish()
    }

    /**
     * Oculta el teclado virtual.
     *
     * Se llama justo antes de iniciar sesión, para que el usuario no
     * vea el teclado ocupando media pantalla mientras la app habla
     * con el servidor.
     */
    private fun hideKeyboard() {
        val inputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}