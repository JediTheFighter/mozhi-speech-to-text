package com.mozhi.core.translation

import com.mozhi.domain.model.TranslationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scaffold for a later cloud translator. Do not bind this in [com.mozhi.core.translation.di.TranslationModule]
 * until you add credentials and a real HTTP client.
 */
@Singleton
class CloudTranslationEngine @Inject constructor() : TranslationEngine {
    override val enabled: Boolean = true
    override val name: String = "cloud-placeholder"

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationResult {
        // TODO: POST to your translation API. Keep STT local; only this hop should leave the device.
        return TranslationResult(
            sourceText = text,
            translatedText = text,
            sourceLang = sourceLang,
            targetLang = targetLang,
            engineName = name,
        )
    }
}
