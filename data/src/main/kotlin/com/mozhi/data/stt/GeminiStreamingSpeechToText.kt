package com.mozhi.data.stt

import com.mozhi.core.audio.AudioChunk
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
import com.mozhi.data.remote.GeminiSpeechClient
import com.mozhi.domain.model.TranscriptionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class GeminiStreamingSpeechToText @Inject constructor(
    private val gemini: GeminiSpeechClient,
) {
    private val _snapshot = MutableStateFlow(TranscriptionSnapshot())
    val snapshot: StateFlow<TranscriptionSnapshot> = _snapshot.asStateFlow()

    private var inbox = Channel<AudioChunk>(64, BufferOverflow.DROP_OLDEST)
    private val capacity = AudioConfig.SAMPLE_RATE_HZ * max(AudioConfig.MAX_LISTEN_SECONDS, 12)
    private val pcm = FloatArray(capacity)
    private var pcmSize = 0
    private var collectJob: Job? = null
    private val pushed = AtomicInteger(0)
    @Volatile private var stopRequested = false
    @Volatile private var epoch = 0

    fun start(scope: CoroutineScope) {
        epoch += 1
        val session = epoch
        stopRequested = false
        pcmSize = 0
        pushed.set(0)
        _snapshot.value = TranscriptionSnapshot(
            isListening = true,
            debugLine = "listening — tap stop to send to Gemini",
        )
        MozhiLog.i("gemini capture start session=$session")
        collectJob?.cancel()
        inbox.close()
        val channel = Channel<AudioChunk>(64, BufferOverflow.DROP_OLDEST)
        inbox = channel
        collectJob = scope.launch(Dispatchers.Default) {
            for (chunk in channel) {
                if (stopRequested || session != epoch) continue
                append(chunk)
            }
        }
    }

    fun push(chunk: AudioChunk) {
        if (stopRequested) return
        val n = pushed.incrementAndGet()
        if (n == 1 || n % 10 == 0) {
            val msg = "mic chunk #$n rms=${"%.4f".format(chunk.rms)} buffered=${pcmSize}"
            MozhiLog.i(msg)
            _snapshot.update { it.copy(debugLine = msg, audioLevel = chunk.rms.coerceIn(0f, 1f) * 3f) }
        }
        inbox.trySend(chunk)
    }

    fun fail(message: String) {
        MozhiLog.e(message)
        _snapshot.update {
            it.copy(debugLine = message, isProcessing = false, errorMessage = message)
        }
    }

    private fun append(chunk: AudioChunk) {
        _snapshot.update {
            it.copy(
                audioLevel = (it.audioLevel * 0.45f + chunk.rms * 3.2f).coerceIn(0f, 1f),
                isListening = true,
            )
        }
        val incoming = chunk.samples
        if (pcmSize + incoming.size > pcm.size) {
            val keep = pcm.size - incoming.size
            if (keep > 0) System.arraycopy(pcm, pcmSize - keep, pcm, 0, keep)
            pcmSize = keep.coerceAtLeast(0)
        }
        System.arraycopy(incoming, 0, pcm, pcmSize, incoming.size)
        pcmSize += incoming.size
    }

    suspend fun flushAndStop() {
        val session = epoch
        collectJob?.cancel()
        collectJob = null
        inbox.close()
        stopRequested = true
        val leftover = pcm.copyOf(pcmSize)
        MozhiLog.i("gemini flush leftover=${leftover.size}")
        _snapshot.update {
            it.copy(
                isListening = false,
                isProcessing = true,
                errorMessage = "",
                debugLine = "sending ${leftover.size / AudioConfig.SAMPLE_RATE_HZ}s to Gemini…",
            )
        }
        if (leftover.isEmpty()) {
            _snapshot.update { it.copy(isListening = false, isProcessing = false, audioLevel = 0f) }
            finishSession()
            return
        }
        if (leftover.size < AudioConfig.SAMPLE_RATE_HZ / 2) {
            failAndIdle("Recording too short. Speak, then tap stop.")
            finishSession()
            return
        }
        try {
            val text = withTimeoutOrNull(45_000) { gemini.transcribe(leftover) }
            if (session != epoch) return
            when {
                text == null -> failAndIdle("Gemini timed out. Check network and GEMINI_API_KEY.")
                text.isBlank() -> failAndIdle("Gemini returned no transcript.")
                else -> {
                    MozhiLog.i("gemini flush text='${text.take(120)}'")
                    _snapshot.update {
                        it.copy(
                            committedText = text,
                            partialText = "",
                            isListening = false,
                            isProcessing = false,
                            audioLevel = 0f,
                            errorMessage = "",
                            debugLine = "done '${text.take(80)}'",
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            MozhiLog.e("gemini flush failed", t)
            if (session == epoch) {
                failAndIdle(t.message ?: "Gemini request failed")
            }
        }
        finishSession()
    }

    private fun failAndIdle(message: String) {
        _snapshot.update {
            it.copy(
                isProcessing = false,
                isListening = false,
                audioLevel = 0f,
                errorMessage = message,
                debugLine = message,
            )
        }
    }

    private fun finishSession() {
        epoch += 1
        pcmSize = 0
    }
}
