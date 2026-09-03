package com.mozhi.domain.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptScriptTest {
    @Test
    fun malayalamDoesNotNeedRewrite() {
        assertFalse(TranscriptScript.needsMalayalamRewrite("ഞാൻ ഇന്ന് office-ൽ പോകും"))
        assertTrue(TranscriptScript.hasMalayalam("ഞാൻ ഇന്ന് office-ൽ പോകും"))
    }

    @Test
    fun hindiNeedsRewrite() {
        assertTrue(TranscriptScript.hasDevanagari("मैं कल ऑफिस जाऊंगा"))
        assertTrue(TranscriptScript.needsMalayalamRewrite("मैं कल ऑफिस जाऊंगा"))
    }

    @Test
    fun romanizedManglishNeedsRewrite() {
        assertTrue(TranscriptScript.looksLikeRomanizedMalayalam("njan innale officeil poyi"))
        assertTrue(TranscriptScript.needsMalayalamRewrite("njan innale officeil poyi"))
    }

    @Test
    fun shortEnglishStays() {
        assertFalse(TranscriptScript.needsMalayalamRewrite("OK"))
    }
}
