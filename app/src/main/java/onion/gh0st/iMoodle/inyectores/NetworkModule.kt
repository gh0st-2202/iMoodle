package onion.gh0st.iMoodle.inyectores

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import onion.gh0st.iMoodle.api.pasen.network.PasenApiClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Módulo de Hilt que provee las dependencias de red compartidas.
 *
 * ## Qué provee
 *
 * - **`OkHttpClient`**: el cliente HTTP global que usa toda la app
 *   para hacer peticiones (CAS, Moodle, Pasen).
 * - **`PasenApiClient`**: el cliente específico para la API de Pasen,
 *   construido a partir del anterior.
 *
 * ## Por qué vive en `SingletonComponent`
 *
 * Para garantizar que existe **una única instancia** de cada cliente
 * durante todo el ciclo de vida del proceso. Si hubiera varias,
 * cada una tendría su propio pool de conexiones y su propio pool de
 * hilos, lo que desperdiciaría recursos y podría causar problemas de
 * rendimiento.
 *
 * Con un solo `OkHttpClient`, todas las peticiones de la app
 * comparten:
 *
 * - El mismo pool de conexiones (reutiliza sockets entre peticiones).
 * - El mismo pool de hilos (no crea hilos nuevos para cada
 *   petición).
 * - La misma caché (si la hubiera).
 *
 * ## Por qué es un `object` y no una `abstract class`
 *
 * Porque los métodos `@Provides` tienen cuerpo (construyen cosas), a
 * diferencia de los `@Binds` de `DataModule`, que son abstractos.
 * Para métodos con cuerpo, `object` es lo más simple.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Crea el `OkHttpClient` global de la aplicación.
     *
     * ## Qué hace
     *
     * Configura un cliente HTTP con timeouts de 30 segundos en
     * conexión y lectura. Ese valor es un compromiso:
     *
     * - **Suficiente** para conexiones móviles lentas (3G, zonas con
     *   mala cobertura).
     * - **No demasiado alto** para no dejar al usuario esperando una
     *   eternidad si el servidor no responde.
     *
     * ## Por qué no se configuran más cosas
     *
     * Porque las opciones por defecto de OkHttp son sensatas para la
     * mayoría de casos. Si en el futuro hace falta añadir
     * interceptores (para logging, para reintentos, para
     * autenticación, etc.), se añaden aquí y todos los clientes de la
     * app se benefician.
     *
     * @return Cliente HTTP singleton reutilizado por todos los
     *   repositorios.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Crea el cliente de la API de Pasen.
     *
     * ## Qué hace
     *
     * Construye un `PasenApiClient` a partir del `OkHttpClient`
     * global. Así todas las peticiones a Pasen comparten el pool de
     * conexiones con las de Moodle y CAS, lo que es eficiente.
     *
     * ## Por qué no necesita un gestor de cookies propio
     *
     * Porque Pasen usa un esquema de autenticación distinto al de
     * Moodle: en lugar de cookies, usa un `access_token` que se
     * envía en la cabecera `Authorization: Bearer <token>`. Las
     * cookies no intervienen, así que no hace falta un
     * `CookieManager` específico.
     *
     * ## Por qué se construye aquí y no dentro del repositorio
     *
     * Porque `PasenApiClient` no tiene estado (solo hace peticiones
     * HTTP), así que puede ser un `@Singleton` compartido por todos
     * los repositorios que lo necesiten. Si se construyera dentro de
     * cada repositorio, tendríamos varias instancias, cada una con su
     * propio estado interno (aunque mínimo).
     *
     * @param okHttpClient Cliente HTTP base.
     * @return El cliente de Pasen.
     */
    @Provides
    @Singleton
    fun providePasenApiClient(okHttpClient: OkHttpClient): PasenApiClient =
        PasenApiClient(okHttpClient)
}