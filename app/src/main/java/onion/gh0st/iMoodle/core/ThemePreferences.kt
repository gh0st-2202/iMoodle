package onion.gh0st.iMoodle.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias de apariencia de la app.
 *
 * Guarda la paleta de color elegida por el usuario en un
 * `SharedPreferences` privado (no sensible, sin cifrar). Se lee desde
 * `ThemedActivity.attachBaseContext`, antes de que Hilt esté disponible,
 * por eso se accede directamente con el nombre del fichero.
 *
 * ## Dos claves, dos momentos
 *
 * - [KEY_PALETTE]: paleta activa; cambia los colores al instante.
 * - [KEY_PENDING_PALETTE]: icono pendiente; se aplica cuando la app pasa
 *   a segundo plano, para no cerrar la app justo después de elegir un
 *   color (ver `AppIconManager.applyPendingIcon`).
 */
object ThemePreferences {

    /** Nombre del fichero de preferencias. */
    private const val PREFERENCES_FILE_NAME = "app_settings"

    /** Clave de la paleta seleccionada. */
    private const val KEY_PALETTE = "theme_palette"

    /** Clave del cambio de icono pendiente. */
    private const val KEY_PENDING_PALETTE = "pending_launcher_palette"

    /** Paleta por defecto (verde Junta). */
    const val DEFAULT_PALETTE = "verde_junta"

    /**
     * Lee la paleta guardada.
     *
     * @param context Contexto para acceder a las preferencias.
     * @return Id de la paleta, o [DEFAULT_PALETTE] si no hay ninguna.
     */
    fun getPaletteId(context: Context): String =
        preferences(context).getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE

    /**
     * Guarda la paleta elegida.
     *
     * @param context Contexto para acceder a las preferencias.
     * @param paletteId Id de la paleta.
     */
    fun setPaletteId(context: Context, paletteId: String) {
        preferences(context).edit().putString(KEY_PALETTE, paletteId).apply()
    }

    /**
     * Guarda el icono que debe aplicarse cuando la app pase a segundo plano.
     *
     * @param context Contexto para acceder a las preferencias.
     * @param paletteId Id de la paleta cuyo icono se activará después.
     */
    fun setPendingPaletteId(context: Context, paletteId: String) {
        preferences(context).edit().putString(KEY_PENDING_PALETTE, paletteId).apply()
    }

    /**
     * Devuelve el id del icono pendiente, o `null` si no hay ninguno.
     *
     * @param context Contexto para acceder a las preferencias.
     * @return Id de la paleta pendiente, o `null`.
     */
    fun getPendingPaletteId(context: Context): String? =
        preferences(context).getString(KEY_PENDING_PALETTE, null)

    /**
     * Borra el icono pendiente una vez aplicado.
     *
     * @param context Contexto para acceder a las preferencias.
     */
    fun clearPendingPalette(context: Context) {
        preferences(context).edit().remove(KEY_PENDING_PALETTE).apply()
    }

    /**
     * Abre el fichero de preferencias de apariencia.
     *
     * @param context Contexto para acceder a las preferencias.
     * @return Instancia privada de `SharedPreferences`.
     */
    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
}
