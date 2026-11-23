package onion.gh0st.iMoodle.ui.common

import onion.gh0st.iMoodle.R

/**
 * Paletas de color disponibles para la app.
 *
 * La paleta solo cambia el color de acento; el modo claro/oscuro sigue
 * controlado por el sistema (DayNight). La primera ([VERDE_JUNTA]) es
 * la paleta original y no aplica ningún overlay (overlayResId = 0).
 *
 * @property id Identificador persistido en [onion.gh0st.iMoodle.core.ThemePreferences].
 * @property displayNameRes Recurso de texto con el nombre visible.
 * @property previewColorRes Recurso de color para la vista previa (círculo).
 * @property overlayResId Estilo overlay que aplica la paleta, o 0 si es la
 *   paleta por defecto (no hace falta overlay).
 */
enum class ThemePalette(
    val id: String,
    val displayNameRes: Int,
    val previewColorRes: Int,
    val overlayResId: Int,
) {
    VERDE_JUNTA(
        id = "verde_junta",
        displayNameRes = R.string.theme_palette_verde_junta,
        previewColorRes = R.color.color_acento,
        overlayResId = 0,
    ),
    AZUL(
        id = "azul",
        displayNameRes = R.string.theme_palette_azul,
        previewColorRes = R.color.palette_azul,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Azul,
    ),
    MORADO(
        id = "morado",
        displayNameRes = R.string.theme_palette_morado,
        previewColorRes = R.color.palette_morado,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Morado,
    ),
    NARANJA(
        id = "naranja",
        displayNameRes = R.string.theme_palette_naranja,
        previewColorRes = R.color.palette_naranja,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Naranja,
    ),
    ROJO(
        id = "rojo",
        displayNameRes = R.string.theme_palette_rojo,
        previewColorRes = R.color.palette_rojo,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Rojo,
    ),
    TEAL(
        id = "teal",
        displayNameRes = R.string.theme_palette_teal,
        previewColorRes = R.color.palette_teal,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Teal,
    ),
    ROSA(
        id = "rosa",
        displayNameRes = R.string.theme_palette_rosa,
        previewColorRes = R.color.palette_rosa,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Rosa,
    ),
    INDIGO(
        id = "indigo",
        displayNameRes = R.string.theme_palette_indigo,
        previewColorRes = R.color.palette_indigo,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Indigo,
    ),
    AMBAR(
        id = "ambar",
        displayNameRes = R.string.theme_palette_ambar,
        previewColorRes = R.color.palette_ambar,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Ambar,
    ),
    CIAN(
        id = "cian",
        displayNameRes = R.string.theme_palette_cian,
        previewColorRes = R.color.palette_cian,
        overlayResId = R.style.ThemeOverlay_MiMoodleAndalucia_Cian,
    );

    companion object {

        /**
         * Devuelve la paleta cuyo [id] coincide, o [VERDE_JUNTA] si no
         * existe.
         *
         * @param id Identificador persistido.
         * @return Paleta correspondiente.
         */
        fun fromId(id: String): ThemePalette =
            entries.firstOrNull { it.id == id } ?: VERDE_JUNTA
    }
}