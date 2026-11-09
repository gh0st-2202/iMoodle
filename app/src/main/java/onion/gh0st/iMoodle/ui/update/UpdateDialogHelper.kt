package onion.gh0st.iMoodle.ui.update

import android.content.Context
import android.content.res.ColorStateList
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import onion.gh0st.iMoodle.R
import onion.gh0st.iMoodle.databinding.DialogUpdateBinding
import onion.gh0st.iMoodle.domain.model.update.AppUpdate

/**
 * Ayudante para mostrar el diálogo de actualización.
 *
 * ## ¿Qué es un "diálogo"?
 * Es una ventana flotante que aparece encima de la pantalla y que
 * el usuario tiene que cerrar (o responder) para seguir usando la
 * app. En este caso, aparece cuando hay una versión nueva de la app
 * disponible.
 *
 * ## ¿Qué muestra?
 * La información de la nueva versión, **respetando el formato
 * Markdown** que se usa en GitHub en los Releases. Eso significa
 * que si el changelog tiene títulos, negritas, listas o enlaces,
 * se verán formateados como en la web de GitHub, no como texto
 * plano con asteriscos y almohadillas.
 *
 * ## ¿Qué es Markdown?
 * Es un lenguaje de marcas muy sencillo para dar formato al texto.
 * Por ejemplo, `**texto**` se ve como **texto**, y `# Título` se
 * ve como un título grande. GitHub lo usa en los Releases para
 * escribir los changelogs de forma legible.
 *
 * ## Sobre el tamaño del diálogo
 * El ancho se fija al 90% de la pantalla para que el texto se lea
 * cómodamente. El alto se ajusta al contenido (`WRAP_CONTENT`), de
 * modo que si el changelog es corto el diálogo será pequeño, y si
 * es largo, el `TextView` con `maxHeight` hará scroll y el diálogo
 * no crecerá más allá de ese límite.
 *
 * ## Sobre el redondeo de las esquinas
 * Se aplica un fondo con forma redondeada a la ventana del diálogo
 * usando `MaterialShapeDrawable`, leyendo el color del tema actual.
 * (Antes se intentó con un `ThemeOverlay`, pero provocaba un
 * *crash*: `MaterialAlertDialogBuilder` no acepta un "overlay" de
 * tema, necesita un tema completo.)
 */
object UpdateDialogHelper {

