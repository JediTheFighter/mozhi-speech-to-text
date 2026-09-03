package com.mozhi.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {
    @Test
    fun pcm16MonoWritesRiffHeaderAndSampleCount() {
        val samples = FloatArray(160) { 0.25f }
        val wav = WavEncoder.pcm16Mono(samples, 16_000)
        assertEquals(44 + samples.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val rate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16_000, rate)
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(samples.size * 2, dataSize)
        assertTrue(wav[44] != 0.toByte() || wav[45] != 0.toByte())
    }
}
