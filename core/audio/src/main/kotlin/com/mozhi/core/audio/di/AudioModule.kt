package com.mozhi.core.audio.di

import com.mozhi.core.audio.AudioRecordMicrophoneEngine
import com.mozhi.core.audio.MicrophoneEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun microphone(impl: AudioRecordMicrophoneEngine): MicrophoneEngine
}
