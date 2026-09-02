package com.mozhi.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
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
        MozhiLog.i("AudioRecord minBuf=$minBuf frame=$frameSamples buffer=$bufferSize")
        val recorder = openRecorder(bufferSize)
        MozhiLog.i("AudioRecord started state=${recorder.state} recording=${recorder.recordingState}")
        recorder.startRecording()
        MozhiLog.i("AudioRecord recordingState=${recorder.recordingState}")
        var frames = 0
        var emptyReads = 0
        val reader = Thread({
            val shortBuf = ShortArray(frameSamples)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read <= 0) {
                        emptyReads++
                        if (emptyReads == 1 || emptyReads % 50 == 0) {
                            MozhiLog.w("AudioRecord read=$read emptyCount=$emptyReads")
                        }
                        continue
                    }
                    emptyReads = 0
                    val floats = FloatArray(read)
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val v = shortBuf[i] / 32768f
                        floats[i] = v
                        sumSq += v * v
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    frames++
                    if (frames == 1 || frames % 25 == 0) {
                        MozhiLog.d("mic frame=$frames read=$read rms=${"%.4f".format(rms)}")
                    }
                    val result = trySend(AudioChunk(floats, rms, System.currentTimeMillis()))
                    if (!result.isSuccess && frames % 25 == 0) {
                        MozhiLog.w("mic trySend dropped frame=$frames closed=${result.isClosed}")
                    }
                    if (result.isClosed) break
                }
            } catch (t: SecurityException) {
                MozhiLog.e("mic security", t)
            } catch (t: IllegalStateException) {
                MozhiLog.e("mic illegal state", t)
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
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED,
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
            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                MozhiLog.i("using audio source=$source")
                return recorder
            }
            MozhiLog.w("audio source $source failed to init")
            recorder.release()
        }
        error("Microphone failed to initialize")
    }
}
