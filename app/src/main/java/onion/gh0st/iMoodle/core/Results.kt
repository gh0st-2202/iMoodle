package onion.gh0st.iMoodle.core

import kotlinx.coroutines.CancellationException

/**
 * Utilidad para capturar errores sin romper la cancelación de
 * corrutinas.
 *
 * ## Qué es `runCatching` y qué problema tiene
 *
 * Kotlin tiene una función estándar llamada `runCatching` que
 * envuelve un bloque de código y captura cualquier excepción que
 * ocurra dentro, devolviéndola como un `Result` (éxito o fallo). Es
 * muy cómoda para no tener que escribir `try/catch` por todas partes.
 *
 * ```
 * val result = runCatching { hacerAlgoQuePuedeFallar() }
 * result.onSuccess { ... }.onFailure { ... }
 * ```
 *
 * **Pero tiene un problema serio en corrutinas**: `runCatching`
 * captura **todas** las excepciones, incluyendo
 * `CancellationException`. Y `CancellationException` **no es un
 * error**: es la señal que usa Kotlin para cancelar una corrutina
 * (porque el usuario cerró la pantalla, porque la app se va a
 * segundo plano, etc.). Si la capturas y la tratas como un error,
 * la corrutina sigue viva cuando debería haberse detenido. Eso se
 * llama "romper la cancelación estructurada" y puede provocar fugas
 * de memoria y comportamientos raros.
 *
 * Esta clase define una versión corregida: `runCatchingCancellable`,
 * que captura las excepciones normales pero **re-lanza**
 * `CancellationException` para que la corrutina se cancele
 * correctamente.
 *
 * ## Cuándo usar cada una
 *
 * - **`runCatchingCancellable`**: cuando estás en una corrutina y
 *   quieres capturar errores pero respetar la cancelación. Es lo
 *   habitual en repositorios y clientes de red.
 * - **`runCatching` estándar**: solo si estás seguro de que la
 *   operación no puede ser cancelada (por ejemplo, una operación
 *   puramente de CPU que dura milisegundos).
 *
 * ## Forma de uso
 *
 * ```
 * val result = runCatchingCancellable { api.getCourses() }
 * result.onSuccess { courses -> ... }
 *       .onFailure { exception -> ... }
 * ```
 *
 * ## Por qué es `internal`
 *
 * Porque es una utilidad de bajo nivel que solo se usa dentro del
 * módulo `:app`. No forma parte de la API pública del proyecto.
 */

/**
 * Ejecuta un bloque y devuelve un `Result` con el resultado o la
 * excepción.
 *
 * ## Comportamiento
 *
 * - **Éxito**: devuelve `Result.success` con el valor devuelto por
 *   el bloque.
 * - **Excepción normal**: devuelve `Result.failure` con la
 *   excepción.
 * - **`CancellationException`**: se **re-lanza** en lugar de
 *   capturarse. Es la diferencia clave con `runCatching` estándar.
 *
 * ## Ejemplo
 *
 * ```
 * val result = runCatchingCancellable {
 *     api.callREST("core_x")  // puede lanzar IOException
 * }
 * // Si todo va bien: Result.success(response)
 * // Si falla: Result.failure(IOException)
 * // Si la corrutina se cancela: se propaga CancellationException
 * ```
 *
 * @param block Bloque a ejecutar.
 * @return `Result.success` con el valor, o `Result.failure` con la
 *   excepción.
 * @throws CancellationException si el bloque fue cancelado.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Throwable) {
        Result.failure(exception)
    }