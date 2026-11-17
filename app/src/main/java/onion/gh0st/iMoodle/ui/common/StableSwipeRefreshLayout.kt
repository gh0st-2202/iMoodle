package onion.gh0st.iMoodle.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * [SwipeRefreshLayout] que evita reordenar su indicador cuando ya está
 * delante.
 *
 * ## Por qué existe
 *
 * `SwipeRefreshLayout` llama a `bringChildToFront()` sobre su "bolita"
 * en **cada** `ACTION_DOWN` (lo hace para que el indicador se dibuje
 * por encima del contenido mientras se arrastra). El problema es que
 * `bringChildToFront()` pide siempre un nuevo `requestLayout()`, aunque
 * la bolita **ya** sea el último hijo y no haya nada que reordenar.
 *
 * Ese `requestLayout()` llega en mitad del gesto. Cuando el contenido es
 * un `RecyclerView` horizontal con tarjetas (el listado de cursos en
 * horizontal), el nuevo *layout* vuelve a colocar las tarjetas y la
 * pulsación se pierde: la tarjeta recibe `ACTION_CANCEL` (o un
 * `ACTION_UP` con el estado "pulsado" ya borrado) y el curso no se abre.
 * Además, el gesto puede acabar interpretándose como un tirón vertical,
 * que mueve la lista y lanza la recarga.
 *
 * Reordenar **solo cuando hace falta** (la bolita no es el último hijo)
 * elimina ese *layout* intermedio y deja que la pulsación llegue a la
 * tarjeta. En vertical no cambia nada: el orden de dibujo sigue siendo
 * el mismo.
 */
class StableSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    /**
     * Reordena [child] solo si todavía no es el último hijo.
     *
     * @param child Vista que se quiere dibujar por encima del resto.
     */
    override fun bringChildToFront(child: View) {
        if (indexOfChild(child) == childCount - 1) return
        super.bringChildToFront(child)
    }

    /**
     * Deja el indicador delante **antes** del primer toque.
     *
     * `SwipeRefreshLayout` añade su indicador como primer hijo en el
     * constructor, y el contenido del XML se añade después. Si esperamos
     * al primer `ACTION_DOWN` para reordenarlos, ese primer toque todavía
     * provoca el `requestLayout()` que rompe la pulsación. Adelantarlo al
     * final de la inflación deja el orden correcto desde el principio.
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 1) super.bringChildToFront(getChildAt(0))
    }
}
