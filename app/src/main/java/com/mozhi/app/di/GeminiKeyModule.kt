package com.mozhi.app.di

import com.mozhi.app.BuildConfig
import com.mozhi.data.di.GeminiApiKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object GeminiKeyModule {
    @Provides
    @GeminiApiKey
    fun geminiApiKey(): String = BuildConfig.GEMINI_API_KEY.trim()
}
