package onion.gh0st.iMoodle.domain.repository.update

import onion.gh0st.iMoodle.domain.model.update.AppUpdate

/**
 * Contrato para buscar actualizaciones de la aplicación.
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
}