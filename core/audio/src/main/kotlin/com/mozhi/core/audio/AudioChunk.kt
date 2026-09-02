package com.mozhi.core.audio

data class AudioChunk(
    val samples: FloatArray,
    val rms: Float,
    val timestampMs: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioChunk && samples.contentEquals(other.samples) && rms == other.rms

    override fun hashCode(): Int = 31 * samples.contentHashCode() + rms.hashCode()
}
