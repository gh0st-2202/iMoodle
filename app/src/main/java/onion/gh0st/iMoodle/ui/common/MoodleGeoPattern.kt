package onion.gh0st.iMoodle.ui.common

import android.util.LruCache
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Generador de patrones geométricos SVG para portadas de curso.
 *
 * ## Qué problema resuelve
 *
 * Algunos cursos de Moodle no tienen imagen de portada. Si la app
 * mostrara un fondo plano para esos cursos, la lista se vería
 * aburrida y monótona: todos los cursos sin imagen serían iguales.
 *
 * Esta clase genera un **patrón geométrico único** para cada curso,
 * a partir de su nombre y su ID. El patrón es un SVG (un formato de
 * imagen vectorial), que se carga con Coil.
 *
 * ## Por qué SVG y no PNG
 *
 * Porque un SVG es texto plano: se puede generar en el momento, es
 * muy ligero y se escala sin perder calidad. Un PNG tendría que
 * venir de un servidor (no lo tenemos) o generarse con Canvas (más
 * complejo y pesado).
 *
 * ## Cómo funciona
 *
 * 1. Se calcula un **hash SHA-1** a partir de `"course-<id>-<name>"`.
 * 2. Del hash se derivan:
 *    - Un **color base** (hue, saturation).
 *    - Un **índice de patrón** (qué patrón de los 16 disponibles
 *      usar).
 *    - Los **parámetros del patrón** (tamaño de las celdas,
 *      opacidades, etc.).
 * 3. Se genera el SVG del patrón elegido.
 * 4. Se cachea en memoria para no regenerarlo en cada bind.
 *
 * ## Por qué es determinista
 *
 * Porque el hash SHA-1 es determinista: la misma entrada produce
 * siempre la misma salida. Así, el mismo curso siempre tendrá el
 * mismo patrón, aunque el usuario cierre y abra la app. Es lo que
 * da coherencia visual.
 *
 * ## Sobre la caché
 *
 * Se usa un `LruCache` con 64 entradas. Es suficiente para una lista
 * de cursos típica con scroll. Si se supera, se desaloja el menos
 * usado recientemente.
 *
 * ## Sobre el `ByteBuffer` y Coil
 *
 * Coil consume el `ByteBuffer` que recibe: lee todo el contenido y
 * deja el buffer "agotado" (position = limit). Si devolviéramos
 * siempre el mismo `ByteBuffer` cacheado, la segunda vez que Coil
 * intentara leerlo, encontraría el buffer agotado y no leería nada.
 *
 * Para evitarlo, la caché guarda un `ByteBuffer` "maestro" con
 * position = 0, y cada llamada a `svgBytes` devuelve un
 * `duplicate()`. Un duplicado es una vista independiente que
 * comparte el contenido subyacente pero tiene su propio position y
 * limit. Así cada llamada obtiene un buffer "fresco" sin tener que
 * copiar los datos.
 */
object MoodleGeoPattern {

    /**
     * Nombres de los patrones disponibles.
     *
     * ## Por qué 16
     *
     * Porque queremos variedad visual sin que sea abrumador. 16
     * patrones dan suficiente diversidad para una lista de cursos
     * típica.
     *
     * ## Sobre el orden
     *
     * El orden importa: el índice del patrón se deriva del hash.
     * Añadir o reordenar nombres cambia el reparto, así que hay que
     * hacerlo con cuidado (invalidaría las cachés previas, aunque no
     * es un problema grave).
     */
    private val PATTERN_NAMES = listOf(
        "chevrons", "octagons", "overlapping_circles", "plus_signs",
        "xes", "sine_waves", "hexagons", "overlapping_rings",
        "plaid", "triangles", "squares", "nested_squares",
        "mosaic_squares", "concentric_circles", "diamonds", "tessellation",
    )

    /** Ancho del SVG generado, en unidades del viewBox. */
    private const val SVG_WIDTH = 1000

    /** Alto del SVG generado, en unidades del viewBox. */
    private const val SVG_HEIGHT = 500

