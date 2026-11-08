package onion.gh0st.iMoodle.domain.model.session

/**
 * Provincias andaluzas con instancia propia de Moodle.
 *
 * ## Qué es esto
 *
 * La Junta de Andalucía tiene **ocho provincias**, y cada una tiene su
 * propia instancia de Moodle Centros. Las instancias comparten
 * servidor, pero se distinguen por el segmento de la URL:
 *
 * ```
 * https://educacionadistancia.juntadeandalucia.es/centros/cordoba
 * https://educacionadistancia.juntadeandalucia.es/centros/sevilla
 * https://educacionadistancia.juntadeandalucia.es/centros/malaga
 * ```
 *
 * Este enum representa esas ocho provincias, con dos datos por cada
 * una:
 *
 * - El **slug** (el identificador que va en la URL, en minúsculas y
 *   sin tildes).
 * - El **nombre legible** (lo que se muestra al usuario, con tildes
 *   y mayúsculas correctas).
 *
 * ## Por qué es un enum
 *
 * Porque las provincias son un conjunto fijo y conocido. Un enum
 * garantiza que no se pueda inventar una provincia nueva (por
 * ejemplo, con un typo) y permite hacer `when` exhaustivo cuando se
 * necesite.
 *
 * ## Sobre el slug
 *
 * El slug es el identificador técnico que va en la URL. Es
 * **minúsculas y sin tildes** (`"cordoba"`, no `"Córdoba"` ni
 * `"córdoba"`), porque así es como lo usa la Junta en sus URLs. No
 * se debe cambiar: cambiarlo rompería todas las URLs de Moodle.
 *
 * ## Quién lo usa
 *
 * - `MoodleConfig` lo usa para construir la URL base de Moodle.
 * - `CasConfig` lo usa (indirectamente) para construir la URL de
 *   servicio del CAS.
 * - `AuthRepositoryImpl` lo usa para saber contra qué provincia
 *   autenticar.
 * - `LoginActivity` lo usa para mostrar el spinner de provincias.
 * - `DefaultSessionRepository` lo usa para persistir la sesión por
 *   provincia.
 */
enum class Provincia(val slug: String, val displayName: String) {

    /** Provincia de Almería. */
    ALMERIA("almeria", "Almería"),

    /** Provincia de Cádiz. */
    CADIZ("cadiz", "Cádiz"),

    /** Provincia de Córdoba. */
    CORDOBA("cordoba", "Córdoba"),

    /** Provincia de Granada. */
    GRANADA("granada", "Granada"),

    /** Provincia de Huelva. */
    HUELVA("huelva", "Huelva"),

    /** Provincia de Jaén. */
    JAEN("jaen", "Jaén"),

    /** Provincia de Málaga. */
    MALAGA("malaga", "Málaga"),

    /** Provincia de Sevilla. */
    SEVILLA("sevilla", "Sevilla");

    companion object {

        /**
         * Devuelve la provincia cuyo slug coincide con el indicado,
         * ignorando mayúsculas.
         *
         * ## Cuándo se llama
         *
         * - Al recuperar una sesión guardada del disco, para
         *   reconstruir la `Provincia` a partir del slug persistido.
         * - En tests, para construir provincias a partir de cadenas.
         *
         * ## Por qué ignora mayúsculas
         *
         * Porque el slug se guarda en minúsculas, pero al leerlo de
         * fuentes externas (por ejemplo, un JSON) podría venir en
         * mayúsculas. Ignorar mayúsculas hace la función más
         * robusta.
         *
         * @param slug Slug a buscar.
         * @return La `Provincia` correspondiente, o `null` si no
         *   existe.
         */
        fun fromSlug(slug: String): Provincia? =
            entries.firstOrNull { provincia ->
                provincia.slug.equals(slug, ignoreCase = true)
            }
    }
}