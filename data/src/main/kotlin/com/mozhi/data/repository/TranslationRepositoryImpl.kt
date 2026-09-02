package com.mozhi.data.repository

import com.mozhi.core.translation.TranslationEngine
import com.mozhi.domain.model.TranslationResult
import com.mozhi.domain.repository.TranslationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val engine: TranslationEngine,
) : TranslationRepository {
    override val isEnabled: Boolean get() = engine.enabled
    override val engineName: String get() = engine.name

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationResult = engine.translate(text, sourceLang, targetLang)
}