    /**
     * Caché de patrones ya generados, indexada por ID de curso.
     *
     * Guarda el buffer "maestro" (con position = 0). Los llamadores
     * nunca reciben este buffer: `svgBytes` devuelve siempre un
     * `duplicate()`.
     */
    private val patternCache = LruCache<Int, ByteBuffer>(64)

    /**
     * Devuelve el SVG del curso como un `ByteBuffer` listo para
     * Coil.
     *
     * ## Comportamiento
     *
     * - Si el patrón ya está en caché, devuelve un duplicado del
     *   buffer maestro.
     * - Si no, lo genera, guarda el maestro en caché y devuelve un
     *   duplicado.
     *
     * En ambos casos, el buffer devuelto tiene `position = 0` y
     * `limit = size`, independiente del maestro (ver explicación en
     * el KDoc de la clase).
     *
     * @param courseName Nombre del curso (semilla del patrón).
     * @param courseId ID del curso (clave de caché).
     * @return El SVG en memoria, listo para leerse.
     */
    fun svgBytes(courseName: String, courseId: Int): ByteBuffer {
        patternCache.get(courseId)?.let { cachedBuffer -> return cachedBuffer.duplicate() }
        val svgContent = generateSvg("course-$courseId-$courseName")
        val svgByteBuffer = ByteBuffer.wrap(svgContent.toByteArray())
        patternCache.put(courseId, svgByteBuffer)
        return svgByteBuffer.duplicate()
    }

    // --- Generación ---

    /**
     * Genera el SVG completo a partir de la semilla.
     *
     * ## Qué hace
     *
     * 1. Calcula el hash SHA-1 de la semilla.
     * 2. Deriva el color base (hue + saturation).
     * 3. Deriva el índice del patrón.
     * 4. Genera el patrón correspondiente.
     *
     * ## Cómo se deriva el color
     *
     * Se toman 3 caracteres hex del hash (posiciones 14-16),
     * se convierten a un número entre 0 y 4095, y se escalan a un
     * hue entre 0 y 359. La saturación se deriva de otro carácter
     * hex (posición 17).
     *
     * ## Cómo se deriva el patrón
     *
     * Se toma un carácter hex del hash (posición 20) y se hace módulo
     * el número de patrones. El resultado es un índice entre 0 y 15.
     *
     * @param seed Semilla (`"course-<id>-<name>"`).
     * @return El SVG completo como `String`.
     */
    private fun generateSvg(seed: String): String {
        val hash = sha1(seed)
        val hue = hexVal(hash, 14, 3) / 4095.0 * 359.0
        val saturation = hexVal(hash, 17, 1) / 100.0
        val baseColor = hslToHex(hue, (0.5 + saturation) * 100, 50.0)
        val patternIndex = hexVal(hash, 20, 1) % PATTERN_NAMES.size
        return when (PATTERN_NAMES[patternIndex]) {
            "chevrons" -> generateChevrons(hash, baseColor)
            "octagons" -> generateOctagons(hash, baseColor)
            "overlapping_circles" -> generateOverlappingCircles(hash, baseColor)
            "plus_signs" -> generatePlusSigns(hash, baseColor)
            "xes" -> generateXes(hash, baseColor)
            "sine_waves" -> generateSineWaves(hash, baseColor)
            "hexagons" -> generateHexagons(hash, baseColor)
            "overlapping_rings" -> generateOverlappingRings(hash, baseColor)
            "plaid" -> generatePlaid(hash, baseColor)
            "triangles" -> generateTriangles(hash, baseColor)
            "squares" -> generateSquares(hash, baseColor)
            "nested_squares" -> generateNestedSquares(hash, baseColor)
            "mosaic_squares" -> generateMosaicSquares(hash, baseColor)
            "concentric_circles" -> generateConcentricCircles(hash, baseColor)
            "diamonds" -> generateDiamonds(hash, baseColor)
            else -> generateTessellation(hash, baseColor)
        }
    }

    // --- Utilidades de hash y color ---

