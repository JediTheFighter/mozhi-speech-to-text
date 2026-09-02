package com.mozhi.core.stt.whisper

import android.os.Build
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

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
        if (ptr != 0L && loadedPath == modelPath) return@withContext
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
        val loaded = WhisperLib.initContext(modelPath)
        check(loaded != 0L) { "Could not load Whisper model at $modelPath" }
        ptr = loaded
        loadedPath = modelPath
    }

    suspend fun transcribe(
        samples: FloatArray,
        language: String,
        onPartial: (String) -> Unit,
    ): String = withContext(dispatcher) {
        check(ptr != 0L) { "Whisper model is not loaded" }
        WhisperLib.setListener(
            object : WhisperSegmentListener {
                override fun onSegment(text: String, isPartial: Boolean) {
                    if (text.isNotBlank()) onPartial(text.trim())
                }
            },
        )
        val result = WhisperLib.fullTranscribe(ptr, samples, preferredThreads(), language)
        WhisperLib.setListener(null)
        result.trim()
    }

    fun abort() {
        if (ptr != 0L) WhisperLib.requestAbort()
    }

    suspend fun release() = withContext(dispatcher) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
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
