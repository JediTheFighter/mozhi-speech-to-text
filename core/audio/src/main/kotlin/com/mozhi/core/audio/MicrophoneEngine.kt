package com.mozhi.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.mozhi.core.common.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface MicrophoneEngine {
    fun stream(): Flow<AudioChunk>
}

@Singleton
class AudioRecordMicrophoneEngine @Inject constructor() : MicrophoneEngine {
    @SuppressLint("MissingPermission")
    override fun stream(): Flow<AudioChunk> = flow {
        val minBuf = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "Microphone is not available on this device" }
        val frameSamples = AudioConfig.SAMPLE_RATE_HZ / 10
        val bufferSize = maxOf(minBuf, frameSamples * 2 * 8)
        val recorder = openRecorder(bufferSize)
        val shortBuf = ShortArray(frameSamples)
        recorder.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                val read = if (Build.VERSION.SDK_INT >= 23) {
                    recorder.read(shortBuf, 0, shortBuf.size, AudioRecord.READ_NON_BLOCKING)
                } else {
                    recorder.read(shortBuf, 0, shortBuf.size)
                }
                if (read <= 0) {
                    delay(15)
                    continue
                }
                val floats = FloatArray(read)
                var sumSq = 0.0
                for (i in 0 until read) {
                    val v = shortBuf[i] / 32768f
                    floats[i] = v
                    sumSq += v * v
                }
                emit(AudioChunk(floats, sqrt(sumSq / read).toFloat(), System.currentTimeMillis()))
            }
        } finally {
            runCatching {
                recorder.stop()
                recorder.release()
            }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT,
        )
        for (source in sources) {
            val recorder = AudioRecord(
                source,
                AudioConfig.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
        error("Microphone failed to initialize")
    }
}
