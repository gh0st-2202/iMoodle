package onion.gh0st.iMoodle.api.pasen.network

/**
 * Error producido al hablar con la API de iPasen.
 *
 * ## Por qué existe
 *
 * Antes todos los fallos se lanzaban como `IllegalStateException`, un
 * tipo demasiado genérico: no permitía distinguir un fallo de iPasen
 * de cualquier otro error de programación. Con un tipo propio, quien
 * llama puede capturar exactamente este caso y el resto de excepciones
 * siguen indicando fallos inesperados.
 *
 * @param message Descripción del fallo, con la operación afectada.
 * @param cause Excepción original (red, JSON mal formado…), si la hay.
 */
class PasenApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
