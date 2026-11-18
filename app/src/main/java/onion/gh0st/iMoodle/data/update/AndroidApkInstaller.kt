package onion.gh0st.iMoodle.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import onion.gh0st.iMoodle.domain.repository.update.ApkInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ApkInstaller] con el instalador de paquetes de
 * Android.
 *
 * ## Qué hace
 *
 * Solo se ocupa del último paso de la actualización: entregar el APK
 * al instalador del sistema con un `Intent.ACTION_VIEW`. La descarga
 * la hace `GitHubUpdateRepository` y la coordina
 * `UpdateViewModel`.
 *
 * ## Por qué hace falta un `FileProvider`
 *
 * Desde Android 7 una app no puede entregar a otra una ruta `file://`.
 * El `FileProvider` genera una URI `content://` temporal con permiso
 * de lectura solo para el instalador. Las carpetas compartibles se
 * declaran en `res/xml/file_paths.xml` (solo `cache/updates/`).
 *
 * ## Qué verifica Android
 *
 * El propio instalador comprueba que el APK esté firmado con **la
 * misma clave** que la app instalada y rechaza la actualización si no
 * coincide.
 *
 * ## Qué NO hace
 *
 * No descarga, no valida la versión y no muestra mensajes: devuelve
 * `false` si no hay un manejador disponible y deja que la capa de
 * presentación explique el fallo.
 *
 * @param context Contexto de aplicación para resolver el
 *   `FileProvider` y lanzar el `Intent`.
 */
@Singleton
class AndroidApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApkInstaller {

    /**
     * Abre el instalador del sistema con [apkFile].
     *
     * @param apkFile APK descargado (debe estar dentro de
     *   `cache/updates/`).
     * @return `true` si se pudo abrir el instalador; `false` si el
     *   sistema no tiene ninguna app capaz de instalar paquetes.
     */
    override fun install(apkFile: File): Boolean {
        val secureFileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(secureFileUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(installIntent)
            true
        } catch (exception: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        /** Tipo MIME que identifica un instalable de Android. */
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
