package com.mozhi.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mozhi.core.common.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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
        val frameSamples = AudioConfig.SAMPLE_RATE_HZ / 10 // 100ms
        val bufferSize = maxOf(minBuf, frameSamples * 2 * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioConfig.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone failed to initialize" }
        val shortBuf = ShortArray(frameSamples)
        recorder.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) continue
                val floats = FloatArray(read)
                var sumSq = 0.0
                for (i in 0 until read) {
                    val v = shortBuf[i] / 32768f
                    floats[i] = v
                    sumSq += v * v
                }
                val rms = sqrt(sumSq / read).toFloat()
                emit(AudioChunk(floats, rms, System.currentTimeMillis()))
            }
        } finally {
            runCatching {
                recorder.stop()
                recorder.release()
            }
        }
    }.flowOn(Dispatchers.IO)
}
