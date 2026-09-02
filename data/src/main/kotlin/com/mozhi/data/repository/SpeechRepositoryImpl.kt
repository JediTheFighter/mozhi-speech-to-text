package com.mozhi.data.repository

import com.mozhi.core.audio.MicrophoneEngine
import com.mozhi.core.common.MozhiLog
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val sessionLock = Mutex()
    private var captureJob: Job? = null

    override val transcription: Flow<TranscriptionSnapshot> = streaming.snapshot

    override fun isEngineReady(): Boolean = whisperEngine.isReady

    override suspend fun ensureEngineReady() {
        val model = modelRepository.selectedModel()
            ?: error("No speech model selected")
        val path = modelRepository.localPath(model)
        val file = File(path)
        MozhiLog.i("ensureEngineReady id=${model.id} path=$path exists=${file.exists()} bytes=${file.length()}")
        check(file.exists()) { "Download a speech model before listening" }
        whisperEngine.load(path)
    }

    override suspend fun start() {
        sessionLock.withLock {
            MozhiLog.i("repo start")
            halt()
            whisperEngine.clearAbort()
            streaming.start(scope)
            captureJob = scope.launch {
                try {
                    microphone.stream().collect { chunk ->
                        streaming.push(chunk)
                    }
                } catch (t: Throwable) {
                    MozhiLog.e("microphone stream ended", t)
                    streaming.fail("mic stream: ${t.message}")
                }
            }
        }
    }

    override suspend fun stop() {
        MozhiLog.i("repo stop")
        sessionLock.withLock { halt() }
    }

    private suspend fun halt() {
        whisperEngine.abort()
        streaming.stopImmediate()
        captureJob?.cancel()
        withTimeoutOrNull(1_600) { captureJob?.join() }
        captureJob = null
    }
}
