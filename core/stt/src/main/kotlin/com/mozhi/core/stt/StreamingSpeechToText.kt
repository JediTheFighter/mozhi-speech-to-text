package com.mozhi.core.stt

import com.mozhi.core.audio.AudioChunk
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
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
import java.util.concurrent.atomic.AtomicInteger
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
    private val pushedChunks = AtomicInteger(0)
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
        pushedChunks.set(0)
        _snapshot.value = TranscriptionSnapshot(
            isListening = true,
            debugLine = "session $session started — waiting for mic audio",
        )
        MozhiLog.i("stream start session=$session")
        collectJob?.cancel()
        collectJob = scope.launch(Dispatchers.Default) {
            chunks.collect { chunk ->
                if (stopRequested || session != epoch) return@collect
                val window = appendLocked(chunk)
                maybeTranscribe(scope, session, chunk.timestampMs, window)
            }
        }
    }

    suspend fun push(chunk: AudioChunk) {
        if (stopRequested) return
        val n = pushedChunks.incrementAndGet()
        if (n == 1 || n % 20 == 0) {
            val msg = "mic chunk #$n rms=${"%.4f".format(chunk.rms)} samples=${chunk.samples.size}"
            MozhiLog.i(msg)
            if (n == 1 || n % 40 == 0) {
                _snapshot.update { it.copy(debugLine = msg) }
            }
        }
        chunks.emit(chunk)
    }

    fun stopImmediate() {
        val stopped = epoch
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
            it.copy(
                isListening = false,
                isProcessing = false,
                audioLevel = 0f,
                debugLine = "stopped session=$stopped chunks=${pushedChunks.get()}",
            )
        }
        MozhiLog.i("stream stop session=$stopped chunks=${pushedChunks.get()}")
    }

    private suspend fun appendLocked(chunk: AudioChunk): FloatArray {
        val elapsed = System.currentTimeMillis() - startedAt
        _snapshot.update { current ->
            current.copy(
                audioLevel = (current.audioLevel * 0.45f + chunk.rms * 3.2f).coerceIn(0f, 1f),
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
        if (window.size < minSamples) {
            if (pushedChunks.get() % 20 == 0) {
                val msg = "waiting for audio ${window.size}/$minSamples samples"
                MozhiLog.i(msg)
                _snapshot.update { it.copy(debugLine = msg) }
            }
            return
        }
        if (now - lastInferAt < strideMs && lastInferAt != 0L) return
        if (!inferring.compareAndSet(false, true)) {
            MozhiLog.d("skip infer, previous still running")
            return
        }
        lastInferAt = now
        val copy = window.copyOf()
        var peak = 0f
        for (s in copy) {
            val a = if (s < 0) -s else s
            if (a > peak) peak = a
        }
        val queued =
            "queue infer session=$session samples=${copy.size} (${copy.size / AudioConfig.SAMPLE_RATE_HZ}s) peak=${"%.4f".format(peak)}"
        MozhiLog.i(queued)
        _snapshot.update { it.copy(debugLine = queued) }
        inferJob = scope.launch(Dispatchers.Default) {
            try {
                if (!stopRequested && session == epoch) runInference(copy, session)
            } catch (t: Throwable) {
                MozhiLog.e("infer crashed", t)
                _snapshot.update { it.copy(debugLine = "infer crashed: ${t.message}", isProcessing = false) }
            } finally {
                inferring.set(false)
            }
        }
    }

    private suspend fun runInference(samples: FloatArray, session: Int) {
        if (stopRequested || session != epoch) return
        _snapshot.update { it.copy(isProcessing = true) }
        val started = System.currentTimeMillis()
        val raw = runCatching {
            whisperEngine.transcribe(samples, AudioConfig.LANGUAGE_CODE) { partial ->
                if (stopRequested || session != epoch) return@transcribe
                MozhiLog.i("partial='${partial.take(80)}'")
                _snapshot.update { current ->
                    val merged = TranscriptMerger.merge(current.committedText, partial)
                    current.copy(
                        committedText = merged.committed,
                        partialText = merged.partial,
                        isProcessing = true,
                    )
                }
            }
        }.onFailure {
            MozhiLog.e("transcribe failed", it)
            _snapshot.update { current -> current.copy(debugLine = "transcribe failed: ${it.message}") }
        }.getOrDefault("")
        val elapsed = System.currentTimeMillis() - started
        val done = if (raw.isBlank()) {
            "infer ${elapsed}ms EMPTY text — logcat MozhiSTT / MozhiWhisper"
        } else {
            "infer ${elapsed}ms len=${raw.length} text='${raw.take(80)}'"
        }
        MozhiLog.i("infer done session=$session $done")
        _snapshot.update { it.copy(debugLine = done) }
        if (stopRequested || session != epoch) {
            MozhiLog.w("infer discarded after stop session=$session epoch=$epoch")
            _snapshot.update { it.copy(isProcessing = false) }
            return
        }
        _snapshot.update { current ->
            val merged = TranscriptMerger.merge(current.committedText, raw)
            current.copy(
                committedText = merged.committed,
                partialText = merged.partial,
                isProcessing = false,
            )
        }
    }
}
