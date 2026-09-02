package com.mozhi.core.stt.whisper

import android.os.Build
import com.mozhi.core.common.MozhiLog
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class WhisperEngine @Inject constructor() {
    @Volatile
    private var ptr: Long = 0L
    @Volatile
    private var loadedPath: String? = null
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "whisper-engine").apply { priority = Thread.NORM_PRIORITY + 1 }
    }.asCoroutineDispatcher()

    val isReady: Boolean get() = ptr != 0L

    suspend fun load(modelPath: String) = withContext(dispatcher) {
        if (ptr != 0L && loadedPath == modelPath) {
            MozhiLog.i("whisper already loaded path=$modelPath")
            return@withContext
        }
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
        MozhiLog.i("whisper loading $modelPath")
        val loaded = WhisperLib.initContext(modelPath)
        check(loaded != 0L) { "Could not load Whisper model at $modelPath" }
        ptr = loaded
        loadedPath = modelPath
        MozhiLog.i("whisper ready sys=${WhisperLib.systemInfo()}")
    }

    suspend fun transcribe(
        samples: FloatArray,
        language: String,
        onPartial: (String) -> Unit,
    ): String {
        MozhiLog.i("whisper waiting for engine n=${samples.size}")
        return withContext(dispatcher) {
            check(ptr != 0L) { "Whisper model is not loaded" }
        var peak = 0f
        var sumSq = 0.0
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSq += s * s
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        val boosted = amplify(samples, peak)
        MozhiLog.i(
            "whisper decode n=${samples.size} lang=$language peak=${"%.4f".format(peak)} rms=${"%.4f".format(rms)} gain=${"%.2f".format(boosted.second)} boostedPeak=${"%.4f".format(boosted.third)} threads=${preferredThreads()}",
        )
        if (peak < 0.02f) {
            MozhiLog.w("mic level is very low (peak=${"%.4f".format(peak)}); applying gain=${"%.2f".format(boosted.second)}")
        }
        WhisperLib.setListener(
            object : WhisperSegmentListener {
                override fun onSegment(text: String, isPartial: Boolean) {
                    if (text.isNotBlank()) onPartial(text.trim())
                }
            },
        )
        val result = WhisperLib.fullTranscribe(ptr, boosted.first, preferredThreads(), language)
        WhisperLib.setListener(null)
            MozhiLog.i("whisper native result='${result.take(160)}' blank=${result.isBlank()}")
            result.trim()
        }
    }

    fun abort() {
        MozhiLog.i("whisper abort requested ready=$isReady")
        if (ptr != 0L) WhisperLib.requestAbort()
    }

    fun clearAbort() {
        WhisperLib.clearAbort()
    }

    suspend fun release() = withContext(dispatcher) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
            loadedPath = null
        }
    }

    private fun amplify(samples: FloatArray, peak: Float): Triple<FloatArray, Float, Float> {
        if (peak < 1e-6f) return Triple(samples, 1f, peak)
        val target = 0.5f
        if (peak >= 0.2f) return Triple(samples, 1f, peak)
        val gain = (target / peak).coerceAtMost(6f)
        val out = FloatArray(samples.size) { i ->
            (samples[i] * gain).coerceIn(-1f, 1f)
        }
        return Triple(out, gain, (peak * gain).coerceAtMost(1f))
    }

    private fun preferredThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 1).coerceIn(2, 4)
    }
}
