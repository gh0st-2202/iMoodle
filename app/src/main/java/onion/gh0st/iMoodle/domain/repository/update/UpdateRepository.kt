package onion.gh0st.iMoodle.domain.repository.update

import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import java.io.File

/**
 * Contrato para buscar y descargar actualizaciones de la aplicación.
 *
 * ## Qué es un contrato
 * Es una regla que dice "la aplicación debe ser capaz de buscar si hay
 * versiones nuevas". No dice *cómo* hacerlo (si buscar en GitHub, en
 * un servidor propio, etc.), solo que debe existir esa capacidad.
 * La capa de datos será la encargada de decidir cómo cumplir esta regla.
 */
interface UpdateRepository {

    /**
     * Comprueba si existe una versión más nueva que la instalada.
     *
     * @return Un resultado que contiene la [AppUpdate] si hay una
     *   actualización disponible, o `null` si la app ya está al día.
     */
    suspend fun checkForUpdates(): Result<AppUpdate?>

    /**
     * Descarga el instalable de una actualización.
     *
     * @param update La actualización devuelta por [checkForUpdates].
     * @return Un resultado que contiene el fichero ya descargado y
     *   completo, o el motivo del fallo (sin red, URL no permitida…).
     */
    suspend fun downloadUpdate(update: AppUpdate): Result<File>
}