package com.mozhi.core.stt

import com.mozhi.core.audio.AudioChunk
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.stt.whisper.WhisperEngine
import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.transcription.TranscriptMerger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class StreamingSpeechToText @Inject constructor(
    private val whisperEngine: WhisperEngine,
) {
    private val _snapshot = MutableStateFlow(TranscriptionSnapshot())
    val snapshot: StateFlow<TranscriptionSnapshot> = _snapshot.asStateFlow()

    private val chunks = MutableSharedFlow<AudioChunk>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val mutex = Mutex()
    private val capacity = AudioConfig.SAMPLE_RATE_HZ * max(AudioConfig.WINDOW_SECONDS, 12)
    private val pcm = FloatArray(capacity)
    private var pcmSize = 0
    private var lastInferAt = 0L
    private var collectJob: Job? = null
    private var inferJob: Job? = null
    private var startedAt = 0L
    private val inferring = AtomicBoolean(false)
    @Volatile
    private var stopRequested = false
    @Volatile
    private var epoch = 0

    fun start(scope: CoroutineScope) {
        epoch += 1
        val session = epoch
        stopRequested = false
        startedAt = System.currentTimeMillis()
        lastInferAt = 0L
        pcmSize = 0
        _snapshot.value = TranscriptionSnapshot(isListening = true)
        collectJob?.cancel()
        collectJob = scope.launch(Dispatchers.Default) {
            chunks.collect { chunk ->
                if (stopRequested) return@collect
                val window = appendLocked(chunk)
                maybeTranscribe(scope, session, chunk.timestampMs, window)
            }
        }
    }

    suspend fun push(chunk: AudioChunk) {
        if (!stopRequested) chunks.emit(chunk)
    }

    fun stopImmediate() {
        epoch += 1
        stopRequested = true
        whisperEngine.abort()
        collectJob?.cancel()
        collectJob = null
        inferJob?.cancel()
        inferJob = null
        inferring.set(false)
        pcmSize = 0
        _snapshot.update {
            it.copy(isListening = false, isProcessing = false, audioLevel = 0f)
        }
    }

    private suspend fun appendLocked(chunk: AudioChunk): FloatArray {
        val elapsed = System.currentTimeMillis() - startedAt
        _snapshot.update {
            it.copy(
                isListening = !stopRequested,
                audioLevel = (it.audioLevel * 0.45f + chunk.rms * 3.2f).coerceIn(0f, 1f),
                elapsedMillis = elapsed,
            )
        }
        return mutex.withLock {
            val incoming = chunk.samples
            if (pcmSize + incoming.size > pcm.size) {
                val keep = pcm.size - incoming.size
                if (keep > 0) System.arraycopy(pcm, pcmSize - keep, pcm, 0, keep)
                pcmSize = keep.coerceAtLeast(0)
            }
            System.arraycopy(incoming, 0, pcm, pcmSize, incoming.size)
            pcmSize += incoming.size
            pcm.copyOf(pcmSize)
        }
    }

    private fun maybeTranscribe(scope: CoroutineScope, session: Int, now: Long, window: FloatArray) {
        if (stopRequested || session != epoch) return
        val strideMs = AudioConfig.STRIDE_SECONDS * 1000L
        val minSamples = AudioConfig.SAMPLE_RATE_HZ * AudioConfig.MIN_INFER_SECONDS
        if (window.size < minSamples) return
        if (now - lastInferAt < strideMs && lastInferAt != 0L) return
        if (!inferring.compareAndSet(false, true)) return
        lastInferAt = now
        val copy = window.copyOf()
        inferJob = scope.launch(Dispatchers.Default) {
            try {
                if (!stopRequested && session == epoch) runInference(copy, session)
            } finally {
                inferring.set(false)
            }
        }
    }

    private suspend fun runInference(samples: FloatArray, session: Int) {
        if (stopRequested || session != epoch) return
        _snapshot.update { it.copy(isProcessing = true) }
        val raw = runCatching {
            whisperEngine.transcribe(samples, AudioConfig.LANGUAGE_CODE) { partial ->
                if (stopRequested || session != epoch) return@transcribe
                _snapshot.update { current ->
                    val merged = TranscriptMerger.merge(current.committedText, partial)
                    current.copy(
                        committedText = merged.committed,
                        partialText = merged.partial,
                        isProcessing = true,
                        isListening = !stopRequested,
                    )
                }
            }
        }.getOrDefault("")
        if (stopRequested || session != epoch) {
            _snapshot.update { it.copy(isListening = session == epoch && !stopRequested, isProcessing = false, audioLevel = 0f) }
            return
        }
        _snapshot.update { current ->
            val merged = TranscriptMerger.merge(current.committedText, raw)
            current.copy(
                committedText = merged.committed,
                partialText = merged.partial,
                isProcessing = false,
                isListening = true,
            )
        }
    }
}
