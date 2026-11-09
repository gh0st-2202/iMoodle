package onion.gh0st.iMoodle.ui.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Ayudante encargado de descargar y lanzar la instalación de una
 * actualización (archivo APK).
 *
 * ## ¿Qué es un APK?
 * Es el "instalador" de una app de Android: un archivo con extensión
 * `.apk` que contiene la app completa. Cuando haces clic en él, el
 * móvil te pregunta si quieres instalar o actualizar la app.
 *
 * ## ¿Qué hace esta clase?
 * 1. Se conecta a internet y descarga el archivo APK en la carpeta
 *    temporal (caché) del teléfono.
 * 2. Prepara el archivo para que el sistema operativo pueda leerlo
 *    de forma segura (usando un "FileProvider").
 * 3. Lanza la pantalla de instalación de Android para que el
 *    usuario confirme la actualización.
 *
 * ## ¿Qué NO hace?
 * Esta clase **no abre el navegador**. Todo el proceso ocurre
 * dentro de la app, hasta que el sistema operativo muestra su
 * propia pantalla de instalación.
 *
 * ## ¿Por qué no abre el navegador?
 * Porque el navegador daría una experiencia peor: el usuario
 * tendría que salir de la app, buscar el archivo, descargarlo, etc.
 * Al hacerlo desde dentro, el flujo es "One-Click": pulsas
 * "Descargar" y se abre el instalador de Android sin salir de la
 * app.
 */
object ApkInstaller {

    /**
     * Descarga el APK y abre la pantalla de instalación.
     *
     * ## Cómo funciona paso a paso
     * 1. **Descarga** el archivo en segundo plano (sin bloquear la
     *    interfaz), en un hilo de trabajo (porque es una operación
     *    lenta que depende de internet).
     * 2. **Vuelve al hilo principal** de la app (el que dibuja la
     *    pantalla) para lanzar el instalador, porque Android exige
     *    que los "diálogos" y los "lanzamientos" de otras apps se
     *    hagan desde el hilo principal.
     *
     * ## ¿Por qué separa descarga y lanzamiento?
     * La descarga puede tardar varios segundos. Si la hiciéramos en
     * el hilo principal, la app se "congelaría". Por eso se baja en
     * segundo plano, y solo cuando termina, se vuelve al hilo
     * principal para lanzar el instalador.
     *
     * @param context El contexto de la aplicación (suele ser la
     *   Activity o el Service que llama a esta función).
     * @param apkUrl La dirección web desde donde descargar el
     *   archivo.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String) {
        val downloadedApkFile = withContext(Dispatchers.IO) {
            downloadApk(context, apkUrl)
        }

        withContext(Dispatchers.Main) {
            launchInstaller(context, downloadedApkFile)
        }
    }

    /**
     * Realiza la descarga física del archivo.
     *
     * ## Cómo funciona paso a paso
     * 1. Decide dónde se va a guardar el archivo: en la carpeta de
     *    caché de la app, con el nombre `iMoodle_update.apk`. (La
     *    carpeta de caché es una zona temporal que Android puede
     *    borrar si necesita espacio; para una actualización eso es
     *    suficiente).
     * 2. Prepara una "petición" HTTP (el "sobre" que se envía al
     *    servidor de GitHub).
     * 3. Envía la petición y espera la respuesta.
     * 4. Comprueba que la respuesta sea correcta (código 200-299).
     *    Si no, lanza un error.
     * 5. Copia el contenido de la respuesta (los bytes del APK) al
     *    archivo de la caché, trocito a trocito.
     * 6. Devuelve el archivo descargado para que el llamador lo use.
     *
     * ## Si algo falla
     * Si la descarga falla (sin internet, 404, servidor caído…), se
     * lanza una excepción. El llamador (`UpdateDialogHelper`) no la
     * captura: el usuario verá el error y podrá reintentar cerrando
     * y volviendo a abrir el diálogo. (Esto es una deuda técnica
     * anotada en `NOTAS.md`.)
     *
     * @param context El contexto de la aplicación.
     * @param apkUrl La dirección web del archivo.
     * @return El archivo ya descargado en el almacenamiento del
     *   teléfono.
     */
    private fun downloadApk(context: Context, apkUrl: String): File {
        val targetFile = File(context.cacheDir, "iMoodle_update.apk")
        val httpClient = OkHttpClient()
        val downloadRequest = Request.Builder().url(apkUrl).build()

        httpClient.newCall(downloadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("No se pudo descargar el archivo: ${response.code}")
            }

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return targetFile
    }

    /**
     * Abre la ventana de instalación del sistema operativo Android.
     *
     * ## Cómo funciona paso a paso
     * 1. Pide al `FileProvider` una "dirección segura" (URI) para el
     *    archivo. El `FileProvider` es un mecanismo de Android que
     *    permite compartir un archivo con otra app sin darle acceso
     *    a todo el almacenamiento.
     * 2. Prepara un `Intent` (una "orden" para el sistema) de tipo
     *    "ver este archivo como si fuera un APK".
     * 3. Añade permisos para que el instalador de Android pueda
     *    leer el archivo.
     * 4. Lanza el instalador. El sistema muestra su propia pantalla
     *    de "¿Instalar esta app?".
     *
     * ## Nota de seguridad
     * A partir de Android 7, no se puede pasar un archivo
     * directamente a otra aplicación por seguridad. Hay que usar un
     * "FileProvider" que genera una dirección (URI) segura y
     * temporal para ese archivo. Por eso se usa
     * `FileProvider.getUriForFile`.
     *
     * ## Sobre `FLAG_ACTIVITY_NEW_TASK`
     * Es una marca que se añade al `Intent` para que el instalador
     * se abra en su propia "tarea" (ventana). Hace falta cuando se
     * lanza desde un contexto que no es una Activity (por ejemplo,
     * desde un `Service`).
     *
     * @param context El contexto de la aplicación.
     * @param apkFile El archivo descargado que se quiere instalar.
     */
    private fun launchInstaller(context: Context, apkFile: File) {
        val secureFileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(secureFileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }
}