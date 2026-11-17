package onion.gh0st.iMoodle.core

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Nombre que se usa para un archivo cuando no se puede averiguar el real.
 */
internal const val DEFAULT_FILE_NAME = "archivo"

/** Tipo MIME genérico para archivos de tipo desconocido. */
private const val UNKNOWN_MIME_TYPE = "application/octet-stream"

/**
 * Averigua el nombre visible de un archivo identificado por una [Uri].
 *
 * ## Por qué hace falta
 *
 * El selector de archivos de Android devuelve URIs `content://…` que **no
 * contienen el nombre** del archivo. Hay que preguntárselo al
 * [ContentResolver] (columna `DISPLAY_NAME`). Si la URI es `file://…`, el
 * nombre es simplemente el último tramo de la ruta.
 *
 * ## Qué pasa si no se puede leer
 *
 * No lanza excepciones: si el proveedor no responde, no tiene la columna
 * o el valor viene vacío, devuelve [fallback]. En particular se evita el
 * fallo clásico de leer `getString(-1)` cuando el proveedor no incluye la
 * columna `DISPLAY_NAME`.
 *
 * @receiver URI del archivo.
 * @param contentResolver Resolvedor del contexto de la app.
 * @param fallback Nombre a usar si no se encuentra uno.
 * @return El nombre del archivo, nunca vacío.
 */
internal fun Uri.displayName(
    contentResolver: ContentResolver,
    fallback: String = DEFAULT_FILE_NAME,
): String {
    val resolvedName = if (scheme == "file") {
        path?.let { filePath -> File(filePath).name }
    } else {
        contentResolver.queryOpenableColumn(this, OpenableColumns.DISPLAY_NAME) { cursor, index ->
            cursor.getString(index)
        }
    }
    return resolvedName?.takeIf { name -> name.isNotBlank() } ?: fallback
}

/**
 * Averigua el tamaño en bytes de un archivo identificado por una [Uri].
 *
 * @receiver URI del archivo.
 * @param contentResolver Resolvedor del contexto de la app.
 * @return El tamaño en bytes, o `null` si el proveedor no lo indica
 *   (algunos proveedores de contenido virtuales no conocen el tamaño).
 */
internal fun Uri.sizeInBytes(contentResolver: ContentResolver): Long? =
    if (scheme == "file") {
        path?.let { filePath -> File(filePath).takeIf { file -> file.exists() }?.length() }
    } else {
        contentResolver.queryOpenableColumn(this, OpenableColumns.SIZE) { cursor, index ->
            cursor.getLong(index)
        }?.takeIf { size -> size >= 0 }
    }

/**
 * Averigua el tipo MIME de un archivo identificado por una [Uri].
 *
 * Para `content://…` se lo pregunta al proveedor. Para `file://…` lo
 * deduce de la extensión (antes se enviaban siempre como `text/plain`,
 * lo que era incorrecto para PDF, imágenes, etc.).
 *
 * @receiver URI del archivo.
 * @param contentResolver Resolvedor del contexto de la app.
 * @return El tipo MIME, o `application/octet-stream` si no se conoce.
 */
internal fun Uri.mimeType(contentResolver: ContentResolver): String =
    if (scheme == "file") {
        val extension = path?.substringAfterLast('.', missingDelimiterValue = "").orEmpty()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: UNKNOWN_MIME_TYPE
    } else {
        contentResolver.getType(this) ?: UNKNOWN_MIME_TYPE
    }

/**
 * Consulta una columna de [OpenableColumns] de forma segura.
 *
 * Cubre los tres fallos habituales de estas consultas: que el cursor
 * sea `null`, que la columna no exista (índice `-1`) y que el valor
 * sea nulo. Cualquier excepción del proveedor (por ejemplo, una
 * `SecurityException` por permiso revocado) se trata como "sin dato".
 *
 * @receiver Resolvedor de contenido.
 * @param uri URI a consultar.
 * @param column Columna deseada (`OpenableColumns.DISPLAY_NAME` o `SIZE`).
 * @param read Función que extrae el valor del cursor en el índice dado.
 * @return El valor leído, o `null` si no hay dato.
 */
private inline fun <T> ContentResolver.queryOpenableColumn(
    uri: Uri,
    column: String,
    read: (Cursor, Int) -> T,
): T? = runCatching {
    query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(column)
        if (columnIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(columnIndex)) {
            read(cursor, columnIndex)
        } else {
            null
        }
    }
}.getOrNull()
