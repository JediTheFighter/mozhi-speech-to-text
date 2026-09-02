package com.mozhi.data.di

import com.mozhi.data.repository.GeminiSettingsRepositoryImpl
import com.mozhi.data.repository.ModelRepositoryImpl
import com.mozhi.data.repository.SpeechRepositoryImpl
import com.mozhi.data.repository.TranslationRepositoryImpl
import com.mozhi.domain.repository.GeminiSettingsRepository
import com.mozhi.domain.repository.ModelRepository
import com.mozhi.domain.repository.SpeechRepository
import com.mozhi.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun speech(impl: SpeechRepositoryImpl): SpeechRepository

    @Binds
    @Singleton
    abstract fun models(impl: ModelRepositoryImpl): ModelRepository

    @Binds
    @Singleton
    abstract fun translation(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    @Singleton
    abstract fun geminiSettings(impl: GeminiSettingsRepositoryImpl): GeminiSettingsRepository
}
