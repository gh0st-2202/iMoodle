package onion.gh0st.iMoodle.data.update

/**
 * Datos del repositorio de GitHub del que la app obtiene sus
 * actualizaciones, y comprobación de que una URL de descarga es de fiar.
 *
 * ## Por qué hay que validar la URL
 *
 * La URL del APK viene dentro de la respuesta de la API de GitHub. Antes
 * de descargar nada y ofrecérselo al instalador de Android se comprueba
 * que apunta a las *releases* del repositorio oficial del proyecto. Así,
 * aunque la respuesta se manipulase, la app nunca descargaría un
 * instalable de otro sitio.
 *
 * (Android, además, rechazará instalar un APK firmado con una clave
 * distinta de la de la app instalada; esta comprobación es una segunda
 * barrera, no la única.)
 */
internal object UpdateSource {

    /** Repositorio oficial del proyecto, con el formato `propietario/nombre`. */
    const val REPOSITORY = "gh0st-2202/iMoodle"

    /** Endpoint de la API de GitHub que devuelve la última *release* publicada. */
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    /** Prefijo que deben cumplir las URLs de descarga de los APK de las *releases*. */
    private const val TRUSTED_DOWNLOAD_PREFIX = "https://github.com/$REPOSITORY/releases/download/"

    /**
     * Indica si una URL de descarga pertenece a las *releases* del
     * repositorio oficial.
     *
     * @param url URL a comprobar.
     * @return `true` si usa HTTPS y apunta a `github.com/<repositorio>/releases/download/`.
     */
    fun isTrustedDownloadUrl(url: String): Boolean =
        url.startsWith(TRUSTED_DOWNLOAD_PREFIX, ignoreCase = true)
}
