package onion.gh0st.iMoodle.domain.usecase.file

import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.model.file.FileDownloadException
import onion.gh0st.iMoodle.domain.repository.file.FileDownloader
import onion.gh0st.iMoodle.domain.usecase.session.ObserveSession
import javax.inject.Inject

/**
 * Caso de uso: descargar un archivo de un curso.
 *
 * ## Qué comprueba antes de descargar
 *
 * Que haya una sesión activa. La URL del archivo lleva el token de
 * Moodle, y ese token solo tiene sentido mientras la sesión exista:
 * descargar sin sesión acabaría en un 401 y en un archivo vacío. Por
 * eso esta regla vive en el dominio y no en cada Fragment, que era
 * donde estaba antes (y se repetía en tres pantallas).
 *
 * ## Dónde acaba el trabajo
 *
 * En [FileDownloader], implementado en `data/files/` con el
 * `DownloadManager` del sistema. El dominio no conoce Android.
 *
 * ## Cuándo se llama
 *
 * Después de que el usuario confirme en el diálogo de descarga. El
 * diálogo es UI; la decisión de descargar y el guard de sesión son
 * dominio.
 *
 * @param observeSession Lectura síncrona de la sesión activa.
 * @param fileDownloader Contrato de descarga del sistema.
 */
class DownloadCourseFile @Inject constructor(
    private val observeSession: ObserveSession,
    private val fileDownloader: FileDownloader,
) {

    /**
     * Intenta encolar la descarga.
     *
     * @param file Archivo a descargar.
     * @return `Result.success(Unit)` si se ha encolado,
     *   `Result.failure(FileDownloadException.NoActiveSession)` si no hay
     *   sesión, o el error del sistema si la descarga no se pudo
     *   encolar.
     */
    operator fun invoke(file: CourseFile): Result<Unit> {
        if (observeSession.currentSession == null) {
            return Result.failure(FileDownloadException.NoActiveSession)
        }
        return fileDownloader.download(file)
    }
}
