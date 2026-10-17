package tv.medusa.app.di

import android.content.Context
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.AllowedMediaDao
import tv.medusa.app.data.local.db.AppDatabase
import tv.medusa.app.data.local.db.HomeSectionCacheDao
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.TmdbVoteCacheDao
import tv.medusa.app.data.remote.update.AppUpdateManager
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.usecase.FilterToLibraryUseCase
import tv.medusa.app.domain.usecase.GetHomeFeedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideJellyfinDao(database: AppDatabase): JellyfinDao = database.jellyfinDao()

    @Provides
    fun provideTmdbVoteCacheDao(database: AppDatabase): TmdbVoteCacheDao = database.tmdbVoteCacheDao()

    @Provides
    fun provideHomeSectionCacheDao(database: AppDatabase): HomeSectionCacheDao = database.homeSectionCacheDao()

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository =
        UserPreferencesRepository(context)

    @Provides
    fun provideAllowedMediaDao(database: AppDatabase): AllowedMediaDao = database.allowedMediaDao()

    @Provides
    @Singleton
    fun provideMediaRepository(
        @ApplicationContext context: Context,
        jellyfinDao: JellyfinDao,
        userPreferencesRepository: UserPreferencesRepository,
        tmdbVoteCacheDao: TmdbVoteCacheDao,
        allowedMediaDao: AllowedMediaDao
    ): MediaRepository = MediaRepository(jellyfinDao, userPreferencesRepository, tmdbVoteCacheDao, allowedMediaDao, context)

    @Provides
    @Singleton
    fun provideFilterToLibraryUseCase(mediaRepository: MediaRepository): FilterToLibraryUseCase =
        FilterToLibraryUseCase(mediaRepository)

    @Provides
    @Singleton
    fun provideGetHomeFeedUseCase(
        mediaRepository: MediaRepository,
        filterToLibraryUseCase: FilterToLibraryUseCase,
        homeSectionCacheDao: HomeSectionCacheDao
    ): GetHomeFeedUseCase = GetHomeFeedUseCase(mediaRepository, filterToLibraryUseCase, homeSectionCacheDao)

    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManager(context)
}
