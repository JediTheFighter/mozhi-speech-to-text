package com.mozhi.data.stt

import com.mozhi.core.audio.AudioChunk
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
import com.mozhi.data.remote.GeminiSpeechClient
import com.mozhi.domain.model.TranscriptionSnapshot
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class GeminiStreamingSpeechToText @Inject constructor(
    private val gemini: GeminiSpeechClient,
) {
    private val _snapshot = MutableStateFlow(TranscriptionSnapshot())
    val snapshot: StateFlow<TranscriptionSnapshot> = _snapshot.asStateFlow()

    private var inbox = Channel<AudioChunk>(64, BufferOverflow.DROP_OLDEST)
    private val mutex = Mutex()
    private val capacity = AudioConfig.SAMPLE_RATE_HZ * max(AudioConfig.WINDOW_SECONDS, 12)
    private val pcm = FloatArray(capacity)
    private var pcmSize = 0
    private var collectJob: Job? = null
    private var liveJob: Job? = null
    private var lastLiveAt = 0L
    private val inFlight = AtomicBoolean(false)
    private val pushed = AtomicInteger(0)
    @Volatile private var stopRequested = false
    @Volatile private var epoch = 0

    fun start(scope: CoroutineScope) {
        epoch += 1
        val session = epoch
        stopRequested = false
        inFlight.set(false)
        pcmSize = 0
        lastLiveAt = 0L
        pushed.set(0)
        _snapshot.value = TranscriptionSnapshot(
            isListening = true,
            debugLine = "Gemini listening — speak Malayalam",
        )
        MozhiLog.i("gemini stream start session=$session")
        collectJob?.cancel()
        inbox.close()
        val channel = Channel<AudioChunk>(64, BufferOverflow.DROP_OLDEST)
        inbox = channel
        collectJob = scope.launch(Dispatchers.Default) {
            for (chunk in channel) {
                if (stopRequested || session != epoch) continue
                append(chunk)
                maybeLive(scope, session)
            }
        }
    }

    fun push(chunk: AudioChunk) {
        if (stopRequested) return
        val n = pushed.incrementAndGet()
        if (n == 1 || n % 10 == 0) {
            val msg = "mic chunk #$n rms=${"%.4f".format(chunk.rms)}"
            MozhiLog.i(msg)
            _snapshot.update { it.copy(debugLine = msg, audioLevel = chunk.rms.coerceIn(0f, 1f) * 3f) }
        }
        inbox.trySend(chunk)
    }

    fun fail(message: String) {
        MozhiLog.e(message)
        _snapshot.update { it.copy(debugLine = message, isProcessing = false) }
    }

    private suspend fun append(chunk: AudioChunk) {
        _snapshot.update {
            it.copy(
                audioLevel = (it.audioLevel * 0.45f + chunk.rms * 3.2f).coerceIn(0f, 1f),
                isListening = true,
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

    private suspend fun maybeLive(scope: CoroutineScope, session: Int) {
        if (stopRequested || session != epoch) return
        val now = System.currentTimeMillis()
        if (now - lastLiveAt < LIVE_INTERVAL_MS && lastLiveAt != 0L) return
        val copy = mutex.withLock {
            if (pcmSize < AudioConfig.SAMPLE_RATE_HZ) return
            if (!inFlight.compareAndSet(false, true)) return
            lastLiveAt = now
            val start = (pcmSize - LIVE_WINDOW_SAMPLES).coerceAtLeast(0)
            pcm.copyOfRange(start, pcmSize)
        }
        if (peak(copy) < MIN_SPEECH_PEAK) {
            inFlight.set(false)
            return
        }
        liveJob = scope.launch(Dispatchers.IO) {
            try {
                if (session != epoch || stopRequested) return@launch
                MozhiLog.i("gemini live samples=${copy.size}")
                _snapshot.update { it.copy(isProcessing = true, debugLine = "Gemini transcribing…") }
                val text = gemini.transcribe(copy)
                if (session != epoch) return@launch
                _snapshot.update {
                    it.copy(
                        partialText = text,
                        isProcessing = false,
                        debugLine = "live '${text.take(60)}'",
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                MozhiLog.e("gemini live failed", t)
                _snapshot.update { it.copy(debugLine = "Gemini: ${t.message}", isProcessing = false) }
            } finally {
                inFlight.set(false)
            }
        }
    }

    suspend fun flushAndStop() {
        val session = epoch
        collectJob?.cancel()
        collectJob = null
        inbox.close()
        stopRequested = true
        val leftover = mutex.withLock { pcm.copyOf(pcmSize) }
        MozhiLog.i("gemini flush leftover=${leftover.size} inFlight=${inFlight.get()}")
        _snapshot.update {
            it.copy(isListening = false, isProcessing = true, debugLine = "Gemini final pass…")
        }
        withTimeoutOrNull(20_000) { liveJob?.join() }
        if (leftover.size >= AudioConfig.SAMPLE_RATE_HZ / 2) {
            try {
                val text = gemini.transcribe(leftover)
                MozhiLog.i("gemini flush text='${text.take(120)}'")
                if (session == epoch) {
                    _snapshot.update {
                        it.copy(
                            committedText = text,
                            partialText = "",
                            isListening = false,
                            isProcessing = false,
                            audioLevel = 0f,
                            debugLine = "done '${text.take(80)}'",
                        )
                    }
                }
            } catch (t: Throwable) {
                MozhiLog.e("gemini flush failed", t)
                _snapshot.update {
                    it.copy(
                        isProcessing = false,
                        isListening = false,
                        debugLine = "Gemini: ${t.message}",
                    )
                }
            }
        } else {
            _snapshot.update { it.copy(isListening = false, isProcessing = false, audioLevel = 0f) }
        }
        epoch += 1
        pcmSize = 0
        inFlight.set(false)
    }

    private fun peak(samples: FloatArray): Float {
        var p = 0f
        for (s in samples) p = max(p, abs(s))
        return p
    }

    private companion object {
        const val LIVE_INTERVAL_MS = 1_800L
        val LIVE_WINDOW_SAMPLES = AudioConfig.SAMPLE_RATE_HZ * 8
        const val MIN_SPEECH_PEAK = 0.02f
    }
}
