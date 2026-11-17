package onion.gh0st.iMoodle.domain.repository.update

import java.io.File

/**
 * Contrato para entregar un APK ya descargado al instalador del
 * sistema.
 *
 * ## Por qué es un contrato de dominio
 *
 * Porque "instalar una actualización" es una operación del negocio
 * (mantener la app al día), no un detalle de una pantalla. Antes vivía
 * en `ui/update/ApkInstaller` como objeto estático, lo que obligaba a
 * la Activity a conocer `FileProvider`, `Intent` y el tipo MIME. Ahora:
 *
 * - El **contrato** vive aquí y solo habla de un [File].
 * - La **implementación** (`data/update/AndroidApkInstaller`) usa las
 *   APIs de Android.
 * - El **caso de uso** (`InstallApk`) decide cuándo se llama.
 *
 * ## Qué devuelve
 *
 * `true` si el sistema abrió el instalador; `false` si no hay ninguna
 * app capaz de instalarlo. No lanza excepciones por ausencia de
 * manejador: es un estado esperado que la UI puede explicar.
 *
 * ## Seguridad
 *
 * Android comprueba que el APK esté firmado con la misma clave que la
 * app instalada; si no, rechaza la actualización.
 */
interface ApkInstaller {

    /**
     * Abre el instalador del sistema con [apkFile].
     *
     * @param apkFile APK descargado. La implementación debe validar que
     *   esté dentro de una carpeta compartible por el `FileProvider`.
     * @return `true` si se pudo abrir el instalador; `false` si no hay
     *   ningún manejador disponible.
     */
    fun install(apkFile: File): Boolean
}
