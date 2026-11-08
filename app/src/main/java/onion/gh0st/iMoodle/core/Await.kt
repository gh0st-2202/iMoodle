package onion.gh0st.iMoodle.core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adaptador que convierte una petición HTTP de OkHttp en una
 * operación "suspendible" de Kotlin.
 *
 * ## Qué problema resuelve
 *
 * OkHttp, la librería que usa la app para hacer peticiones HTTP, tiene
 * dos formas de ejecutar una petición:
 *
 * 1. **Síncrona** (`execute()`): bloquea el hilo hasta que llega la
 *    respuesta. Fácil de usar, pero muy peligrosa: si se llama desde
 *    el hilo principal, la interfaz se congela durante toda la
 *    petición (que puede tardar varios segundos). Y si se llama desde
 *    un hilo secundario, ese hilo queda inutilizado mientras espera.
 * 2. **Asíncrona** (`enqueue()`): no bloquea, pero obliga a trabajar
 *    con callbacks. Los callbacks son engorrosos: hay que anidarlos,
 *    gestionar los errores en cada uno, etc.
 *
 * Kotlin tiene una tercera vía: las **corrutinas**. Una función
 * `suspend` puede "pausarse" y "reanudarse" sin bloquear el hilo. Es
 * la forma más cómoda de escribir código asíncrono. Pero OkHttp no
 * sabe nada de corrutinas: hay que adaptarlo.
 *
 * Eso es exactamente lo que hace esta función: coger una petición de
 * OkHttp y convertirla en una función `suspend` que se puede llamar
 * desde una corrutina como si fuera código normal.
 *
 * ## Cómo funciona
 *
 * 1. **Suspende la corrutina** con `suspendCancellableCoroutine`.
 *    Esto significa: "pausa la corrutina actual y no la reanudes
 *    hasta que yo te avise".
 * 2. **Registra una cancelación**: si la corrutina se cancela (porque
 *    el usuario cerró la pantalla, por ejemplo), la petición HTTP
 *    también se cancela. Así no se desperdician recursos.
 * 3. **Lanza la petición con `enqueue()`**. OkHttp la ejecutará en su
 *    propio pool de hilos y, cuando termine, llamará a uno de los dos
 *    callbacks:
 *    - **`onResponse`**: hay respuesta (aunque sea un error HTTP).
 *      Reanuda la corrutina con la respuesta.
 *    - **`onFailure`**: no hay respuesta (fallo de red, timeout,
 *      DNS…). Reanuda la corrutina con la excepción.
 *
 * ## Sobre la cancelación
 *
 * Si la corrutina se cancela entre que se lanza la petición y que
 * llega la respuesta, la respuesta se descarta. Pero hay que
 * **cerrarla** para no dejar la conexión abierta. Por eso, en
 * `onResponse`, si la corrutina ya no está activa, se llama a
 * `response.close()`.
 *
 * ## Por qué se usa en toda la app
 *
 * Porque todos los clientes HTTP de la app (CAS, Moodle, Pasen) usan
 * OkHttp, y todos necesitan llamarlo desde corrutinas. Tener esta
 * función centralizada evita repetir la lógica de adaptación en cada
 * sitio.
 */

/**
 * Convierte una `Call` de OkHttp en una función `suspend`.
 *
 * ## Uso típico
 *
 * ```
 * val response = client.newCall(request).await()
 * response.use {
 *     val body = it.body?.string()
 *     // ...
 * }
 * ```
 *
 * Fíjate en que se usa `use` para cerrar la respuesta. Es importante:
 * si no se cierra, la conexión queda abierta y el pool de conexiones
 * se agota.
 *
 * ## Qué pasa si la corrutina se cancela
 *
 * La petición HTTP se cancela automáticamente con `cancel()`. OkHttp
 * cierra la conexión y libera los recursos. El llamador recibe una
 * `CancellationException`, que es lo normal en corrutinas.
 *
 * @receiver `Call` de OkHttp ya construida (no ejecutada).
 * @return La `Response` de OkHttp.
 * @throws IOException si la petición falla a nivel de red (sin
 *   conexión, timeout, DNS, etc.).
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) {
                continuation.resume(response)
            } else {
                response.close()
            }
        }

        /**
         * Callback que OkHttp invoca cuando la petición falla a nivel
         * de red.
         *
         * @param call `Call` que falló.
         * @param exception Excepción de red original.
         */
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun onFailure(call: Call, exception: IOException) {
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
    })
}