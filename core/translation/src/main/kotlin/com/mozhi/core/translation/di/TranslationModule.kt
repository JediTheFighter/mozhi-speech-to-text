package com.mozhi.core.translation.di

import com.mozhi.core.translation.DisabledTranslationEngine
import com.mozhi.core.translation.TranslationEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    @Singleton
    abstract fun engine(impl: DisabledTranslationEngine): TranslationEngine
}
