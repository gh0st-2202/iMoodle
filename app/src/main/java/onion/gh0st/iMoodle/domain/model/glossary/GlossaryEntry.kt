package onion.gh0st.iMoodle.domain.model.glossary

/**
 * Una entrada de un glosario de Moodle.
 *
 * ## Qué es un glosario
 *
 * El glosario (`mod_glossary`) es una actividad de Moodle en la que el
 * alumnado y el profesorado consultan y, si el curso lo permite, crean
 * definiciones de términos. Cada término es una **entrada**, compuesta
 * por:
 *
 * - Un **concepto** (el término en sí, por ejemplo "Variable").
 * - Una **definición** (su explicación, que puede llevar formato HTML).
 *
 * La app muestra las entradas de un glosario y permite añadir, editar y
 * borrar las propias.
 *
 * ## Sobre el formato de la definición
 *
 * Moodle devuelve la definición en HTML (negritas, listas, enlaces…).
 * La UI decide cómo renderizarla; el modelo no la transforma para no
 * perder información.
 *
 * ## Quién lo usa
 *
 * - `MoodleGlossaryRepository` lo construye a partir del JSON de
 *   Moodle.
 * - `GlossaryAdapter` lo usa para pintar cada entrada.
 * - `GlossaryEntryDialog` lo usa como valores iniciales al editar.
 *
 * @property id ID interno de la entrada en Moodle.
 * @property concept Término del glosario.
 * @property definition Definición del término, en HTML tal cual la
 *   envía Moodle.
 * @property author Nombre completo de quien creó la entrada, o
 *   `"Desconocido"` si Moodle no lo informa.
 * @property createdAt Fecha de creación en **segundos** (epoch).
 * @property updatedAt Fecha de la última modificación en **segundos**
 *   (epoch). Si nunca se ha editado, coincide con [createdAt].
 * @property approved `true` si la entrada está aprobada. Las entradas
 *   pendientes de aprobación solo las ve su autor y el profesorado.
 */
data class GlossaryEntry(
    val id: Int,
    val concept: String,
    val definition: String,
    val author: String,
    val createdAt: Long,
    val updatedAt: Long,
    val approved: Boolean,
)
