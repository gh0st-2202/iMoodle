package onion.gh0st.iMoodle.data.update

/**
 * Compara números de versión del estilo `2.4`, `2.4.1` o `v2.5-beta`.
 *
 * ## Reglas
 *
 * - Se ignora un prefijo `v`/`V` (las etiquetas de GitHub suelen ser `v2.5`).
 * - La parte numérica se compara componente a componente **como números**
 *   (así `2.10` es mayor que `2.9`), rellenando con ceros las que falten
 *   (`2.4` equivale a `2.4.0`).
 * - Un sufijo tras `-` (por ejemplo `-beta`) marca una *pre-versión*, que
 *   es **anterior** a la versión final con los mismos números: `2.5-beta`
 *   es más antigua que `2.5`.
 * - Todo lo que haya tras un `+` (metadatos de compilación) se ignora.
 * - Si alguna de las dos versiones no se puede interpretar, se considera
 *   que **no** hay actualización. Antes, una etiqueta rara provocaba que
 *   se ofreciera una "actualización" siempre.
 */
internal object VersionComparator {

    /**
     * Indica si [candidate] es una versión más reciente que [current].
     *
     * @param current Versión instalada (por ejemplo, `BuildConfig.VERSION_NAME`).
     * @param candidate Versión publicada que se quiere comparar.
     * @return `true` solo si [candidate] es estrictamente más reciente.
     */
    fun isNewer(current: String, candidate: String): Boolean {
        val currentVersion = parse(current) ?: return false
        val candidateVersion = parse(candidate) ?: return false

        val componentCount = maxOf(currentVersion.numbers.size, candidateVersion.numbers.size)
        for (index in 0 until componentCount) {
            val currentPart = currentVersion.numbers.getOrElse(index) { 0 }
            val candidatePart = candidateVersion.numbers.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }

        return currentVersion.isPreRelease && !candidateVersion.isPreRelease
    }

    /**
     * Versión interpretada.
     *
     * @property numbers Componentes numéricos (`2.4.1` → `[2, 4, 1]`).
     * @property isPreRelease `true` si lleva un sufijo de pre-versión (`-beta`…).
     */
    private class ParsedVersion(val numbers: List<Int>, val isPreRelease: Boolean)

    /**
     * Interpreta un texto de versión.
     *
     * @param rawVersion Texto de la versión.
     * @return La versión interpretada, o `null` si no tiene el formato esperado.
     */
    private fun parse(rawVersion: String): ParsedVersion? {
        val withoutBuildMetadata = rawVersion.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val numericPart = withoutBuildMetadata.substringBefore('-')

        val numbers = numericPart.split('.').map { component -> component.toIntOrNull() ?: return null }

        return ParsedVersion(
            numbers = numbers,
            isPreRelease = withoutBuildMetadata.contains('-'),
        )
    }
}
