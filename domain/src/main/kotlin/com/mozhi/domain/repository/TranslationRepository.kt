package com.mozhi.domain.repository

import com.mozhi.domain.model.TranslationResult

interface TranslationRepository {
    val isEnabled: Boolean
    val engineName: String
    suspend fun translate(text: String, sourceLang: String = "ml", targetLang: String = "en"): TranslationResult
}
