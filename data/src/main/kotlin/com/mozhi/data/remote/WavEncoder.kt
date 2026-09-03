package com.mozhi.data.remote

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    fun pcm16Mono(floats: FloatArray, sampleRate: Int): ByteArray {
        val data = ByteArray(floats.size * 2)
        var o = 0
        for (f in floats) {
            val s = (f.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            data[o++] = (s.toInt() and 0xff).toByte()
            data[o++] = ((s.toInt() shr 8) and 0xff).toByte()
        }
        val out = ByteArrayOutputStream(44 + data.size)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + data.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(data.size)
        out.write(header.array())
        out.write(data)
        return out.toByteArray()
    }
}
