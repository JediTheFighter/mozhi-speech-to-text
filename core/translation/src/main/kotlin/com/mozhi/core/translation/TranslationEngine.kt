package com.mozhi.core.translation

import com.mozhi.domain.model.TranslationResult

/**
 * Plug a cloud vendor here later (Google Cloud Translation, Azure, or a custom LLM).
 * The rest of the app talks only to [com.mozhi.domain.repository.TranslationRepository].
 */
interface TranslationEngine {
    val enabled: Boolean
    val name: String
    suspend fun translate(text: String, sourceLang: String, targetLang: String): TranslationResult
}
