package onion.gh0st.iMoodle.core

import android.content.Intent

/**
 * Constantes y utilidades para los "deep-links" que abren el detalle
 * de una tarea desde una notificación.
 *
 * ## Qué es un deep-link
 *
 * Un "deep-link" es un enlace que no solo abre la app, sino que
 * además navega directamente a una pantalla concreta. En esta app, se
 * usan para que, cuando el usuario pulse una notificación de "tarea
 * próxima a entregar", la app se abra directamente en el detalle de
 * esa tarea, no en la pantalla de inicio.
 *
 * ## Por qué necesitan "extras"
 *
 * Un `Intent` de Android es el mensajero que se pasa entre pantallas.
 * Puede llevar "extras": pares clave-valor con datos adicionales.
 * Para que la pantalla de detalle de tarea sepa qué tarea abrir,
 * necesita dos datos:
 *
 * - El ID del curso.
 * - El ID de la instancia del módulo de tarea.
 *
 * Estos datos se meten en el `Intent` de la notificación, se
 * propagan por las pantallas que se abren (login → main → detalle) y
 * finalmente se leen en el detalle.
 *
 * ## El problema que resuelve `copy()`
 *
 * Cuando el usuario pulsa una notificación, Android puede hacer dos
 * cosas:
 *
 * 1. Abrir la app desde cero (el proceso estaba muerto).
 * 2. Traer la app al frente (el proceso estaba vivo).
 *
 * En el primer caso, la app arranca en la pantalla de login (si no
 * hay sesión activa), y luego salta a la pantalla principal. Si no
 * se reenvían los extras por ese camino, el destino original se
 * pierde y el usuario acaba en la pantalla de cursos, no en el
 * detalle de la tarea. `copy()` es la utilidad que reenvía esos
 * extras de un `Intent` a otro.
 *
 * ## Qué NO hace esta clase
 *
 * - **No crea notificaciones**. Solo define las claves de los extras
 *   y la utilidad para copiarlos.
 * - **No navega**. La navegación la hacen las Activities que leen los
 *   extras.
 */
object DeepLinkExtras {

    /**
     * Clave del extra que contiene el ID del curso.
     *
     * El nombre (`"OPEN_TASK_COURSE_ID"`) es una convención: empieza
     * por `OPEN_TASK_` para que, al mirar un `Intent` en un log o en
     * el debugger, quede claro que es un extra de deep-link.
     */
    const val COURSE_ID = "OPEN_TASK_COURSE_ID"

    /**
     * Clave del extra que contiene el ID de la instancia del módulo.
     *
     * En Moodle, una "instancia" es el ID del módulo dentro de su
     * plugin (en este caso, `mod_assign`). Es distinto del `cmid`,
     * que es el ID del módulo dentro del curso. La app acepta ambos
     * en la pantalla de detalle, pero la notificación envía el de la
     * instancia.
     */
    const val INSTANCE_ID = "OPEN_TASK_INSTANCE_ID"

    /**
     * Copia los extras de destino de un `Intent` a otro, solo si son
     * válidos.
     *
     * ## Cuándo se usa
     *
     * - Al redirigir de `MainActivity` a `LoginActivity` cuando el
     *   proceso estaba muerto (hay que reenviar los extras para que,
     *   tras el login, se abra la tarea original).
     * - Al redirigir de `LoginActivity` a `MainActivity` tras un login
     *   exitoso.
     *
     * ## Validación
     *
     * Solo se copian los extras si **ambos** son mayores que 0. Si
     * alguno falta o es inválido, no se copia nada. Esto evita
     * abrir el detalle de una tarea inexistente.
     *
     * @param from `Intent` de origen. Puede ser `null` (por ejemplo,
     *   si la Activity se ha abierto sin `Intent`).
     * @param to `Intent` de destino. Se modifica in-place.
     */
    fun copy(from: Intent?, to: Intent) {
        val courseId = from?.getIntExtra(COURSE_ID, 0) ?: 0
        val instanceId = from?.getIntExtra(INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            to.putExtra(COURSE_ID, courseId)
            to.putExtra(INSTANCE_ID, instanceId)
        }
    }
}