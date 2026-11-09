package onion.gh0st.iMoodle.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.BuildConfig
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación que busca actualizaciones en GitHub.
 *
 * ## Cómo funciona
 * 1. Se conecta a internet y pregunta a la página web de GitHub por
 *    la última versión publicada del proyecto.
 * 2. Lee la respuesta (un texto estructurado llamado JSON) para
 *    extraer el número de versión, las notas de cambios y el enlace
 *    de descarga del archivo de instalación.
 * 3. Compara ese número con la versión que tiene instalada el usuario.
 * 4. Si la de GitHub es más nueva, devuelve los datos para avisar al
 *    usuario.
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val httpClient: OkHttpClient,
) : UpdateRepository {

    private val latestReleaseUrl =
        "https://api.github.com/repos/gh0st-2202/iMoodle/releases/latest"

    /**
     * Consulta GitHub para ver si hay una actualización.
     *
     * @return El resultado de la consulta.
     */
    override suspend fun checkForUpdates(): Result<AppUpdate?> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(latestReleaseUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = httpClient.newCall(request).await()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Error HTTP: ${response.code}"),
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.success(null)

                val releaseJson = JSONObject(responseBody)
                val latestVersionTag = releaseJson.optString("tag_name")
                    .removePrefix("v")
                val changelog = releaseJson.optString(
                    "body",
                    "No hay notas de la versión disponibles.",
                )
                val downloadUrl = extractDownloadUrl(releaseJson)

                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, latestVersionTag) &&
                    downloadUrl.isNotEmpty()
                ) {
                    Result.success(AppUpdate(latestVersionTag, changelog, downloadUrl))
                } else {
                    Result.success(null)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }

    /**
     * Busca el enlace de descarga del archivo adjunto a la versión.
     *
     * @param releaseJson La respuesta de GitHub convertida a objeto.
     * @return La dirección de descarga, o un texto vacío si no hay.
     */
    private fun extractDownloadUrl(releaseJson: JSONObject): String {
        val assetsArray = releaseJson.optJSONArray("assets") ?: return ""
        if (assetsArray.length() == 0) return ""
        return assetsArray
            .getJSONObject(0)
            .optString("browser_download_url", "")
    }

    /**
     * Compara dos versiones para saber cuál es más nueva.
     *
     * @param current La versión instalada actualmente (ej. "2.1").
     * @param latest La versión obtenida de internet (ej. "2.2").
     * @return `true` si la versión de internet es más nueva.
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toInt() }
            val latestParts = latest.split(".").map { it.toInt() }

            for (index in 0 until maxOf(currentParts.size, latestParts.size)) {
                val currentPart = currentParts.getOrElse(index) { 0 }
                val latestPart = latestParts.getOrElse(index) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            false
        } catch (exception: Exception) {
            current != latest
        }
    }
}