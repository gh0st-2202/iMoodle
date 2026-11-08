package onion.gh0st.iMoodle.fakes

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Cliente OkHttp falso para tests: cada petición la resuelve un lambda,
 * sin red ni `MockWebServer`.
 *
 * Permite simular también fallos de red (lanzando `IOException` desde el
 * lambda) y respuestas HTTP arbitrarias.
 */
object FakeHttp {

    /**
     * Construye un cliente cuyas respuestas las decide [handler].
     *
     * @param handler Función que recibe la [Request] y devuelve la
     *   [Response]. Puede lanzar `IOException` para simular fallo de red.
     * @return Cliente `OkHttpClient` configurado con el interceptor.
     */
    fun client(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    /**
     * Construye una [Response] con cuerpo JSON y código HTTP.
     *
     * @param request Petición original (necesaria para encadenar).
     * @param body Cuerpo de la respuesta.
     * @param code Código HTTP (por defecto, 200).
     * @return La `Response` lista.
     */
    fun response(request: Request, body: String, code: Int = 200): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    /** Cuerpo con el que Moodle responde a un token caducado. */
    const val INVALID_TOKEN_JSON =
        """{"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token"}"""
}