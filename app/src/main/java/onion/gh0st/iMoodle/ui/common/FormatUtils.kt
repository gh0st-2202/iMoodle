package onion.gh0st.iMoodle.ui.common

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utilidades de formato para la capa de UI.
 *
 * ## Qué hace
 *
 * Centraliza dos operaciones de formato que se usan en varias
 * pantallas:
 *
 * 1. **Formatear tamaños de archivo**: convertir un número de bytes
 *    (por ejemplo, `1536000`) en algo legible (`"1,5 MB"`).
 * 2. **Formatear notas numéricas**: convertir un `Double` (por
 *    ejemplo, `7.5`) en una cadena sin decimales innecesarios
 *    (`"7.5"` o `"8"`).
 *
 * ## Por qué centralizar
 *
 * Porque son operaciones que se repiten en varios sitios (la lista
 * de archivos, el detalle de tarea, etc.). Tenerlas en un solo
 * fichero:
 *
 * - Evita duplicación.
 * - Garantiza que el formato sea consistente en toda la app.
 * - Facilita el testing (aunque no hay tests ahora mismo).
 */
object FormatUtils {

    /**
     * Formatea un tamaño en bytes como una cadena legible.
     *
     * ## Cómo funciona
     *
     * 1. Si el tamaño es 0 o negativo, devuelve `"0 B"`.
     * 2. Calcula en qué unidad cae el número:
     *    - **B** (bytes): menos de 1024.
     *    - **KB** (kilobytes): 1024 bytes o más.
     *    - **MB** (megabytes): 1024 KB o más.
     *    - **GB** (gigabytes): 1024 MB o más.
     *    - **TB** (terabytes): 1024 GB o más.
     * 3. Divide el número por la potencia de 1024
     *    correspondiente.
     * 4. Formatea con **un decimal** y la unidad.
     *
     * ## Ejemplos
     *
     * ```
     * formatFileSize(0)        → "0 B"
     * formatFileSize(500)      → "500,0 B"
     * formatFileSize(1536)     → "1,5 KB"
     * formatFileSize(1048576)  → "1,0 MB"
     * ```
     *
     * ## Sobre el separador decimal
     *
     * Se usa `Locale.getDefault()`, así que en España se muestra con
     * coma (`"1,5 MB"`) y en otros países con punto. Es lo que
     * espera el usuario.
     *
     * @param bytes Tamaño en bytes.
     * @return El tamaño formateado con un decimal y la unidad.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val unitIndex = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / Math.pow(1024.0, unitIndex.toDouble()),
            units[unitIndex],
        )
    }

    /**
     * Formatea una nota numérica con hasta 2 decimales, sin ceros
     * innecesarios.
     *
     * ## Cómo funciona
     *
     * 1. Redondea a 2 decimales.
     * 2. Si el resultado es un número entero (por ejemplo, `8.0`),
     *    devuelve solo la parte entera (`"8"`).
     * 3. Si no, devuelve el número completo (`"7.5"`, `"7.25"`).
     *
     * ## Ejemplos
     *
     * ```
     * formatGrade(8.0)      → "8"
     * formatGrade(7.5)      → "7.5"
     * formatGrade(7.33333)  → "7.33"
     * formatGrade(9.99999)  → "10"
     * ```
     *
     * ## Por qué redondear a 2 decimales
     *
     * Porque las notas de Moodle a veces tienen muchos decimales
     * (`7.3333333333`) y mostrarlos todos es ruidoso. Dos decimales
     * es un buen compromiso entre precisión y legibilidad.
     *
     * ## Sobre el separador decimal
     *
     * Se usa el de `Double.toString()`, que siempre es punto (`.`),
     * independientemente del locale. Es una limitación conocida: en
     * España, lo natural sería coma. Si en el futuro se quiere
     * adaptar al locale, habría que usar `NumberFormat`.
     *
     * @param value Nota numérica.
     * @return La nota formateada.
     */
    fun formatGrade(value: Double): String {
        val roundedValue = (value * 100).roundToInt() / 100.0
        return if (roundedValue == roundedValue.toLong().toDouble()) {
            roundedValue.toLong().toString()
        } else {
            roundedValue.toString()
        }
    }
}