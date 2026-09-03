package com.mozhi.data.repository

import com.mozhi.core.audio.MicrophoneEngine
import com.mozhi.core.common.MozhiLog
import com.mozhi.data.remote.GeminiSpeechClient
import com.mozhi.data.stt.GeminiStreamingSpeechToText
import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.repository.SpeechRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRepositoryImpl @Inject constructor(
    private val microphone: MicrophoneEngine,
    private val streaming: GeminiStreamingSpeechToText,
    private val gemini: GeminiSpeechClient,
) : SpeechRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionLock = Mutex()
    private var captureJob: Job? = null

    override val transcription: Flow<TranscriptionSnapshot> = streaming.snapshot

    override fun isEngineReady(): Boolean = gemini.hasApiKey()

    override fun clearTranscript() {
        streaming.clearTranscript()
    }

    override suspend fun ensureEngineReady() {
        check(gemini.hasApiKey()) {
            "GEMINI_API_KEY missing. Add it to local.properties and rebuild the app."
        }
        MozhiLog.i("ensureEngineReady gemini cloud")
    }

    override suspend fun start() {
        sessionLock.withLock {
            MozhiLog.i("repo start gemini")
            halt()
            streaming.start(scope)
            captureJob = scope.launch {
                try {
                    microphone.stream().collect { chunk ->
                        streaming.push(chunk)
                    }
                } catch (t: CancellationException) {
                    MozhiLog.i("microphone stream cancelled")
                    throw t
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
        captureJob?.cancel()
        withTimeoutOrNull(1_600) { captureJob?.join() }
        captureJob = null
        withContext(NonCancellable) {
            streaming.flushAndStop()
        }
    }
}
