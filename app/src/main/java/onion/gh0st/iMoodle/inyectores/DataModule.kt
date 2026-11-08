package onion.gh0st.iMoodle.inyectores

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import onion.gh0st.iMoodle.api.moodle.auth.MoodleAuthRepository
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.repository.MoodleAssignmentRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleCalendarRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleCourseRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleForumRepository
import onion.gh0st.iMoodle.api.pasen.repository.PasenScheduleRepository
import onion.gh0st.iMoodle.api.pasen.repository.PasenUserProfileRepository
import onion.gh0st.iMoodle.data.auth.EncryptedCredentialsStore
import onion.gh0st.iMoodle.data.auth.EncryptedTokenStore
import onion.gh0st.iMoodle.data.session.DefaultSessionRepository
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import onion.gh0st.iMoodle.notifications.DefaultNotificationScheduler
import javax.inject.Singleton

/**
 * Módulo de Hilt que conecta los **contratos** del dominio con sus
 * **implementaciones**.
 *
 * ## Qué hace este módulo
 *
 * En arquitectura limpia, el dominio define **interfaces** (contratos)
 * y la infraestructura proporciona **implementaciones**. Por ejemplo:
 *
 * - `CourseRepository` (dominio): contrato que dice "se puede
 *   obtener un flujo de cursos".
 * - `MoodleCourseRepository` (infraestructura): implementación real
 *   que hace peticiones a Moodle.
 *
 * El dominio no puede construir `MoodleCourseRepository` directamente
 * porque no lo conoce (y no debe conocerlo). Entonces, ¿cómo obtiene
 * un ViewModel su `CourseRepository`? Aquí es donde entra este
 * módulo.
 *
 * Este módulo usa la anotación `@Binds` (en lugar de `@Provides`) para
 * decirle a Hilt: "cuando alguien te pida este contrato, devuélvele
 * esta implementación". Hilt se encarga del resto.
 *
 * ## Por qué se usa `@Binds` y no `@Provides`
 *
 * `@Binds` es más eficiente que `@Provides` cuando solo hay que
 * "conectar" una interfaz con una implementación existente. No hay
 * que construir nada: la implementación ya se construye en otro
 * sitio (con su propio `@Inject constructor`). `@Binds` solo dice
 * "esto es aquello".
 *
 * ## Por qué es una `abstract class` y no un `object`
 *
 * Porque los métodos `@Binds` son abstractos: no tienen cuerpo, solo
 * firma. Una `object` no puede tener métodos abstractos. Una
 * `abstract class`, sí.
 *
 * ## Organización
 *
 * Los bindings se agrupan por proveedor / capa para facilitar la
 * lectura:
 *
 * 1. Contratos de dominio sobre Moodle.
 * 2. Contratos de dominio sobre la Junta (CAS / Pasen).
 * 3. Contratos de `api` (acceso al `MoodleSession` vivo).
 * 4. Contratos de notificaciones.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // --- Contratos de dominio (Moodle) ---

    /**
     * Binds `AuthRepositoryImpl` como `AuthRepository`.
     *
     * @param impl Implementación real.
     * @return El contrato de autenticación.
     */
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: MoodleAuthRepository): AuthRepository

    /**
     * Binds `EncryptedTokenStore` como `TokenStore`.
     *
     * @param impl Almacén cifrado de tokens.
     * @return El contrato de persistencia de sesiones.
     */
    @Binds @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    /**
     * Binds `DefaultSessionRepository` como `SessionRepository`.
     *
     * @param impl Gestor del ciclo de vida de la sesión.
     * @return El contrato de sesión.
     */
    @Binds @Singleton
    abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    /**
     * Binds `MoodleCourseRepository` como `CourseRepository`.
     *
     * @param impl Repositorio con persistencia local (Room).
     * @return El contrato de cursos.
     */
    @Binds @Singleton
    abstract fun bindCourseRepository(impl: MoodleCourseRepository): CourseRepository

    /**
     * Binds `MoodleCalendarRepository` como `CalendarRepository`.
     *
     * @param impl Repositorio con persistencia local (Room).
     * @return El contrato de calendario.
     */
    @Binds @Singleton
    abstract fun bindCalendarRepository(impl: MoodleCalendarRepository): CalendarRepository

    /**
     * Binds `MoodleAssignmentRepository` como `AssignmentRepository`.
     *
     * @param impl Repositorio que consulta Moodle directamente.
     * @return El contrato de tareas.
     */
    @Binds @Singleton
    abstract fun bindAssignmentRepository(impl: MoodleAssignmentRepository): AssignmentRepository

    /**
     * Binds `MoodleForumRepository` como `ForumRepository`.
     *
     * @param impl Repositorio que consulta Moodle directamente.
     * @return El contrato de foros.
     */
    @Binds @Singleton
    abstract fun bindForumRepository(impl: MoodleForumRepository): ForumRepository

    // --- Contratos de dominio (Junta / PASEN) ---

    /**
     * Binds `EncryptedCredentialsStore` como `CredentialsStore`.
     *
     * @param impl Almacén cifrado de credenciales de la Junta.
     * @return El contrato de persistencia de credenciales.
     */
    @Binds @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedCredentialsStore): CredentialsStore

    /**
     * Binds `PasenScheduleRepository` como `ScheduleRepository`.
     *
     * @param impl Repositorio con caché por usuario.
     * @return El contrato de horario.
     */
    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: PasenScheduleRepository): ScheduleRepository

    /**
     * Binds `PasenUserProfileRepository` como `UserProfileRepository`.
     *
     * @param impl Repositorio con caché por usuario.
     * @return El contrato de enriquecimiento del perfil.
     */
    @Binds @Singleton
    abstract fun bindUserProfileRepository(
        impl: PasenUserProfileRepository,
    ): UserProfileRepository

    // --- Contratos de api ---

    /**
     * Binds `DefaultSessionRepository` como `SessionApiProvider`.
     *
     * Permite a los repositorios de `api/` obtener el cliente REST
     * autenticado sin conocer el ciclo de vida de la sesión.
     *
     * @param impl Gestor del ciclo de vida de la sesión.
     * @return El proveedor de `MoodleApiClient`.
     */
    @Binds @Singleton
    abstract fun bindSessionApiProvider(impl: DefaultSessionRepository): SessionApiProvider

    /**
     * Binds `DefaultSessionRepository` como `MoodleSessionProvider`.
     *
     * Solo lo usan componentes de infraestructura que necesitan las
     * cookies o el `OkHttpClient` (por ejemplo, `MoodleWebViewActivity`).
     *
     * @param impl Gestor del ciclo de vida de la sesión.
     * @return El proveedor de `MoodleSession`.
     */
    @Binds @Singleton
    abstract fun bindMoodleSessionProvider(impl: DefaultSessionRepository): MoodleSessionProvider

    // --- Notificaciones ---

    /**
     * Binds `DefaultNotificationScheduler` como `NotificationScheduler`.
     *
     * @param impl Programador basado en `AlarmManager`.
     * @return El contrato de programación de recordatorios.
     */
    @Binds @Singleton
    abstract fun bindNotificationScheduler(impl: DefaultNotificationScheduler): NotificationScheduler
}