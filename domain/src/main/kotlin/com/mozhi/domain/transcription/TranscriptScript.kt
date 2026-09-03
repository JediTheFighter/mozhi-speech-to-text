package com.mozhi.domain.transcription

object TranscriptScript {
    fun hasMalayalam(text: String): Boolean = text.any { it in MALAYALAM }

    fun hasDevanagari(text: String): Boolean = text.any { it in DEVANAGARI }

    fun looksLikeRomanizedMalayalam(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 4) return false
        if (hasMalayalam(text) || hasDevanagari(text)) return false
        return letters.all { it.code < 128 }
    }

    fun needsMalayalamRewrite(text: String): Boolean =
        text.isNotBlank() && (hasDevanagari(text) || looksLikeRomanizedMalayalam(text))

    private val MALAYALAM = '\u0D00'..'\u0D7F'
    private val DEVANAGARI = '\u0900'..'\u097F'
}
