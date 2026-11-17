package onion.gh0st.iMoodle.domain.model.file

/**
 * Errores de negocio de la descarga de archivos.
 *
 * ## Por qué existe
 *
 * El `Result` de la descarga puede fallar por dos motivos muy
 * distintos: un problema del sistema (no se pudo encolar) o una
 * decisión del dominio (no hay sesión). La UI no quiere distinguir el
 * detalle técnico, pero el dominio sí puede decidir "esto no se
 * intenta". Este tipo permite expresar ese segundo caso sin inventar
 * una excepción genérica.
 *
 * ## Qué NO incluye
 *
 * Los fallos de infraestructura (por ejemplo, un `SecurityException`
 * del `DownloadManager`) viajan como la excepción original dentro del
 * `Result`, porque son información útil para el log.
 */
sealed class FileDownloadException(message: String) : Exception(message) {

    /**
     * No hay sesión activa, así que la URL del archivo no es de fiar
     * (puede estar caducada o pertenecer a otro usuario).
     *
     * La descarga ni siquiera se intenta.
     */
    object NoActiveSession : FileDownloadException("No hay sesión activa")
}
