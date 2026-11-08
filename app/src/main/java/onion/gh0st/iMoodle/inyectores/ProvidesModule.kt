package onion.gh0st.iMoodle.inyectores

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import onion.gh0st.iMoodle.api.moodle.repository.MoodleUserRepository
import onion.gh0st.iMoodle.api.moodle.repository.UserRepositoryFactory
import onion.gh0st.iMoodle.data.session.UserCache
import javax.inject.Singleton

/**
 * Módulo de Hilt con los `@Provides` que no encajan en otros
 * módulos.
 *
 * ## Qué provee
 *
 * Solo una cosa: la **fábrica** de `UserRepository`. Es una pieza
 * peculiar que merece una explicación.
 *
 * ## Qué es un `UserRepositoryFactory` y por qué no es un `@Binds`
 *
 * `UserRepository` necesita el `MoodleApiClient` de la sesión activa
 * para hacer peticiones. Pero el `MoodleApiClient` no existe hasta
 * que el usuario se ha logueado. Por eso no se puede inyectar
 * directamente en tiempo de compilación.
 *
 * La solución es una **fábrica**: una función que recibe el
 * `MoodleApiClient` y devuelve un `UserRepository` listo. El
 * `DefaultSessionRepository` la usa después del login o del
 * auto-login, cuando ya tiene el cliente.
 *
 * Como `UserRepositoryFactory` no es un tipo que Hilt pueda construir
 * automáticamente (es una `fun interface` con lógica), hay que
 * "enseñarle" cómo construirla con un `@Provides`. No basta con
 * `@Binds`.
 *
 * ## Por qué se llama "ProvidesModule"
 *
 * Porque solo contiene `@Provides`. Los demás módulos (`DataModule`,
 * `DatabaseModule`, `NetworkModule`) contienen una mezcla de `@Binds`
 * y `@Provides`, o solo `@Provides` de una categoría concreta. Este
 * es el cajón de sastre para los `@Provides` que no encajan en
 * ninguno de los otros.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProvidesModule {

    /**
     * Provee la fábrica de `UserRepository`.
     *
     * ## Qué hace
     *
     * Devuelve una `UserRepositoryFactory` que, cuando se le pide
     * crear un repositorio con un `MoodleApiClient`, construye un
     * `MoodleUserRepository` que usa la caché compartida
     * (`UserCache`).
     *
     * ## Por qué se pasa la caché
     *
     * Porque la caché del usuario (`UserCache`) es un `@Singleton`
     * que debe sobrevivir entre sesiones y entre repositorios. Si
     * cada `MoodleUserRepository` construyera su propia caché, no se
     * compartiría nada: cada vez que se creara un repositorio nuevo,
     * la caché se perdería.
     *
     * Pasando la misma instancia de `UserCache`, todos los
     * repositorios (y el `DefaultSessionRepository`) ven la misma
     * caché. Cuando uno actualiza el usuario, los demás lo ven
     * inmediatamente.
     *
     * @param cache Caché del usuario actual.
     * @return La fábrica que construye el `MoodleUserRepository`
     *   real.
     */
    @Provides
    @Singleton
    fun provideUserRepositoryFactory(cache: UserCache): UserRepositoryFactory =
        UserRepositoryFactory { api -> MoodleUserRepository(api, cache) }
}