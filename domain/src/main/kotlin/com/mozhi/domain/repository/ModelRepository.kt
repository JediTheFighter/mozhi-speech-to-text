package com.mozhi.domain.repository

import com.mozhi.domain.model.ModelInstallState
import com.mozhi.domain.model.SpeechModel
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    val catalog: Flow<List<ModelInstallState>>
    suspend fun download(modelId: String)
    suspend fun select(modelId: String)
    suspend fun selectedModel(): SpeechModel?
    fun localPath(model: SpeechModel): String
}
