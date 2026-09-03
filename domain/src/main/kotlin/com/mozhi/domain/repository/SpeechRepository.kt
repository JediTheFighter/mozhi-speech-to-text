package com.mozhi.domain.repository

import com.mozhi.domain.model.TranscriptionSnapshot
import kotlinx.coroutines.flow.Flow

interface SpeechRepository {
    val transcription: Flow<TranscriptionSnapshot>
    suspend fun start()
    suspend fun stop()
    suspend fun ensureEngineReady()
    fun isEngineReady(): Boolean
    fun clearTranscript()
}
