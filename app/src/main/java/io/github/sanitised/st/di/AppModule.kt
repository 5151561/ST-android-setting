package io.github.sanitised.st.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.data.AppContainer
import io.github.sanitised.st.data.CharacterRepository
import io.github.sanitised.st.data.LocalTavernClientProvider
import io.github.sanitised.st.data.LocalTavernLibraryReader
import io.github.sanitised.st.data.preferences.UserPreferencesRepository
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.data.repository.GroupChatRepository
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.repository.WorldInfoRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppPaths(application: Application): AppPaths = AppPaths(application)

    @Provides
    @Singleton
    fun provideAppContainer(application: Application): AppContainer = AppContainer(application)

    @Provides
    @Singleton
    fun provideLocalTavernLibraryReader(appPaths: AppPaths): LocalTavernLibraryReader =
        LocalTavernLibraryReader(appPaths.dataDir)

    @Provides
    @Singleton
    fun provideLocalTavernClientProvider(container: AppContainer): LocalTavernClientProvider =
        container.tavernClientProvider

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(container: AppContainer): UserPreferencesRepository =
        container.userPreferencesRepository

    @Provides
    @Singleton
    fun provideCharacterRepository(container: AppContainer): CharacterRepository =
        container.characterRepository

    @Provides
    @Singleton
    fun provideWorldInfoRepository(container: AppContainer): WorldInfoRepository =
        container.worldInfoRepository

    @Provides
    @Singleton
    fun provideConnectionRepository(container: AppContainer): ConnectionRepository =
        container.connectionRepository

    @Provides
    @Singleton
    fun providePersonaPresetRepository(container: AppContainer): PersonaPresetRepository =
        container.personaPresetRepository

    @Provides
    @Singleton
    fun provideGroupChatRepository(container: AppContainer): GroupChatRepository =
        container.groupChatRepository
}
