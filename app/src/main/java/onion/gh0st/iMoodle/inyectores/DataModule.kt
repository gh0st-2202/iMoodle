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
import onion.gh0st.iMoodle.data.favourites.LocalFavouritesRepository
import onion.gh0st.iMoodle.data.session.DefaultSessionRepository
import onion.gh0st.iMoodle.data.update.GitHubUpdateRepository
import onion.gh0st.iMoodle.domain.repository.assignment.AssignmentRepository
import onion.gh0st.iMoodle.domain.repository.calendar.CalendarRepository
import onion.gh0st.iMoodle.domain.repository.course.CourseRepository
import onion.gh0st.iMoodle.domain.repository.course.FavouritesRepository
import onion.gh0st.iMoodle.domain.repository.forum.ForumRepository
import onion.gh0st.iMoodle.domain.repository.notification.NotificationScheduler
import onion.gh0st.iMoodle.domain.repository.schedule.ScheduleRepository
import onion.gh0st.iMoodle.domain.repository.session.AuthRepository
import onion.gh0st.iMoodle.domain.repository.session.CredentialsStore
import onion.gh0st.iMoodle.domain.repository.session.SessionRepository
import onion.gh0st.iMoodle.domain.repository.session.TokenStore
import onion.gh0st.iMoodle.domain.repository.session.UserProfileRepository
import onion.gh0st.iMoodle.domain.repository.update.UpdateRepository
import onion.gh0st.iMoodle.notifications.DefaultNotificationScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: MoodleAuthRepository): AuthRepository

    @Binds @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    @Binds @Singleton
    abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds @Singleton
    abstract fun bindCourseRepository(impl: MoodleCourseRepository): CourseRepository

    @Binds @Singleton
    abstract fun bindCalendarRepository(impl: MoodleCalendarRepository): CalendarRepository

    @Binds @Singleton
    abstract fun bindAssignmentRepository(impl: MoodleAssignmentRepository): AssignmentRepository

    @Binds @Singleton
    abstract fun bindForumRepository(impl: MoodleForumRepository): ForumRepository

    @Binds @Singleton
    abstract fun bindCredentialsStore(impl: EncryptedCredentialsStore): CredentialsStore

    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: PasenScheduleRepository): ScheduleRepository

    @Binds @Singleton
    abstract fun bindUserProfileRepository(impl: PasenUserProfileRepository): UserProfileRepository

    @Binds @Singleton
    abstract fun bindSessionApiProvider(impl: DefaultSessionRepository): SessionApiProvider

    @Binds @Singleton
    abstract fun bindMoodleSessionProvider(impl: DefaultSessionRepository): MoodleSessionProvider

    @Binds @Singleton
    abstract fun bindNotificationScheduler(impl: DefaultNotificationScheduler): NotificationScheduler

    @Binds @Singleton
    abstract fun bindUpdateRepository(impl: GitHubUpdateRepository): UpdateRepository

    // --- Favoritos (Implementación Local) ---
    @Binds @Singleton
    abstract fun bindFavouritesRepository(impl: LocalFavouritesRepository): FavouritesRepository
}