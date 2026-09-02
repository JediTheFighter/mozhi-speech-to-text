package com.mozhi.domain.repository

import kotlinx.coroutines.flow.Flow

interface GeminiSettingsRepository {
    fun observeApiKey(): Flow<String>
    suspend fun apiKey(): String
    suspend fun setApiKey(value: String)
}
