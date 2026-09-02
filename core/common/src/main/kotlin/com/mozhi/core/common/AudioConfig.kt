package com.mozhi.core.common

object AudioConfig {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_COUNT = 1
    const val WINDOW_SECONDS = 8
    const val STRIDE_SECONDS = 2
    const val OVERLAP_SECONDS = 2
    const val LANGUAGE_CODE = "ml"
    const val MAX_LISTEN_SECONDS = 90
}

object AppDispatchers {
    const val IO = "MozhiIo"
    const val Default = "MozhiDefault"
}
