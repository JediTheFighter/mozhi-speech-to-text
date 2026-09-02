package com.mozhi.data.repository

import com.mozhi.core.audio.MicrophoneEngine
import com.mozhi.core.stt.StreamingSpeechToText
import com.mozhi.core.stt.whisper.WhisperEngine
import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.repository.ModelRepository
import com.mozhi.domain.repository.SpeechRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRepositoryImpl @Inject constructor(
    private val microphone: MicrophoneEngine,
    private val streaming: StreamingSpeechToText,
    private val whisperEngine: WhisperEngine,
    private val modelRepository: ModelRepository,
) : SpeechRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null

    override val transcription: Flow<TranscriptionSnapshot> = streaming.snapshot

    override fun isEngineReady(): Boolean = whisperEngine.isReady

    override suspend fun ensureEngineReady() {
        val model = modelRepository.selectedModel()
            ?: error("No speech model selected")
        val path = modelRepository.localPath(model)
        check(File(path).exists()) { "Download a speech model before listening" }
        if (!whisperEngine.isReady) {
            whisperEngine.load(path)
        }
    }

    override suspend fun start() {
        stop()
        streaming.start(scope)
        captureJob = scope.launch {
            microphone.stream().collect { chunk ->
                streaming.push(chunk)
            }
        }
    }

    override suspend fun stop() {
        whisperEngine.abort()
        captureJob?.cancel()
        captureJob = null
        streaming.stopAndFinalize()
    }
}
