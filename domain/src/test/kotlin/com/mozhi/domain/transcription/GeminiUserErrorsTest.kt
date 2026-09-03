package com.mozhi.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiUserErrorsTest {
    @Test
    fun mapsQuota() {
        assertEquals(
            GeminiUserErrors.Quota,
            GeminiUserErrors.from("HTTP 429 (gemini-3.5-flash-lite): RESOURCE_EXHAUSTED quota"),
        )
    }

    @Test
    fun mapsTimeout() {
        assertEquals(GeminiUserErrors.Timeout, GeminiUserErrors.from("Gemini timed out"))
    }

    @Test
    fun mapsUnknownToGeneric() {
        assertEquals(GeminiUserErrors.Generic, GeminiUserErrors.from("boom"))
    }
}
