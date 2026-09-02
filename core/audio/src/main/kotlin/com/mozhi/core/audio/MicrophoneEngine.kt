package com.mozhi.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface MicrophoneEngine {
    fun stream(): Flow<AudioChunk>
}

/**
 * Create, start, and read [AudioRecord] on one dedicated thread.
 * Vivo/OEM HALs often start recording from the UI/Default thread then stall
 * forever on [AudioRecord.read] from a second thread — that matches logs where
 * the recorder starts (source 6) but Whisper never sees samples.
 */
@Singleton
class AudioRecordMicrophoneEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : MicrophoneEngine {
    @SuppressLint("MissingPermission")
    override fun stream(): Flow<AudioChunk> = callbackFlow {
        val stopFlag = AtomicBoolean(false)
        val recorderRef = AtomicReference<AudioRecord?>(null)
        val started = CompletableDeferred<Unit>()
        val reader = Thread({
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minBuf > 0) { "Microphone is not available on this device" }
                val frameSamples = AudioConfig.SAMPLE_RATE_HZ / 10
                val bufferSize = maxOf(minBuf, frameSamples * 2 * 8)
                MozhiLog.i("AudioRecord minBuf=$minBuf frame=$frameSamples buffer=$bufferSize")
                Log.i(AUDIORECORD_TAG, "mozhi open minBuf=$minBuf frame=$frameSamples")
                val recorder = openWorkingRecorder(bufferSize, frameSamples)
                recorderRef.set(recorder)
                if (!started.isCompleted) started.complete(Unit)
                val shortBuf = ShortArray(frameSamples)
                var frames = 0
                var emptyReads = 0
                while (!stopFlag.get() && !Thread.currentThread().isInterrupted) {
                    val read = recorder.read(
                        shortBuf,
                        0,
                        shortBuf.size,
                        AudioRecord.READ_BLOCKING,
                    )
                    if (read <= 0) {
                        emptyReads++
                        if (emptyReads == 1 || emptyReads % 25 == 0) {
                            MozhiLog.w("AudioRecord read=$read emptyCount=$emptyReads")
                            Log.w(AUDIORECORD_TAG, "mozhi read=$read empty=$emptyReads")
                        }
                        if (read == AudioRecord.ERROR_DEAD_OBJECT ||
                            read == AudioRecord.ERROR_INVALID_OPERATION
                        ) {
                            throw IllegalStateException("AudioRecord died read=$read")
                        }
                        continue
                    }
                    emptyReads = 0
                    val floats = FloatArray(read)
                    var sumSq = 0.0
                    var peak = 0f
                    var intPeak = 0
                    for (i in 0 until read) {
                        val sample = shortBuf[i]
                        val mag = if (sample < 0) -sample else sample.toInt()
                        if (mag > intPeak) intPeak = mag
                        val v = sample / 32768f
                        floats[i] = v
                        sumSq += v * v
                        val a = if (v < 0) -v else v
                        if (a > peak) peak = a
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    frames++
                    if (frames == 1 || frames % 10 == 0) {
                        val msg = "mic frame=$frames read=$read rms=${"%.4f".format(rms)} peak=${"%.4f".format(peak)} intPeak=$intPeak"
                        MozhiLog.i(msg)
                        Log.i(AUDIORECORD_TAG, "mozhi $msg")
                    }
                    val result = trySend(AudioChunk(floats, rms, System.currentTimeMillis()))
                    if (result.isClosed) break
                }
                MozhiLog.i("mic reader loop ended frames=$frames")
            } catch (t: Throwable) {
                MozhiLog.e("mic capture failed", t)
                Log.e(AUDIORECORD_TAG, "mozhi capture failed", t)
                if (!started.isCompleted) started.completeExceptionally(t)
                close(t)
            } finally {
                recorderRef.getAndSet(null)?.let { rec ->
                    runCatching { rec.stop() }
                    runCatching { rec.release() }
                }
            }
        }, "mozhi-mic")
        reader.start()
        try {
            withTimeout(8_000) { started.await() }
        } catch (t: Throwable) {
            stopFlag.set(true)
            runCatching { recorderRef.get()?.stop() }
            close(t)
        }
        awaitClose {
            MozhiLog.i("mic awaitClose")
            stopFlag.set(true)
            runCatching { recorderRef.get()?.stop() }
            reader.join(1_500)
        }
    }.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @SuppressLint("MissingPermission")
    private fun openWorkingRecorder(bufferSize: Int, frameSamples: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        )
        for (source in sources) {
            val recorder = buildRecorder(source, bufferSize) ?: continue
            try {
                recorder.startRecording()
            } catch (t: Throwable) {
                MozhiLog.w("audio source $source start failed", t)
                recorder.release()
                continue
            }
            MozhiLog.i(
                "probing source=$source state=${recorder.state} recording=${recorder.recordingState}",
            )
            Log.i(AUDIORECORD_TAG, "mozhi probe source=$source recording=${recorder.recordingState}")
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                MozhiLog.w("source $source not RECORDSTATE_RECORDING")
                runCatching { recorder.stop() }
                recorder.release()
                continue
            }
            if (probeHasFrames(recorder, frameSamples)) {
                MozhiLog.i("using audio source=$source (delivered samples)")
                Log.i(AUDIORECORD_TAG, "mozhi using source=$source")
                return recorder
            }
            MozhiLog.w("source $source produced no samples in probe window")
            runCatching { recorder.stop() }
            recorder.release()
        }
        MozhiLog.w("no source passed probe; falling back to MIC without probe")
        val last = buildRecorder(MediaRecorder.AudioSource.MIC, bufferSize)
            ?: error("Microphone failed to initialize")
        last.startRecording()
        return last
    }

    @SuppressLint("MissingPermission")
    private fun buildRecorder(source: Int, bufferSize: Int): AudioRecord? {
        val recorder = try {
            if (Build.VERSION.SDK_INT >= 31) {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AudioConfig.SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setContext(context)
                    .build()
            } else {
                AudioRecord(
                    source,
                    AudioConfig.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }
        } catch (t: Throwable) {
            MozhiLog.w("audio source $source build failed", t)
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            MozhiLog.w("audio source $source failed to init state=${recorder.state}")
            recorder.release()
            return null
        }
        return recorder
    }

    @SuppressLint("MissingPermission")
    private fun probeHasFrames(recorder: AudioRecord, frameSamples: Int): Boolean {
        val buf = ShortArray(frameSamples)
        val deadline = SystemClock.elapsedRealtime() + 450
        while (SystemClock.elapsedRealtime() < deadline) {
            val n = recorder.read(buf, 0, buf.size, AudioRecord.READ_NON_BLOCKING)
            if (n > 0) {
                MozhiLog.i("probe read=$n firstSample=${buf[0]}")
                return true
            }
            Thread.sleep(20)
        }
        return false
    }

    private companion object {
        const val AUDIORECORD_TAG = "AudioRecord"
    }
}
