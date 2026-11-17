package onion.gh0st.iMoodle.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.BuildConfig
import onion.gh0st.iMoodle.core.Defaults
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.core.runCatchingCancellable
import onion.gh0st.iMoodle.domain.model.update.AppUpdate
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprueba y descarga las actualizaciones de la app desde las
 * *releases* de GitHub.
 *
 * ## Cómo comprueba si hay versión nueva
 *
 * 1. Pide a la API de GitHub la última *release* (`releases/latest`, que
 *    ya excluye borradores y pre-versiones).
 * 2. Compara su etiqueta con `BuildConfig.VERSION_NAME` mediante
 *    [VersionComparator].
 * 3. Busca entre sus *assets* el primer archivo `.apk` (una release puede
 *    adjuntar también el código fuente, mapas de ofuscación, etc.).
 * 4. Si hay versión más nueva y APK con una URL de confianza
 *    ([UpdateSource.isTrustedDownloadUrl]), devuelve un [AppUpdate].
 *
 * ## Cómo descarga
 *
 * [downloadUpdate] guarda el APK en `cache/updates/` primero con extensión
 * `.part` y solo lo renombra a `.apk` cuando la descarga ha terminado bien,
 * así nunca se ofrece al instalador un fichero a medias. Antes de nada
 * comprueba otra vez que la URL pertenece al repositorio oficial.
 *
 * @param httpClient Cliente HTTP compartido de la app.
 * @param context Contexto de aplicación, para acceder a la carpeta de caché.
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    @param:ApplicationContext private val context: Context,
) : UpdateRepository {

    /**
     * Consulta si existe una versión más reciente que la instalada.
     *
     * @return `AppUpdate` si hay una versión nueva instalable; `null` si
     *   la app ya está al día o la release no trae un APK válido; o el
     *   fallo ocurrido (sin red, GitHub caído, límite de peticiones…).
     */
    override suspend fun checkForUpdates(): Result<AppUpdate?> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable { fetchAvailableUpdate() }
        }

    /**
     * Descarga el APK de una actualización a la caché de la app.
     *
     * @param update Actualización a descargar.
     * @return El fichero APK ya completo, o el fallo ocurrido (URL no
     *   permitida, error HTTP, descarga vacía…).
     */
    override suspend fun downloadUpdate(update: AppUpdate): Result<File> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable { downloadApk(update.downloadUrl) }
        }

    /**
     * Pide la última release a GitHub y la convierte en [AppUpdate] si
     * procede.
     *
     * @return La actualización disponible, o `null` si no hay.
     * @throws IllegalStateException si GitHub responde con un error HTTP.
     */
    private suspend fun fetchAvailableUpdate(): AppUpdate? {
        val request = Request.Builder()
            .url(UpdateSource.LATEST_RELEASE_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", Defaults.USER_AGENT)
            .build()

        val responseBody = httpClient.newCall(request).await().use { response ->
            check(response.isSuccessful) { "Error HTTP ${response.code} al consultar la última versión" }
            response.body?.string()
        } ?: return null

        val releaseJson = JSONObject(responseBody)
        val latestVersion = releaseJson.optString("tag_name").removePrefix("v")
        if (!VersionComparator.isNewer(BuildConfig.VERSION_NAME, latestVersion)) return null

        val downloadUrl = findApkDownloadUrl(releaseJson) ?: return null

        return AppUpdate(
            versionName = latestVersion,
            changelog = releaseChangelog(releaseJson),
            downloadUrl = downloadUrl,
        )
    }

    /**
     * Busca la URL de descarga del primer *asset* `.apk` de la release.
     *
     * Solo se acepta si pertenece al repositorio oficial
     * ([UpdateSource.isTrustedDownloadUrl]).
     *
     * @param releaseJson JSON de la release.
     * @return La URL de descarga, o `null` si no hay un APK de confianza.
     */
    private fun findApkDownloadUrl(releaseJson: JSONObject): String? {
        val assets = releaseJson.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val isApk = asset.optString("name").endsWith(".apk", ignoreCase = true)
            val downloadUrl = asset.optString("browser_download_url")
            if (isApk && UpdateSource.isTrustedDownloadUrl(downloadUrl)) return downloadUrl
        }
        return null
    }

    /**
     * Obtiene las notas de la versión (Markdown) de la release.
     *
     * GitHub devuelve `"body": null` cuando la release no tiene
     * descripción. `JSONObject.optString` convertiría ese `null` JSON en
     * el texto literal `"null"`, que acabaría en el diálogo de
     * actualización; por eso se comprueba aparte.
     *
     * @param releaseJson JSON de la release.
     * @return Las notas, o una cadena vacía si no hay (la UI muestra
     *   entonces un texto por defecto).
     */
    private fun releaseChangelog(releaseJson: JSONObject): String =
        if (releaseJson.isNull("body")) "" else releaseJson.optString("body")

    /**
     * Descarga un APK a `cache/updates/iMoodle_update.apk`.
     *
     * @param downloadUrl URL del APK (debe ser de confianza).
     * @return El fichero descargado.
     * @throws IllegalArgumentException si la URL no pertenece al repositorio oficial.
     * @throws IllegalStateException si el servidor responde con error o la descarga queda vacía.
     */
    private suspend fun downloadApk(downloadUrl: String): File {
        require(UpdateSource.isTrustedDownloadUrl(downloadUrl)) { "URL de descarga no permitida" }

        val updatesDirectory = File(context.cacheDir, UPDATES_DIRECTORY).apply { mkdirs() }
        val targetFile = File(updatesDirectory, APK_FILE_NAME)
        val partialFile = File(updatesDirectory, "$APK_FILE_NAME.part")
        partialFile.delete()
        targetFile.delete()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", Defaults.USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).await().use { response ->
                check(response.isSuccessful) { "No se pudo descargar la actualización (HTTP ${response.code})" }
                val responseBody = checkNotNull(response.body) { "La descarga de la actualización está vacía" }
                responseBody.byteStream().use { input ->
                    partialFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            check(partialFile.length() > 0) { "El APK descargado está vacío" }
            check(partialFile.renameTo(targetFile)) { "No se pudo guardar el APK descargado" }
        } catch (throwable: Throwable) {
            partialFile.delete()
            throw throwable
        }

        return targetFile
    }

    companion object {
        /** Subcarpeta de la caché donde se guarda el APK (debe coincidir con `file_paths.xml`). */
        const val UPDATES_DIRECTORY = "updates"

        /** Nombre del APK descargado. */
        const val APK_FILE_NAME = "iMoodle_update.apk"
    }
}
