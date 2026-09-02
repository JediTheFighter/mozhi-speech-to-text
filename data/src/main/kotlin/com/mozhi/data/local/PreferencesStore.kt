package com.mozhi.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mozhi.domain.catalog.SpeechModelCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mozhiStore by preferencesDataStore("mozhi_prefs")

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val selectedModel = stringPreferencesKey("selected_model_id")
    private val geminiKey = stringPreferencesKey("gemini_api_key")

    val selectedModelId: Flow<String> = context.mozhiStore.data.map { prefs ->
        prefs[selectedModel] ?: SpeechModelCatalog.DEFAULT_MODEL_ID
    }

    val geminiApiKey: Flow<String> = context.mozhiStore.data.map { prefs ->
        prefs[geminiKey].orEmpty()
    }

    suspend fun setSelectedModel(id: String) {
        context.mozhiStore.edit { it[selectedModel] = id }
    }

    suspend fun setGeminiApiKey(value: String) {
        context.mozhiStore.edit { it[geminiKey] = value }
    }
}
