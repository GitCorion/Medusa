package com.example.tujelly.di

import android.content.Context
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.AppDatabase
import com.example.tujelly.data.local.db.HomeSectionCacheDao
import com.example.tujelly.data.local.db.JellyfinDao
import com.example.tujelly.data.local.db.TmdbVoteCacheDao
import com.example.tujelly.data.remote.github.AppUpdateManager
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.usecase.FilterToLibraryUseCase
import com.example.tujelly.domain.usecase.GetHomeFeedUseCase
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
    @Singleton
    fun provideMediaRepository(
        jellyfinDao: JellyfinDao,
        userPreferencesRepository: UserPreferencesRepository,
        tmdbVoteCacheDao: TmdbVoteCacheDao
    ): MediaRepository = MediaRepository(jellyfinDao, userPreferencesRepository, tmdbVoteCacheDao)

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
