package onion.gh0st.iMoodle.inyectores

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import onion.gh0st.iMoodle.api.moodle.auth.MoodleAuthRepository
import onion.gh0st.iMoodle.api.moodle.auth.MoodleSessionProvider
import onion.gh0st.iMoodle.api.moodle.auth.SessionApiProvider
import onion.gh0st.iMoodle.api.moodle.repository.MoodleAssignmentRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleAssignmentSubmissionRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleCalendarRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleCourseRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleForumRepository
import onion.gh0st.iMoodle.api.moodle.repository.MoodleSubmissionCommentsRepository
import onion.gh0st.iMoodle.api.pasen.repository.PasenScheduleRepository
import onion.gh0st.iMoodle.api.pasen.repository.PasenUserProfileRepository
import onion.gh0st.iMoodle.data.auth.EncryptedCredentialsStore
import onion.gh0st.iMoodle.data.auth.EncryptedTokenStore
import onion.gh0st.iMoodle.data.files.AndroidFileDownloader
import onion.gh0st.iMoodle.data.favourites.LocalFavouritesRepository
import onion.gh0st.iMoodle.data.session.DefaultSessionRepository
import onion.gh0st.iMoodle.data.update.AndroidApkInstaller
import onion.gh0st.iMoodle.data.update.GitHubUpdateRepository
import onion.gh0st.iMoodle.data.web.DefaultWebSessionRepository
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentSubmissionRepository
import onion.gh0st.iMoodle.domain.repository.assignment.SubmissionCommentsRepository
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import onion.gh0st.iMoodle.domain.repository.file.FileDownloader
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import onion.gh0st.iMoodle.domain.repository.update.ApkInstaller
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import onion.gh0st.iMoodle.domain.repository.web.WebSessionRepository
import onion.gh0st.iMoodle.notifications.DefaultNotificationScheduler
import javax.inject.Singleton

/**
 * Módulo de Hilt que enlaza cada contrato del dominio con su
 * implementación concreta de la capa de datos.
 *
 * ## Para qué sirve
 *
 * Los ViewModels y casos de uso piden interfaces (`CourseRepository`,
 * `TokenStore`…) y no saben quién las implementa. Aquí se declara, una vez,
 * qué clase concreta se entrega para cada interfaz. Cambiar de
 * implementación (por ejemplo, otra forma de guardar las credenciales) solo
 * requiere tocar este fichero.
 *
 * Todas las vinculaciones son `@Singleton`: hay una única instancia en toda
 * la app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /**
     * Vincula el contrato [AuthRepository] con su implementación real,
     * [MoodleAuthRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: MoodleAuthRepository): AuthRepository

    /**
     * Vincula el contrato [TokenStore] con su implementación real,
     * [EncryptedTokenStore]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    /**
     * Vincula el contrato [SessionRepository] con su implementación real,
     * [DefaultSessionRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    /**
     * Vincula el contrato [FileDownloader] con la implementación basada
     * en el `DownloadManager` del sistema.
     */
    @Binds @Singleton
    abstract fun bindFileDownloader(impl: AndroidFileDownloader): FileDownloader

    /**
     * Vincula el contrato [CourseRepository] con su implementación real,
     * [MoodleCourseRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindCourseRepository(impl: MoodleCourseRepository): CourseRepository

    /**
     * Vincula el contrato [CalendarRepository] con su implementación real,
     * [MoodleCalendarRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindCalendarRepository(impl: MoodleCalendarRepository): CalendarRepository

    /**
     * Vincula el contrato [AssignmentRepository] con su implementación real,
     * [MoodleAssignmentRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindAssignmentRepository(impl: MoodleAssignmentRepository): AssignmentRepository

    /**
     * Vincula el contrato [AssignmentSubmissionRepository] con su implementación real,
     * [MoodleAssignmentSubmissionRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindAssignmentSubmissionRepository(
        impl: MoodleAssignmentSubmissionRepository,
    ): AssignmentSubmissionRepository

    /**
     * Vincula el contrato [ForumRepository] con su implementación real,
     * [MoodleForumRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindForumRepository(impl: MoodleForumRepository): ForumRepository

    /**
     * Vincula el contrato [CredentialsStore] con su implementación real,
     * [EncryptedCredentialsStore]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedCredentialsStore): CredentialsStore

    /**
     * Vincula el contrato [ScheduleRepository] con su implementación real,
     * [PasenScheduleRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: PasenScheduleRepository): ScheduleRepository

    /**
     * Vincula el contrato [UserProfileRepository] con su implementación real,
     * [PasenUserProfileRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindUserProfileRepository(impl: PasenUserProfileRepository): UserProfileRepository

    /**
     * Vincula el contrato [SessionApiProvider] con su implementación real,
     * [DefaultSessionRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindSessionApiProvider(impl: DefaultSessionRepository): SessionApiProvider

    /**
     * Vincula el contrato [MoodleSessionProvider] con su implementación real,
     * [DefaultSessionRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindMoodleSessionProvider(impl: DefaultSessionRepository): MoodleSessionProvider

    /**
     * Vincula el contrato [WebSessionRepository] con su implementación
     * real, [DefaultWebSessionRepository]. Traduce la sesión de OkHttp a
     * un modelo de dominio que la UI del `WebView` puede consumir.
     */
    @Binds @Singleton
    abstract fun bindWebSessionRepository(
        impl: DefaultWebSessionRepository,
    ): WebSessionRepository

    /**
     * Vincula el contrato [NotificationScheduler] con su implementación real,
     * [DefaultNotificationScheduler]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindNotificationScheduler(impl: DefaultNotificationScheduler): NotificationScheduler

    /**
     * Vincula el contrato [UpdateRepository] con su implementación real,
     * [GitHubUpdateRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindUpdateRepository(impl: GitHubUpdateRepository): UpdateRepository

    /**
     * Vincula el contrato [ApkInstaller] con su implementación real,
     * [AndroidApkInstaller]. Entrega el APK descargado al instalador de
     * paquetes del sistema sin que la UI conozca `Intent` ni
     * `FileProvider`.
     */
    @Binds @Singleton
    abstract fun bindApkInstaller(impl: AndroidApkInstaller): ApkInstaller

    /**
     * Vincula el contrato [FavouritesRepository] con su implementación real,
     * [LocalFavouritesRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindFavouritesRepository(impl: LocalFavouritesRepository): FavouritesRepository

    /**
     * Vincula el contrato [SubmissionCommentsRepository] con su implementación real,
     * [MoodleSubmissionCommentsRepository]. Hilt inyecta esta última cada vez que se pide la interfaz.
     */
    @Binds @Singleton
    abstract fun bindSubmissionCommentsRepository(
        impl: MoodleSubmissionCommentsRepository,
    ): SubmissionCommentsRepository
}