package onion.gh0st.iMoodle.domain.model.course

/**
 * Archivo descargable de un curso de Moodle.
 *
 * ## Qué es esto
 *
 * Representa cualquier archivo que el alumno puede descargar desde un
 * curso: apuntes, plantillas, PDFs, presentaciones, etc. Aparece
 * asociado a:
 *
 * - **Módulos de tipo `resource`** (un PDF suelto).
 * - **Módulos de tipo `folder`** (una carpeta con varios archivos).
 * - **Módulos de tipo `assign`** (adjuntos del enunciado de una
 *   tarea).
 *
 * ## Sobre la URL de descarga
 *
 * El campo `fileUrl` **ya lleva** el token de Moodle incorporado
 * cuando apunta al propio Moodle. El motivo es que el
 * `DownloadManager` de Android no sabe añadir tokens: si la URL no
 * los lleva, la descarga falla con 401 (no autorizado).
 *
 * El token se añade en el repositorio con `appendMoodleToken`, que
 * además se asegura de no añadirlo si la URL apunta a otro host (por
 * seguridad: no queremos regalar el token a un servidor externo).
 *
 * ## Sobre el nombre del archivo
 *
 * El nombre (`fileName`) viene del servidor y **no es de fiar**:
 * podría contener caracteres inválidos para el sistema de archivos
 * de Android (como `/` o `:`), o incluso intentos de path traversal
 * (como `../../etc/passwd`). Por eso, antes de usarlo como ruta en
 * el `DownloadManager`, la UI lo sanea con `FileNames.sanitize`.
 *
 * @property fileName Nombre del archivo tal cual lo envía Moodle.
 *   Puede contener caracteres no seguros para el sistema de archivos.
 * @property fileUrl URL absoluta de descarga. Lleva token si apunta
 *   al propio Moodle.
 * @property fileSize Tamaño en bytes. `0` si Moodle no lo informa.
 * @property mimeType Tipo MIME del archivo (`"application/pdf"`,
 *   `"image/jpeg"`, etc.), o `null` si no se conoce.
 * @property timeModified Última modificación en **segundos**. `0` si
 *   no está informada.
 */
data class CourseFile(
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val mimeType: String?,
    val timeModified: Long,
)