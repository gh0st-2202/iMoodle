package onion.gh0st.iMoodle.domain.usecase.update

import onion.gh0st.iMoodle.domain.repository.update.ApkInstaller
import java.io.File
import javax.inject.Inject

/**
 * Caso de uso: instalar un APK de actualización ya descargado.
 *
 * ## Qué hace
 *
 * Delega en [ApkInstaller], que entrega el archivo al instalador de
 * paquetes de Android. Es el último paso del ciclo de actualización:
 * `CheckForUpdates` → `DownloadUpdate` → `InstallApk`.
 *
 * ## Por qué no captura errores
 *
 * Porque la única respuesta posible es "se pudo abrir" o "no se pudo
 * abrir". El contrato devuelve un `Boolean` en lugar de lanzar, así
 * que el ViewModel puede emitir un aviso sin envolver la llamada en
 * `try/catch`.
 *
 * ## Quién lo llama
 *
 * `UpdateViewModel`, justo después de una descarga correcta. La UI no
 * toca `Intent`, `FileProvider` ni rutas de archivo.
 *
 * @param apkInstaller Contrato de instalación del sistema.
 */
class InstallApk @Inject constructor(
    private val apkInstaller: ApkInstaller,
) {

    /**
     * Abre el instalador con el APK indicado.
     *
     * @param apkFile APK descargado y listo para instalar.
     * @return `true` si el instalador se abrió; `false` si no hay
     *   ninguna app capaz de instalarlo.
     */
    operator fun invoke(apkFile: File): Boolean = apkInstaller.install(apkFile)
}
