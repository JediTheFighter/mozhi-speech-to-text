package com.mozhi.data.repository

import android.content.Context
import com.mozhi.data.local.PreferencesStore
import com.mozhi.data.remote.ModelDownloader
import com.mozhi.domain.catalog.SpeechModelCatalog
import com.mozhi.domain.model.ModelInstallState
import com.mozhi.domain.model.SpeechModel
import com.mozhi.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesStore,
    private val downloader: ModelDownloader,
) : ModelRepository {

    private val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    override val catalog: Flow<List<ModelInstallState>> = combine(
        prefs.selectedModelId,
        progress,
    ) { selected, prog ->
        SpeechModelCatalog.models.map { model ->
            ModelInstallState(
                model = model,
                downloaded = File(localPath(model)).exists(),
                selected = model.id == selected,
                downloadProgress = prog[model.id],
            )
        }
    }

    override suspend fun download(modelId: String) {
        val model = SpeechModelCatalog.byId(modelId)
        val dest = File(localPath(model))
        if (dest.exists() && dest.length() > 1024 * 1024) return
        progress.update { it + (modelId to 0f) }
        try {
            downloader.download(model, dest) { fraction ->
                progress.update { it + (modelId to fraction) }
            }
        } finally {
            progress.update { it - modelId }
        }
    }

    override suspend fun select(modelId: String) {
        prefs.setSelectedModel(modelId)
    }

    override suspend fun selectedModel(): SpeechModel? {
        val id = prefs.selectedModelId.first()
        return SpeechModelCatalog.models.firstOrNull { it.id == id }
    }

    override fun localPath(model: SpeechModel): String {
        return File(context.filesDir, "models/${model.id}.bin").absolutePath
    }
}
