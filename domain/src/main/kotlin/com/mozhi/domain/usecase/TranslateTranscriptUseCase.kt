package com.mozhi.domain.usecase

import com.mozhi.domain.model.TranslationResult
import com.mozhi.domain.repository.TranslationRepository

class TranslateTranscriptUseCase(
    private val translationRepository: TranslationRepository,
) {
    val isAvailable: Boolean get() = translationRepository.isEnabled

    suspend operator fun invoke(text: String): TranslationResult {
        check(translationRepository.isEnabled) {
            "Cloud translation is not wired yet. Implement CloudTranslationEngine."
        }
        return translationRepository.translate(text)
    }
}
