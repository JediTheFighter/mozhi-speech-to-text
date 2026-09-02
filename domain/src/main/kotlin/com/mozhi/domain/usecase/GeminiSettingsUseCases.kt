package com.mozhi.domain.usecase

import com.mozhi.domain.repository.GeminiSettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveGeminiApiKeyUseCase(
    private val settings: GeminiSettingsRepository,
) {
    operator fun invoke(): Flow<String> = settings.observeApiKey()
}

class SaveGeminiApiKeyUseCase(
    private val settings: GeminiSettingsRepository,
) {
    suspend operator fun invoke(value: String) {
        settings.setApiKey(value.trim())
    }
}
