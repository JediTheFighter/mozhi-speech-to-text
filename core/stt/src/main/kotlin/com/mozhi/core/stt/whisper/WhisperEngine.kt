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
    ): String = withContext(dispatcher) {
        check(ptr != 0L) { "Whisper model is not loaded" }
        var peak = 0f
        var sumSq = 0.0
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSq += s * s
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        MozhiLog.i(
            "whisper decode n=${samples.size} lang=$language peak=${"%.4f".format(peak)} rms=${"%.4f".format(rms)} threads=${preferredThreads()}",
        )
        if (peak < 0.005f) {
            MozhiLog.w("audio looks silent, whisper may return empty")
        }
        WhisperLib.setListener(
            object : WhisperSegmentListener {
                override fun onSegment(text: String, isPartial: Boolean) {
                    if (text.isNotBlank()) onPartial(text.trim())
                }
            },
        )
        val result = WhisperLib.fullTranscribe(ptr, samples, preferredThreads(), language)
        WhisperLib.setListener(null)
        MozhiLog.i("whisper native result='${result.take(160)}' blank=${result.isBlank()}")
        result.trim()
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

    private fun preferredThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            Build.VERSION.SDK_INT >= 31 -> (cores - 2).coerceIn(2, 6)
            else -> (cores - 1).coerceIn(2, 4)
        }
    }
}
