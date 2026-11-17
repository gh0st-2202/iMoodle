package onion.gh0st.iMoodle.domain.repository.file

import onion.gh0st.iMoodle.domain.model.course.CourseFile

/**
 * Contrato para descargar un archivo de Moodle al almacenamiento del
 * dispositivo.
 *
 * ## Por qué es un contrato y no una función suelta
 *
 * Porque la descarga depende de una API del sistema (`DownloadManager`
 * en Android) y el dominio no debe conocerla. Con esta interfaz, el
 * caso de uso [onion.gh0st.iMoodle.domain.usecase.file.DownloadCourseFile]
 * decide **si** se puede descargar y la implementación de `data/`
 * decide **cómo**.
 *
 * ## Qué recibe
 *
 * Un [CourseFile] con la URL ya autenticada. El dominio no añade el
 * token: eso ya lo hace el repositorio que construye el modelo
 * (`appendMoodleToken`), y así el token nunca pasa por la UI.
 *
 * ## Qué devuelve
 *
 * `Result<Unit>`:
 *
 * - **Éxito**: la descarga se ha encolado correctamente.
 * - **Fallo**: no se ha podido encolar (sistema sin gestor de
 *   descargas, nombre inválido, error del sistema…).
 *
 * ## Quién la implementa
 *
 * - **Producción**: `AndroidFileDownloader` (`data/files/`).
 * - **Tests**: un doble en memoria o `mockk`.
 */
interface FileDownloader {

    /**
     * Encola la descarga de [file].
     *
     * @param file Archivo a descargar, con URL y nombre ya saneables.
     * @return `Result.success(Unit)` si se ha encolado; si no,
     *   `Result.failure(...)` con la causa.
     */
    fun download(file: CourseFile): Result<Unit>
}