    /**
     * Calcula el hash SHA-1 de una cadena y lo devuelve en
     * hexadecimal.
     *
     * ## Qué es SHA-1
     *
     * Es una función hash criptográfica. Dada una entrada, produce
     * una salida de longitud fija (20 bytes, 40 caracteres hex) que
     * parece aleatoria. Es determinista: misma entrada, misma
     * salida.
     *
     * ## Por qué SHA-1 y no otro
     *
     * Porque está disponible en cualquier JVM sin dependencias. No
     * necesitamos seguridad criptográfica, solo una función
     * determinista que disperse bien los valores.
     *
     * @param input Cadena a hashear.
     * @return Hash en hex (40 caracteres).
     */
    private fun sha1(input: String): String {
        val hashBytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return hashBytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * Extrae un valor numérico de un bloque de caracteres hex del
     * hash.
     *
     * @param hash Hash SHA-1 en hex.
     * @param start Posición inicial.
     * @param length Longitud del bloque (1-8 caracteres hex).
     * @return Valor decimal del bloque.
     */
    private fun hexVal(hash: String, start: Int, length: Int): Int =
        hash.substring(start, start + length).toInt(16)

    /**
     * Mapea linealmente un valor de un rango a otro.
     *
     * ## Fórmula
     *
     * ```
     * (value - minVal) * (maxOut - minOut) / (maxVal - minVal) + minOut
     * ```
     *
     * @param value Valor de entrada.
     * @param minVal Mínimo del rango de entrada.
     * @param maxVal Máximo del rango de entrada.
     * @param minOut Mínimo del rango de salida.
     * @param maxOut Máximo del rango de salida.
     * @return Valor mapeado.
     */
    private fun map(value: Int, minVal: Int, maxVal: Int, minOut: Int, maxOut: Int): Int =
        (value - minVal) * (maxOut - minOut) / (maxVal - minVal) + minOut

    /**
     * Devuelve la opacidad según la paridad de un valor.
     *
     * Valores pares → `"0.02"` (casi transparente). Impares →
     * `"0.15"` (visible). Crea contraste sutil entre celdas.
     *
     * @param value Valor de control.
     * @return La opacidad como `String`.
     */
    private fun opacity(value: Int): String = if (value % 2 == 0) "0.02" else "0.15"

    /**
     * Devuelve el color de relleno según la paridad de un valor.
     *
     * @param value Valor de control.
     * @return `"#ddd"` (claro) si par, `"#222"` (oscuro) si impar.
     */
    private fun fillColor(value: Int): String = if (value % 2 == 0) "#ddd" else "#222"

    /**
     * Convierte un color HSL a hex RGB.
     *
     * ## Qué es HSL
     *
     * Es un modelo de color con tres componentes:
     *
     * - **Hue** (H): el "tono", de 0 a 360. 0 es rojo, 120 es verde,
     *   240 es azul.
     * - **Saturation** (S): la "intensidad", de 0 a 100. 0 es gris,
     *   100 es color puro.
     * - **Lightness** (L): el "brillo", de 0 a 100. 0 es negro, 100
     *   es blanco.
     *
     * ## Por qué HSL y no RGB directamente
     *
     * Porque HSL es más intuitivo para generar colores variados: se
     * elige un hue aleatorio, se fija la saturación y la
     * luminosidad, y se obtiene un color. Con RGB habría que
     * asegurarse de que los tres canales varíen en conjunto para no
     * obtener colores feos.
     *
     * @param h Hue (0-360).
     * @param s Saturación (0-100).
     * @param l Luminosidad (0-100).
     * @return Color en formato `#RRGGBB`.
     */
    private fun hslToHex(h: Double, s: Double, l: Double): String {
        val chroma = (1 - abs(2 * l / 100 - 1)) * s / 100
        val intermediate = chroma * (1 - abs((h / 60) % 2 - 1))
        val matchValue = l / 100 - chroma / 2
        val (red, green, blue) = when {
            h < 60 -> Triple(chroma, intermediate, 0.0)
            h < 120 -> Triple(intermediate, chroma, 0.0)
            h < 180 -> Triple(0.0, chroma, intermediate)
            h < 240 -> Triple(0.0, intermediate, chroma)
            h < 300 -> Triple(intermediate, 0.0, chroma)
            else -> Triple(chroma, 0.0, intermediate)
        }
        return String.format(
            "#%02X%02X%02X",
            ((red + matchValue) * 255).roundToInt(),
            ((green + matchValue) * 255).roundToInt(),
            ((blue + matchValue) * 255).roundToInt(),
        )
    }

    // --- Patrones (rellenan 1000x500) ---

    /**
     * Patrón de chevrones (galones en V) apilados con solape
     * vertical.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateChevrons(hash: String, baseColor: String): String {
        val chevronWidth = map(hexVal(hash, 0, 1), 0, 15, 30, 80)
        val chevronHeight = map(hexVal(hash, 0, 1), 0, 15, 30, 80)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = -chevronHeight
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<g fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" " +
                            "transform=\"translate($x,$y)\">",
                )
                svgBuilder.append(
                    "<path d=\"M0,0 L${chevronWidth / 2},${chevronHeight / 2} " +
                            "L$chevronWidth,0\" stroke=\"#000\" stroke-opacity=\"0.02\" " +
                            "fill=\"none\" /></g>",
                )
                x += chevronWidth
                column++
            }
            y += (chevronHeight * 0.66).toInt()
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de octágonos regulares en mosaico.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateOctagons(hash: String, baseColor: String): String {
        val octagonSize = map(hexVal(hash, 0, 1), 0, 15, 10, 60)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val octagonSvg = "<polygon points=\"${octagonSize * 0.3},0 " +
                        "${octagonSize * 0.7},0 $octagonSize,${octagonSize * 0.3} " +
                        "$octagonSize,${octagonSize * 0.7} ${octagonSize * 0.7},$octagonSize " +
                        "${octagonSize * 0.3},$octagonSize 0,${octagonSize * 0.7} " +
                        "0,${octagonSize * 0.3}\" fill=\"${fillColor(value)}\" " +
                        "fill-opacity=\"${opacity(value)}\" stroke=\"#000\" " +
                        "stroke-opacity=\"0.02\"/>"
                svgBuilder.append("<g transform=\"translate($x,$y)\">$octagonSvg</g>")
                x += octagonSize
                column++
            }
            y += octagonSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de círculos rellenos que se solapan.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateOverlappingCircles(hash: String, baseColor: String): String {
        val circleRadius = map(hexVal(hash, 0, 1), 0, 15, 25, 200) / 2
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<circle cx=\"$x\" cy=\"$y\" r=\"$circleRadius\" " +
                            "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" />",
                )
                x += circleRadius
                column++
            }
            y += circleRadius
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de signos "+" en mosaico.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generatePlusSigns(hash: String, baseColor: String): String {
        val squareSize = map(hexVal(hash, 0, 1), 0, 15, 10, 25)
        val plusSize = squareSize * 3
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT + plusSize) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH + plusSize) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val pathData = "M$squareSize,0 h$squareSize v$squareSize h$squareSize " +
                        "v$squareSize h-$squareSize v$squareSize h-$squareSize " +
                        "v-$squareSize h-$squareSize v-$squareSize h$squareSize z"
                svgBuilder.append(
                    "<g transform=\"translate($x,$y)\"><path d=\"$pathData\" " +
                            "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" " +
                            "stroke=\"#000\" stroke-opacity=\"0.02\"/></g>",
                )
                x += plusSize
                column++
            }
            y += plusSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de "X" en mosaico.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateXes(hash: String, baseColor: String): String {
        val squareSize = map(hexVal(hash, 0, 1), 0, 15, 10, 25)
        val cellSize = squareSize * 3 * 0.943
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0.0
        var row = 0
        while (y < SVG_HEIGHT + cellSize) {
            var x = 0.0
            var column = 0
            while (x < SVG_WIDTH + cellSize) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<g transform=\"translate($x,$y)\"><path " +
                            "d=\"M0,0 L$squareSize,$squareSize M$squareSize,0 L0,$squareSize\" " +
                            "stroke=\"${fillColor(value)}\" stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"2\"/></g>",
                )
                x += cellSize
                column++
            }
            y += cellSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de ondas sinusoidales apiladas.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateSineWaves(hash: String, baseColor: String): String {
        val wavePeriod = map(hexVal(hash, 0, 1), 0, 15, 100, 400)
        val waveAmplitude = map(hexVal(hash, 1, 1), 0, 15, 30, 100)
        val lineWidth = map(hexVal(hash, 2, 1), 0, 15, 3, 30)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = -waveAmplitude
        var toggle = true
        while (y < SVG_HEIGHT + waveAmplitude) {
            val stroke = if (toggle) "#ddd" else "#222"
            val strokeOpacity = if (toggle) "0.141333" else "0.115333"
            var x = 0
            val pathBuilder = StringBuilder("M 0,$waveAmplitude ")
            while (x < SVG_WIDTH) {
                pathBuilder.append(
                    "C ${x + wavePeriod / 4},0 ${x + wavePeriod / 2},0 " +
                            "${x + wavePeriod * 3 / 4},$waveAmplitude ",
                )
                pathBuilder.append(
                    "S ${x + wavePeriod * 5 / 4},${waveAmplitude * 2} " +
                            "${x + wavePeriod * 3 / 2},$waveAmplitude ",
                )
                x += wavePeriod * 3 / 2
            }
            svgBuilder.append(
                "<path d=\"$pathBuilder\" fill=\"none\" stroke=\"$stroke\" " +
                        "style=\"opacity:$strokeOpacity;stroke-width:${lineWidth}px;\" " +
                        "transform=\"translate(0,$y)\" />",
            )
            y += lineWidth
            toggle = !toggle
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de hexágonos en panal de abeja.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateHexagons(hash: String, baseColor: String): String {
        val hexagonSide = map(hexVal(hash, 0, 1), 0, 15, 8, 60)
        val hexagonHeight = hexagonSide * sqrt(3.0)
        val hexagonWidth = hexagonSide * 2
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0.0
        var row = 0
        while (y < SVG_HEIGHT + hexagonHeight) {
            var x = -hexagonSide.toDouble()
            var column = 0
            while (x < SVG_WIDTH + hexagonWidth) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val offsetY = if (column % 2 == 0) y else y + hexagonHeight / 2
                val hexagonSvg = "<polygon points=\"${hexagonSide / 2},0 " +
                        "${hexagonSide * 1.5},0 $hexagonSide,${hexagonHeight / 2} " +
                        "${hexagonSide * 1.5},$hexagonHeight ${hexagonSide / 2},$hexagonHeight " +
                        "0,${hexagonHeight / 2}\" fill=\"${fillColor(value)}\" " +
                        "fill-opacity=\"${opacity(value)}\" stroke=\"#000\" " +
                        "stroke-opacity=\"0.02\"/>"
                svgBuilder.append("<g transform=\"translate($x,$offsetY)\">$hexagonSvg</g>")
                x += hexagonSide * 1.5
                column++
            }
            y += hexagonHeight
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de anillos superpuestos (círculos sin relleno).
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateOverlappingRings(hash: String, baseColor: String): String {
        val ringRadius = map(hexVal(hash, 0, 1), 0, 15, 20, 80)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT + ringRadius) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH + ringRadius) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<circle cx=\"$x\" cy=\"$y\" r=\"$ringRadius\" fill=\"none\" " +
                            "stroke=\"${fillColor(value)}\" stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"${ringRadius / 4}\" />",
                )
                x += ringRadius
                column++
            }
            y += ringRadius
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de cuadros escoceses (líneas verticales y horizontales
     * entrecruzadas).
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generatePlaid(hash: String, baseColor: String): String {
        val stripeSize = map(hexVal(hash, 0, 1), 0, 15, 20, 80)
        val svgBuilder = StringBuilder(header(baseColor))
        var columnIndex = 0
        var x = 0
        while (x < SVG_WIDTH) {
            val value = hexVal(hash, columnIndex % 38, 1)
            svgBuilder.append(
                "<rect x=\"$x\" y=\"0\" width=\"${stripeSize / 2}\" height=\"100%\" " +
                        "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" />",
            )
            x += stripeSize
            columnIndex++
        }
        var rowIndex = 0
        var y = 0
        while (y < SVG_HEIGHT) {
            val value = hexVal(hash, (rowIndex + 20) % 38, 1)
            svgBuilder.append(
                "<rect x=\"0\" y=\"$y\" width=\"100%\" height=\"${stripeSize / 2}\" " +
                        "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" />",
            )
            y += stripeSize
            rowIndex++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de triángulos equiláteros en filas alternas.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateTriangles(hash: String, baseColor: String): String {
        val triangleSide = map(hexVal(hash, 0, 1), 0, 15, 15, 80)
        val triangleHeight = triangleSide / 2 * sqrt(3.0)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0.0
        var row = 0
        while (y < SVG_HEIGHT + triangleHeight) {
            var x = -triangleSide / 2
            var column = 0
            while (x < SVG_WIDTH + triangleSide) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val rotationDegrees = if (column % 2 != 0) 180 else 0
                val triangleSvg = "<polygon points=\"${triangleSide / 2},0 " +
                        "$triangleSide,$triangleHeight 0,$triangleHeight\" " +
                        "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" " +
                        "stroke=\"#000\" stroke-opacity=\"0.02\"/>"
                svgBuilder.append(
                    "<g transform=\"translate($x,$y) " +
                            "rotate($rotationDegrees,${triangleSide / 2},${triangleHeight / 2})\">" +
                            "$triangleSvg</g>",
                )
                x += triangleSide
                column++
            }
            y += triangleHeight
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de cuadrados rellenos en mosaico.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateSquares(hash: String, baseColor: String): String {
        val squareSize = map(hexVal(hash, 0, 1), 0, 15, 10, 60)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<rect x=\"$x\" y=\"$y\" width=\"$squareSize\" height=\"$squareSize\" " +
                            "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" " +
                            "stroke=\"#000\" stroke-opacity=\"0.02\" />",
                )
                x += squareSize
                column++
            }
            y += squareSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de cuadrados anidados (solo contorno).
     *
     * Cada celda dibuja un cuadrado grande y otro más pequeño dentro.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateNestedSquares(hash: String, baseColor: String): String {
        val squareSize = map(hexVal(hash, 0, 1), 0, 15, 20, 60)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<rect x=\"$x\" y=\"$y\" width=\"$squareSize\" height=\"$squareSize\" " +
                            "fill=\"none\" stroke=\"${fillColor(value)}\" " +
                            "stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"${squareSize / 4}\" />",
                )
                svgBuilder.append(
                    "<rect x=\"${x + squareSize / 4}\" y=\"${y + squareSize / 4}\" " +
                            "width=\"${squareSize / 2}\" height=\"${squareSize / 2}\" " +
                            "fill=\"none\" stroke=\"${fillColor(value)}\" " +
                            "stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"${squareSize / 8}\" />",
                )
                x += squareSize
                column++
            }
            y += squareSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de mosaico de cuadrados bicolor.
     *
     * Cada celda se subdivide en cuatro cuadrantes con colores
     * alternos.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateMosaicSquares(hash: String, baseColor: String): String {
        val squareSize = map(hexVal(hash, 0, 1), 0, 15, 20, 60)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val half = squareSize / 2
                svgBuilder.append(
                    "<rect x=\"$x\" y=\"$y\" width=\"$half\" height=\"$half\" " +
                            "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" />",
                )
                svgBuilder.append(
                    "<rect x=\"${x + half}\" y=\"${y + half}\" width=\"$half\" height=\"$half\" " +
                            "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" />",
                )
                svgBuilder.append(
                    "<rect x=\"${x + half}\" y=\"$y\" width=\"$half\" height=\"$half\" " +
                            "fill=\"#222\" fill-opacity=\"0.05\" />",
                )
                svgBuilder.append(
                    "<rect x=\"$x\" y=\"${y + half}\" width=\"$half\" height=\"$half\" " +
                            "fill=\"#ddd\" fill-opacity=\"0.05\" />",
                )
                x += squareSize
                column++
            }
            y += squareSize
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de círculos concéntricos (anillo exterior + interior).
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateConcentricCircles(hash: String, baseColor: String): String {
        val circleRadius = map(hexVal(hash, 0, 1), 0, 15, 20, 60)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT + circleRadius) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH + circleRadius) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                svgBuilder.append(
                    "<circle cx=\"$x\" cy=\"$y\" r=\"$circleRadius\" fill=\"none\" " +
                            "stroke=\"${fillColor(value)}\" stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"${circleRadius / 4}\" />",
                )
                svgBuilder.append(
                    "<circle cx=\"$x\" cy=\"$y\" r=\"${circleRadius / 2}\" fill=\"none\" " +
                            "stroke=\"${fillColor(value)}\" stroke-opacity=\"${opacity(value)}\" " +
                            "stroke-width=\"${circleRadius / 8}\" />",
                )
                x += circleRadius
                column++
            }
            y += circleRadius
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de rombos en mosaico.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateDiamonds(hash: String, baseColor: String): String {
        val diamondWidth = map(hexVal(hash, 0, 1), 0, 15, 10, 50)
        val diamondHeight = map(hexVal(hash, 1, 1), 0, 15, 10, 50)
        val svgBuilder = StringBuilder(header(baseColor))
        var y = 0
        var row = 0
        while (y < SVG_HEIGHT + diamondHeight) {
            var x = -diamondWidth / 2
            var column = 0
            while (x < SVG_WIDTH + diamondWidth) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val diamondSvg = "<polygon points=\"${diamondWidth / 2},0 " +
                        "$diamondWidth,${diamondHeight / 2} ${diamondWidth / 2},$diamondHeight " +
                        "0,${diamondHeight / 2}\" fill=\"${fillColor(value)}\" " +
                        "fill-opacity=\"${opacity(value)}\" stroke=\"#000\" " +
                        "stroke-opacity=\"0.02\"/>"
                svgBuilder.append("<g transform=\"translate($x,$y)\">$diamondSvg</g>")
                x += diamondWidth
                column++
            }
            y += diamondHeight
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Patrón de teselación de triángulos equiláteros.
     *
     * Similar a `generateTriangles` pero con filas intercaladas para
     * que los triángulos encajen sin huecos.
     *
     * @param hash Hash SHA-1 de la semilla.
     * @param baseColor Color de fondo.
     * @return SVG completo.
     */
    private fun generateTessellation(hash: String, baseColor: String): String {
        val triangleSide = map(hexVal(hash, 0, 1), 0, 15, 15, 60)
        val triangleHeight = triangleSide * sqrt(3.0) / 2
        val svgBuilder = StringBuilder(header(baseColor))
        var y = -triangleHeight / 2
        var row = 0
        while (y < SVG_HEIGHT + triangleHeight) {
            var x = 0
            var column = 0
            while (x < SVG_WIDTH + triangleSide) {
                val value = hexVal(hash, (row + column * 6) % 38, 1)
                val offsetY = if (column % 2 == 0) y else y + triangleHeight / 2
                val triangleSvg = "<polygon points=\"${triangleSide / 2},0 " +
                        "$triangleSide,$triangleHeight 0,$triangleHeight\" " +
                        "fill=\"${fillColor(value)}\" fill-opacity=\"${opacity(value)}\" " +
                        "stroke=\"#000\" stroke-opacity=\"0.02\"/>"
                svgBuilder.append("<g transform=\"translate($x,$offsetY)\">$triangleSvg</g>")
                x += triangleSide / 2
                column++
            }
            y += triangleHeight
            row++
        }
        return svgBuilder.append("</svg>").toString()
    }

    /**
     * Devuelve la cabecera SVG con el fondo de color indicado.
     *
     * @param color Color de fondo en formato `#RRGGBB`.
     * @return Cabecera completa (`<svg ...><rect .../>`).
     */
    private fun header(color: String): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$SVG_WIDTH\" " +
                "height=\"$SVG_HEIGHT\" viewBox=\"0 0 $SVG_WIDTH $SVG_HEIGHT\">" +
                "<rect width=\"100%\" height=\"100%\" fill=\"$color\" />"
}