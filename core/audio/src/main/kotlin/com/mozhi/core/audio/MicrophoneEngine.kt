package com.mozhi.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mozhi.core.common.AudioConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface MicrophoneEngine {
    fun stream(): Flow<AudioChunk>
}

@Singleton
class AudioRecordMicrophoneEngine @Inject constructor() : MicrophoneEngine {
    @SuppressLint("MissingPermission")
    override fun stream(): Flow<AudioChunk> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuf > 0) { "Microphone is not available on this device" }
        val frameSamples = AudioConfig.SAMPLE_RATE_HZ / 10
        val bufferSize = maxOf(minBuf, frameSamples * 2 * 8)
        val recorder = openRecorder(bufferSize)
        recorder.startRecording()
        val reader = Thread({
            val shortBuf = ShortArray(frameSamples)
            try {
                while (!Thread.currentThread().isInterrupted) {
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
                    val result = trySend(AudioChunk(floats, rms, System.currentTimeMillis()))
                    if (result.isClosed) break
                }
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
            }
        }, "mozhi-mic")
        reader.start()
        awaitClose {
            reader.interrupt()
            runCatching {
                recorder.stop()
                recorder.release()
            }
            reader.join(400)
        }
    }.buffer(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufferSize: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
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
