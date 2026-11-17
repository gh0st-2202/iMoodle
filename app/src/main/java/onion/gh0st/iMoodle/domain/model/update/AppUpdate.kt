package onion.gh0st.iMoodle.domain.model.update

/**
 * Representa la información de una actualización de la aplicación.
 *
 * ## Qué es
 * Es un contenedor simple de datos. Cuando la aplicación descubre que
 * hay una versión más nueva de sí misma en internet, crea un objeto
 * de este tipo para guardar los detalles y poder avisar al usuario.
 *
 * @property versionName El número de la nueva versión (por ejemplo, "2.2").
 * @property changelog Un texto en Markdown con las notas de la versión,
 *   explicando qué ha cambiado. Puede estar **vacío** si la publicación
 *   no tiene descripción; en ese caso la interfaz muestra un texto por
 *   defecto.
 * @property downloadUrl La dirección web desde donde se puede descargar
 *   el archivo de instalación de la nueva versión.
 */
data class AppUpdate(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
)