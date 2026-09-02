package com.mozhi.core.stt.whisper

import android.os.Build
import android.util.Log

interface WhisperSegmentListener {
    fun onSegment(text: String, isPartial: Boolean)
}

internal object WhisperLib {
    init {
        Log.i(TAG, "ABI=${Build.SUPPORTED_ABIS.joinToString()}")
        System.loadLibrary("mozhi-whisper")
    }

    @JvmStatic external fun initContext(modelPath: String): Long
    @JvmStatic external fun freeContext(ptr: Long)
    @JvmStatic external fun setListener(listener: WhisperSegmentListener?)
    @JvmStatic external fun requestAbort()
    @JvmStatic external fun fullTranscribe(
        ptr: Long,
        audio: FloatArray,
        threads: Int,
        language: String,
    ): String
    @JvmStatic external fun systemInfo(): String

    private const val TAG = "MozhiWhisper"
}