    /**
     * Construye y muestra el diálogo de actualización.
     *
     * ## Cómo funciona paso a paso
     * 1. **Prepara el contenido** del diálogo: infla el layout
     *    `dialog_update.xml` (el diseño del diálogo) y crea un
     *    "renderizador de Markdown" con `Markwon`.
     * 2. **Pinta el changelog** en el `TextView` con el formato
     *    Markdown aplicado. Además, activa el
     *    `LinkMovementMethod` para que los enlaces que aparezcan en
     *    el changelog sean clicables.
     * 3. **Construye el diálogo** con `MaterialAlertDialogBuilder`:
     *    - Título: "Actualización disponible: vX.Y".
     *    - Contenido: el layout ya preparado.
     *    - No cancelable: el usuario tiene que elegir entre
     *      "Descargar" o "Más tarde" (no puede cerrarlo tocando
     *      fuera).
     *    - Botón "Descargar": lanza la descarga en segundo plano y
     *      abre el instalador.
     *    - Botón "Más tarde": cierra el diálogo sin hacer nada.
     * 4. **Aplica los retoques visuales** cuando el diálogo está a
     *    punto de mostrarse (esquinas redondeadas y ancho
     *    limitado).
     * 5. **Lo muestra**.
     *
     * ## ¿Por qué los retoques se aplican en `setOnShowListener`?
     * Porque la "ventana" del diálogo (el `window`) todavía no
     * existe cuando se construye el diálogo; solo existe después de
     * llamar a `show()`. Al ponerlo en un listener que se dispara
     * justo antes de mostrarse, nos aseguramos de que la ventana
     * existe y podemos modificarla.
     *
     * @param context El contexto de la pantalla donde se muestra el
     *   diálogo.
     * @param update Los datos de la actualización disponible
     *   (versión, changelog, URL de descarga…).
     * @param coroutineScope El ámbito de corrutinas para lanzar la
     *   descarga sin bloquear la interfaz.
     */
    fun showUpdateDialog(
        context: Context,
        update: AppUpdate,
        coroutineScope: CoroutineScope,
    ) {
        val binding = DialogUpdateBinding.inflate(LayoutInflater.from(context))
        val markwon = Markwon.create(context)

        markwon.setMarkdown(binding.txtChangelog, update.changelog)
        binding.txtChangelog.movementMethod = LinkMovementMethod.getInstance()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.update_dialog_title, update.versionName))
            .setView(binding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                coroutineScope.launch {
                    ApkInstaller.downloadAndInstall(context, update.downloadUrl)
                }
            }
            .setNegativeButton(R.string.update_action_later, null)
            .create()

        dialog.setOnShowListener {
            applyRoundedCornersToWindow(context, dialog)
            limitWindowSize(context, dialog)
        }
        dialog.show()
    }

    /**
     * Aplica un fondo con esquinas redondeadas a la ventana del
     * diálogo, leyendo el color de superficie del tema actual.
     *
     * ## ¿Qué es "el color de superficie"?
     * Es el color de fondo que el tema (claro u oscuro) usa para
     * los elementos que "flotan" sobre la pantalla (como los
     * diálogos). Al leerlo del tema, el diálogo se ve coherente
     * con el resto de la app en modo claro y en modo oscuro.
     *
     * ## Cómo funciona paso a paso
     * 1. Lee el radio de las esquinas del diálogo desde
     *    `dimens.xml` (así se puede cambiar en un solo sitio).
     * 2. Lee el color de superficie del tema actual con
     *    `MaterialColors.getColor`. Si por algún motivo no lo
     *    encuentra, usa blanco como último recurso.
     * 3. Construye un `MaterialShapeDrawable`: una "forma" con
     *    todas las esquinas redondeadas y rellena del color de
     *    superficie.
     * 4. Asigna ese dibujo como fondo de la ventana del diálogo.
     *
     * ## ¿Por qué `MaterialShapeDrawable` y no un `ThemeOverlay`?
     * Porque `MaterialAlertDialogBuilder` no acepta un "overlay"
     * de tema: necesita un tema completo, y si le pasas un overlay,
     * peta con `UnsupportedOperationException`. Con
     * `MaterialShapeDrawable` leemos el color del tema original y
     * redondeamos las esquinas sin romper los atributos base.
     *
     * @param context El contexto de la pantalla.
     * @param dialog El diálogo al que se le aplica el fondo.
     */
    private fun applyRoundedCornersToWindow(
        context: Context,
        dialog: android.app.Dialog,
    ) {
        val cornerRadiusPx = context.resources.getDimension(R.dimen.update_dialog_corner_radius)
        val surfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            android.graphics.Color.WHITE,
        )

        val roundedBackground = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setAllCornerSizes(cornerRadiusPx)
                .build(),
        ).apply {
            fillColor = ColorStateList.valueOf(surfaceColor)
        }

        dialog.window?.setBackgroundDrawable(roundedBackground)
    }

    /**
     * Limita el ancho de la ventana del diálogo para que sea más
     * largo, pero deja que el alto se ajuste al contenido.
     *
     * ## Cómo funciona paso a paso
     * 1. Lee el ancho de la pantalla del móvil.
     * 2. Calcula el 90% de ese ancho (así el diálogo deja un
     *    pequeño margen a los lados y se ve como una ventana
     *    flotante, no como una pantalla completa).
     * 3. Le dice a la ventana del diálogo que use ese ancho y que
     *    el alto se ajuste al contenido (`WRAP_CONTENT`).
     *
     * ## Por qué el alto es `WRAP_CONTENT`
     * Si el alto fuera fijo (por ejemplo, el 85% de la pantalla),
     * cuando el texto es corto el diálogo se quedaría con ese
     * tamaño enorme y el texto se vería flotando en la parte
     * superior con mucho espacio vacío debajo. Con
     * `WRAP_CONTENT`, el diálogo se ajusta a la cantidad de texto.
     *
     * ## Sobre el `maxHeight` del TextView
     * El `TextView` del layout `dialog_update.xml` tiene un
     * `maxHeight="400dp"`. Cuando el texto supera ese límite, el
     * `ScrollView` hace scroll dentro de ese espacio, y el diálogo
     * no crece más. Por eso el alto puede ser `WRAP_CONTENT` sin
     * miedo a que el diálogo ocupe toda la pantalla.
     *
     * @param context El contexto de la pantalla.
     * @param dialog El diálogo al que se le aplica el tamaño.
     */
    private fun limitWindowSize(
        context: Context,
        dialog: android.app.Dialog,
    ) {
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}