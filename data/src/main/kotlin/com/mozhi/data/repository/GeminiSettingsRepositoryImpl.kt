package com.mozhi.data.repository

import com.mozhi.data.local.PreferencesStore
import com.mozhi.domain.repository.GeminiSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiSettingsRepositoryImpl @Inject constructor(
    private val prefs: PreferencesStore,
) : GeminiSettingsRepository {
    override fun observeApiKey(): Flow<String> = prefs.geminiApiKey

    override suspend fun apiKey(): String = prefs.geminiApiKey.first()

    override suspend fun setApiKey(value: String) {
        prefs.setGeminiApiKey(value.trim())
    }
}
