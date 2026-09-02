package com.mozhi.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMergerTest {
    @Test
    fun mergesOverlappingWindows() {
        val result = TranscriptMerger.merge(
            committed = "നമസ്കാരം എന്റെ പേര്",
            incoming = "എന്റെ പേര് അരുൺ",
        )
        assertTrue(result.display.contains("അരുൺ"))
        assertTrue(result.committed.isNotBlank())
    }

    @Test
    fun usesIncomingWhenCommittedEmpty() {
        val result = TranscriptMerger.merge("", "വണക്കം")
        assertEquals("", result.committed)
        assertEquals("വണക്കം", result.partial)
    }

    @Test
    fun ignoresDuplicateContainedPartial() {
        val result = TranscriptMerger.merge(
            committed = "ഇന്ന് കാലാവസ്ഥ നല്ലതാണ്",
            incoming = "കാലാവസ്ഥ നല്ലതാണ്",
        )
        assertEquals("ഇന്ന് കാലാവസ്ഥ നല്ലതാണ്", result.display)
    }
}
