package com.mozhi.data.di

import com.mozhi.domain.repository.ModelRepository
import com.mozhi.domain.repository.SpeechRepository
import com.mozhi.domain.repository.TranslationRepository
import com.mozhi.domain.usecase.DownloadSpeechModelUseCase
import com.mozhi.domain.usecase.ObserveModelCatalogUseCase
import com.mozhi.domain.usecase.ObserveTranscriptionUseCase
import com.mozhi.domain.usecase.SelectSpeechModelUseCase
import com.mozhi.domain.usecase.StartListeningUseCase
import com.mozhi.domain.usecase.StopListeningUseCase
import com.mozhi.domain.usecase.TranslateTranscriptUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun startListening(repo: SpeechRepository) = StartListeningUseCase(repo)

    @Provides
    @Singleton
    fun stopListening(repo: SpeechRepository) = StopListeningUseCase(repo)

    @Provides
    @Singleton
    fun observeTranscription(repo: SpeechRepository) = ObserveTranscriptionUseCase(repo)

    @Provides
    @Singleton
    fun downloadModel(repo: ModelRepository) = DownloadSpeechModelUseCase(repo)

    @Provides
    @Singleton
    fun selectModel(repo: ModelRepository) = SelectSpeechModelUseCase(repo)

    @Provides
    @Singleton
    fun observeModels(repo: ModelRepository) = ObserveModelCatalogUseCase(repo)

    @Provides
    @Singleton
    fun translate(repo: TranslationRepository) = TranslateTranscriptUseCase(repo)
}
