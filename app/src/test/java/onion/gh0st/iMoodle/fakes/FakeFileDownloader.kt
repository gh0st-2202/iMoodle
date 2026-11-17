package onion.gh0st.iMoodle.fakes

import onion.gh0st.iMoodle.domain.model.course.CourseFile
import onion.gh0st.iMoodle.domain.repository.file.FileDownloader

/**
 * Implementación en memoria de [FileDownloader] para tests.
 *
 * ## Qué registra
 *
 * - Cuántas veces se ha pedido una descarga ([downloadCalls]).
 * - Qué archivo se pidió la última vez ([lastFile]).
 *
 * ## Qué devuelve
 *
 * [result], que el test puede cambiar antes de cada caso (por ejemplo,
 * para simular un fallo del sistema).
 *
 * @property result Resultado a devolver por [download].
 */
class FakeFileDownloader(
    var result: Result<Unit> = Result.success(Unit),
) : FileDownloader {

    /** Número de veces que se ha llamado a [download]. */
    var downloadCalls: Int = 0
        private set

    /** Último archivo recibido por [download], o `null`. */
    var lastFile: CourseFile? = null
        private set

    /**
     * Registra la llamada y devuelve el resultado configurado.
     *
     * @param file Archivo solicitado.
     * @return [result].
     */
    override fun download(file: CourseFile): Result<Unit> {
        downloadCalls++
        lastFile = file
        return result
    }
}
