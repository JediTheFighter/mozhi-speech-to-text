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
    private var job: Job? = null
    private var startedAt = 0L

    fun start(scope: CoroutineScope) {
        startedAt = System.currentTimeMillis()
        lastInferAt = 0L
        pcmSize = 0
        _snapshot.value = TranscriptionSnapshot(isListening = true)
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            chunks.collect { chunk ->
                val window = appendLocked(chunk)
                maybeTranscribe(chunk.timestampMs, window)
            }
        }
    }

    suspend fun push(chunk: AudioChunk) {
        chunks.emit(chunk)
    }

    suspend fun stopAndFinalize() {
        job?.cancel()
        job = null
        val remaining = mutex.withLock { pcm.copyOf(pcmSize) }
        if (remaining.size > AudioConfig.SAMPLE_RATE_HZ / 4) {
            runInference(remaining, finalize = true)
        } else {
            _snapshot.update { it.copy(isListening = false, isProcessing = false, audioLevel = 0f) }
        }
        mutex.withLock { pcmSize = 0 }
    }

    private suspend fun appendLocked(chunk: AudioChunk): FloatArray {
        val elapsed = System.currentTimeMillis() - startedAt
        _snapshot.update {
            it.copy(
                isListening = true,
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

    private suspend fun maybeTranscribe(now: Long, window: FloatArray) {
        val strideMs = AudioConfig.STRIDE_SECONDS * 1000L
        val minSamples = AudioConfig.SAMPLE_RATE_HZ * 2
        if (window.size < minSamples) return
        if (now - lastInferAt < strideMs && lastInferAt != 0L) return
        lastInferAt = now
        runInference(window, finalize = false)
    }

    private suspend fun runInference(samples: FloatArray, finalize: Boolean) {
        _snapshot.update { it.copy(isProcessing = true) }
        val raw = whisperEngine.transcribe(samples, AudioConfig.LANGUAGE_CODE) { partial ->
            _snapshot.update { current ->
                val merged = TranscriptMerger.merge(current.committedText, partial)
                current.copy(
                    committedText = merged.committed,
                    partialText = merged.partial,
                    isProcessing = true,
                )
            }
        }
        _snapshot.update { current ->
            val merged = TranscriptMerger.merge(current.committedText, raw)
            current.copy(
                committedText = if (finalize) merged.display else merged.committed,
                partialText = if (finalize) "" else merged.partial,
                isProcessing = false,
                isListening = !finalize,
                audioLevel = if (finalize) 0f else current.audioLevel,
            )
        }
    }
}
