package onion.gh0st.iMoodle.domain.usecase.update

import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import java.io.File
import javax.inject.Inject

/**
 * Caso de uso para descargar el instalable de una actualización.
 *
 * Es el paso siguiente a [CheckForUpdates]: cuando el usuario acepta la
 * actualización que se le ha ofrecido, se descarga el APK para que la
 * interfaz pueda lanzar el instalador de Android.
 *
 * @param updateRepository Repositorio que sabe dónde y cómo descargar.
 */
class DownloadUpdate @Inject constructor(
    private val updateRepository: UpdateRepository,
) {

    /**
     * Ejecuta la descarga.
     *
     * @param update Actualización que se quiere instalar.
     * @return El fichero APK descargado, o el motivo del fallo.
     */
    suspend operator fun invoke(update: AppUpdate): Result<File> =
        updateRepository.downloadUpdate(update)
}
