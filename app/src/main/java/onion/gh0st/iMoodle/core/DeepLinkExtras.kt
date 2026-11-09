package onion.gh0st.iMoodle.core

import android.content.Intent

/**
 * "Nota" que transporta el destino de una notificación a través de
 * las pantallas de la app.
 *
 * ## ¿Qué problema resuelve?
 * Imagina que recibes una notificación: "Tienes una tarea de
 * Matemáticas que caduca mañana". Al pulsarla, la app debería abrir
 * directamente esa tarea. Pero, ¿y si el móvil cerró la app por
 * falta de memoria? Entonces la app no está en la pantalla correcta:
 * primero te pide identificarte, luego te lleva a la pantalla
 * principal... y si nadie se acuerda de "hacia dónde iba", acabas en
 * la lista de cursos en vez de en tu tarea.
 *
 * Esta clase es como **una nota que se pega al sobre** y que va
 * pasando de pantalla en pantalla hasta que alguien la lee y actúa:
 * "el usuario quería ir a la tarea 42 del curso 7".
 *
 * ## ¿Qué guarda exactamente?
 * Dos números:
 * - **`COURSE_ID`**: el número del curso (por ejemplo, "Matemáticas"
 *   tiene el número 7).
 * - **`INSTANCE_ID`**: el número de la tarea dentro de ese curso
 *   (por ejemplo, "Examen del tema 3" tiene el número 42).
 *
 * Con esos dos números, cualquier pantalla puede reconstruir el
 * destino exacto sin necesidad de más información.
 *
 * ## ¿Qué NO hace?
 * No abre nada por sí sola. Solo **copia** los datos de un sobre a
 * otro. Quien decide abrir la tarea es `MainActivity`.
 */
object DeepLinkExtras {

    /** Etiqueta con la que se guarda el número del curso en el sobre. */
    const val COURSE_ID = "OPEN_TASK_COURSE_ID"

    /** Etiqueta con la que se guarda el número de la tarea en el sobre. */
    const val INSTANCE_ID = "OPEN_TASK_INSTANCE_ID"

    /**
     * Pasa la nota de un sobre a otro.
     *
     * ## Cómo funciona paso a paso
     * 1. Mira en el sobre de origen (`from`) si hay un número de curso
     *    y un número de tarea. Si el sobre de origen no existe, se
     *    toman como 0.
     * 2. Comprueba que **ambos números sean mayores que 0** (es
     *    decir, que sean válidos). Si alguno es 0, no hace nada: la
     *    nota está incompleta y no sirve.
     * 3. Si son válidos, escribe esos dos números en el sobre de
     *    destino (`destination`).
     *
     * ## ¿Por qué se llama `destination` y no `to`?
     * En el lenguaje que usa la app (Kotlin), la palabra `to` es
     * especial: se usa para "emparejar" dos cosas (por ejemplo,
     * `"clave" to "valor"`). Si el parámetro se llamara `to`, el
     * compilador podría confundirse y pensar que quieres hacer un
     * emparejamiento en lugar de pasar un sobre. Para evitar esa
     * confusión, lo llamamos `destination` ("destino").
     *
     * @param from El sobre de origen. Puede ser nulo (no hay nota
     *   que pasar).
     * @param destination El sobre de destino donde se escribirá la
     *   nota (solo si es válida).
     */
    fun copy(from: Intent?, destination: Intent) {
        val courseId = from?.getIntExtra(COURSE_ID, 0) ?: 0
        val instanceId = from?.getIntExtra(INSTANCE_ID, 0) ?: 0
        if (courseId > 0 && instanceId > 0) {
            destination.putExtra(COURSE_ID, courseId)
            destination.putExtra(INSTANCE_ID, instanceId)
        }
    }
}