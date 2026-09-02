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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    private var inbox = Channel<AudioChunk>(
        capacity = 64,
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
        inferring.set(false)
        inferJob = null
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
        inbox.close()
        val channel = Channel<AudioChunk>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        inbox = channel
        collectJob = scope.launch(Dispatchers.Default) {
            for (chunk in channel) {
                if (stopRequested || session != epoch) continue
                appendLocked(chunk)
                maybeLiveTranscribe(scope, session)
            }
        }
        scope.launch {
            delay(1_500)
            if (!stopRequested && session == epoch && pushedChunks.get() == 0) {
                val msg = "NO MIC FRAMES after 1.5s — capture stalled on this phone"
                MozhiLog.e(msg)
                _snapshot.update { it.copy(debugLine = msg) }
            }
        }
    }

    fun fail(message: String) {
        MozhiLog.e(message)
        _snapshot.update { it.copy(debugLine = message, isProcessing = false) }
    }

    fun push(chunk: AudioChunk) {
        if (stopRequested) return
        val n = pushedChunks.incrementAndGet()
        if (n == 1 || n % 10 == 0) {
            val msg = "mic chunk #$n rms=${"%.4f".format(chunk.rms)} samples=${chunk.samples.size}"
            MozhiLog.i(msg)
            _snapshot.update { it.copy(debugLine = msg) }
        }
        val result = inbox.trySend(chunk)
        if (result.isClosed) {
            MozhiLog.w("audio inbox closed, dropped chunk #$n")
        }
    }

    fun stopImmediate() {
        collectJob?.cancel()
        collectJob = null
        inbox.close()
        stopRequested = true
        MozhiLog.i("capture stopped session=$epoch chunks=${pushedChunks.get()} pcm=$pcmSize")
    }

    suspend fun flushAndStop() {
        val session = epoch
        stopImmediate()
        val leftover = mutex.withLock { pcm.copyOf(pcmSize) }
        _snapshot.update {
            it.copy(
                isListening = false,
                isProcessing = true,
                debugLine = "decoding ${leftover.size / AudioConfig.SAMPLE_RATE_HZ}s (keep the app open)…",
            )
        }
        MozhiLog.i("flush begin session=$session leftover=${leftover.size}")
        whisperEngine.clearAbort()
        val minFlush = AudioConfig.SAMPLE_RATE_HZ
        if (leftover.size >= minFlush) {
            val clip = loudestWindow(leftover, 3)
            try {
                MozhiLog.i("flush infer samples=${clip.size} (${clip.size / AudioConfig.SAMPLE_RATE_HZ}s)")
                runInference(clip, session)
            } catch (t: Throwable) {
                MozhiLog.e("flush infer crashed", t)
                _snapshot.update { it.copy(debugLine = "flush infer crashed: ${t.message}") }
            }
        } else {
            MozhiLog.i("flush skip leftover=${leftover.size} (need >=1s)")
        }
        epoch += 1
        pcmSize = 0
        inferJob = null
        inferring.set(false)
        _snapshot.update {
            it.copy(isListening = false, isProcessing = false, audioLevel = 0f)
        }
        MozhiLog.i("flush done session=$session text='${_snapshot.value.displayText.take(80)}'")
    }

    private fun loudestWindow(samples: FloatArray, seconds: Int): FloatArray {
        val keep = (AudioConfig.SAMPLE_RATE_HZ * seconds).coerceAtMost(samples.size)
        if (samples.size <= keep) {
            MozhiLog.i("energy window using full buffer samples=${samples.size}")
            return samples
        }
        val hop = AudioConfig.SAMPLE_RATE_HZ / 10
        var bestStart = 0
        var bestEnergy = -1.0
        var start = 0
        while (start + keep <= samples.size) {
            var energy = 0.0
            var i = start
            val end = start + keep
            while (i < end) {
                val v = samples[i]
                energy += v * v
                i++
            }
            if (energy > bestEnergy) {
                bestEnergy = energy
                bestStart = start
            }
            start += hop
        }
        MozhiLog.i(
            "energy window startMs=${bestStart * 1000 / AudioConfig.SAMPLE_RATE_HZ} lenMs=${keep * 1000 / AudioConfig.SAMPLE_RATE_HZ} energy=${"%.4f".format(bestEnergy / keep)}",
        )
        return samples.copyOfRange(bestStart, bestStart + keep)
    }

    private suspend fun appendLocked(chunk: AudioChunk) {
        val elapsed = System.currentTimeMillis() - startedAt
        _snapshot.update { current ->
            current.copy(
                audioLevel = (current.audioLevel * 0.45f + chunk.rms * 3.2f).coerceIn(0f, 1f),
                elapsedMillis = elapsed,
            )
        }
        mutex.withLock {
            val incoming = chunk.samples
            if (pcmSize + incoming.size > pcm.size) {
                val keep = pcm.size - incoming.size
                if (keep > 0) System.arraycopy(pcm, pcmSize - keep, pcm, 0, keep)
                pcmSize = keep.coerceAtLeast(0)
            }
            System.arraycopy(incoming, 0, pcm, pcmSize, incoming.size)
            pcmSize += incoming.size
        }
    }

    private fun maybeLiveTranscribe(scope: CoroutineScope, session: Int) {
        if (stopRequested || session != epoch) return
        val minLive = AudioConfig.SAMPLE_RATE_HZ * 2
        if (pcmSize < minLive) return
        if (!inferring.compareAndSet(false, true)) return
        val clip = try {
            FloatArray(minLive).also { dest ->
                System.arraycopy(pcm, pcmSize - minLive, dest, 0, minLive)
            }
        } catch (t: Throwable) {
            inferring.set(false)
            return
        }
        inferJob = scope.launch(Dispatchers.Default) {
            try {
                if (session == epoch && !stopRequested) {
                    MozhiLog.i("live infer samples=${clip.size}")
                    runInference(clip, session)
                }
            } catch (t: Throwable) {
                MozhiLog.e("live infer crashed", t)
            } finally {
                inferring.set(false)
            }
        }
    }

    private suspend fun runInference(samples: FloatArray, session: Int) {
        if (session != epoch) {
            MozhiLog.w("infer skipped stale session=$session epoch=$epoch")
            return
        }
        _snapshot.update { it.copy(isProcessing = true) }
        val started = System.currentTimeMillis()
        val raw = runCatching {
            whisperEngine.transcribe(samples, AudioConfig.LANGUAGE_CODE) { partial ->
                if (session != epoch) return@transcribe
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
        if (session != epoch) {
            MozhiLog.w("infer discarded stale session=$session epoch=$epoch")
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
