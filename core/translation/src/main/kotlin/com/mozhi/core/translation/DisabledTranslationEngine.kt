package com.mozhi.core.translation

import com.mozhi.domain.model.TranslationResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisabledTranslationEngine @Inject constructor() : TranslationEngine {
    override val enabled: Boolean = false
    override val name: String = "none"
    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationResult {
        error("Translation is disabled. Bind CloudTranslationEngine when a provider key is available.")
    }
}
