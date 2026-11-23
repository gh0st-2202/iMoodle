package onion.gh0st.iMoodle.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import onion.gh0st.iMoodle.ui.common.ThemePalette

/**
 * Gestiona el alias del launcher que determina el icono de la app.
 *
 * Android no permite cambiar el icono de una app instalada sin crear
 * accesos directos nuevos salvo mediante `<activity-alias>`. Cada
 * paleta tiene un alias con su propio icono; solo uno debe estar
 * activo para no duplicar iconos en el cajón de aplicaciones.
 */
object AppIconManager {

    /** Sufijo del alias de cada paleta (dentro del package de la app). */
    private val aliasSuffix = mapOf(
        ThemePalette.VERDE_JUNTA to ".LauncherGreen",
        ThemePalette.AZUL to ".LauncherBlue",
        ThemePalette.MORADO to ".LauncherPurple",
        ThemePalette.NARANJA to ".LauncherOrange",
        ThemePalette.ROJO to ".LauncherRed",
        ThemePalette.TEAL to ".LauncherTeal",
        ThemePalette.ROSA to ".LauncherPink",
        ThemePalette.INDIGO to ".LauncherIndigo",
        ThemePalette.AMBAR to ".LauncherAmber",
        ThemePalette.CIAN to ".LauncherCyan",
    )

    /**
     * Activa el alias de [palette] y desactiva el resto.
     *
     * El alias nuevo se activa **antes** de desactivar los demás para
     * que el launcher no llegue a quedarse sin acceso directo (eso
     * movería el icono al final del cajón de aplicaciones).
     *
     * @param context Contexto usado para consultar el `PackageManager`.
     * @param palette Paleta cuyo icono se quiere mostrar.
     */
    fun setIcon(context: Context, palette: ThemePalette) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val selectedAlias = aliasSuffix.getValue(palette)
        val target = ComponentName(packageName, packageName + selectedAlias)

        if (packageManager.getComponentEnabledSetting(target) !=
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            packageManager.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        aliasSuffix.forEach { (candidate, suffix) ->
            if (candidate == palette) return@forEach
            val component = ComponentName(packageName, packageName + suffix)
            if (packageManager.getComponentEnabledSetting(component) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    /**
     * Sincroniza el icono con la paleta guardada al arrancar la app.
     *
     * Si el proceso murió antes de que la app pasara a segundo plano,
     * aquí se aplica el icono pendiente: por eso se limpia esa clave
     * justo después, para no repetir el cambio al volver al escritorio.
     *
     * @param context Contexto de la aplicación.
     */
    fun syncSavedIcon(context: Context) {
        setIcon(context, ThemePalette.fromId(ThemePreferences.getPaletteId(context)))
        ThemePreferences.clearPendingPalette(context)
    }

    /**
     * Aplica el icono pendiente cuando la app pasa a segundo plano.
     *
     * Cambiar el alias activo hace que Android reconstruya la caché del
     * launcher y, en algunos fabricantes, cierre el proceso. Hacerlo en
     * este momento evita que el usuario vea cómo se cierra la app justo
     * después de elegir un color: los colores ya cambiaron al instante y
     * el icono se actualiza mientras va al escritorio.
     *
     * @param context Contexto de la aplicación.
     */
    fun applyPendingIcon(context: Context) {
        val paletteId = ThemePreferences.getPendingPaletteId(context) ?: return
        ThemePreferences.clearPendingPalette(context)
        setIcon(context, ThemePalette.fromId(paletteId))
    }
}
