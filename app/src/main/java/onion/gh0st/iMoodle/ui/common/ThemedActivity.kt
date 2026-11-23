package onion.gh0st.iMoodle.ui.common

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.core.ThemePreferences

/**
 * Activity base que aplica la paleta de color elegida por el usuario.
 *
 * ## Qué hace
 *
 * 1. Lee la paleta guardada en [ThemePreferences] **antes** de que se
 *    cree la vista, porque el tema debe estar decidido desde el primer
 *    *layout*. Esperar a `onCreate` provocaría un parpadeo: la pantalla
 *    se dibujaría con el tema verde y cambiaría a continuación.
 * 2. Envuelve el contexto con un [ContextThemeWrapper] que ya trae el
 *    tema resuelto, para que cualquier vista creada fuera de la Activity
 *    (por ejemplo, un diálogo) use también la paleta correcta.
 * 3. Llama a `setTheme` antes de `super.onCreate`, que es el punto en el
 *    que Android empieza a inflar la interfaz.
 *
 * ## Relación con el modo claro/oscuro
 *
 * La paleta solo sustituye los colores de acento. El modo claro u
 * oscuro sigue controlado por DayNight: los overlays se definen por
 * igual en `values/themes.xml` y `values-night/themes.xml`.
 *
 * **Padre directo de**: `LoginActivity`, `MainActivity`,
 * `MoodleWebViewActivity` y `WebActivity`.
 */
abstract class ThemedActivity : AppCompatActivity() {

    /** Tema resuelto en [attachBaseContext], aplicado después en [onCreate]. */
    private var themeResId: Int = R.style.Theme_MiMoodleAndalucia

    /**
     * Resuelve la paleta guardada y prepara un contexto con su tema.
     *
     * Se ejecuta antes que [onCreate] y antes de que Hilt esté
     * disponible, por eso la preferencia se lee directamente con
     * [ThemePreferences] en lugar de inyectarla.
     *
     * @param newBase Contexto base que proporciona Android.
     */
    override fun attachBaseContext(newBase: Context) {
        val paletteId = ThemePreferences.getPaletteId(newBase)
        val palette = ThemePalette.fromId(paletteId)
        themeResId = if (palette.overlayResId != 0) {
            palette.overlayResId
        } else {
            R.style.Theme_MiMoodleAndalucia
        }
        val wrapped = ContextThemeWrapper(newBase, themeResId)
        super.attachBaseContext(wrapped)
    }

    /**
     * Aplica el tema resuelto antes de inflar la interfaz.
     *
     * @param savedInstanceState Estado guardado de la Activity, o `null`.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeResId)
        super.onCreate(savedInstanceState)
    }
}
