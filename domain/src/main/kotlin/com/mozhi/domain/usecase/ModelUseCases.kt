package com.mozhi.domain.usecase

import com.mozhi.domain.repository.ModelRepository

class DownloadSpeechModelUseCase(
    private val modelRepository: ModelRepository,
) {
    suspend operator fun invoke(modelId: String) = modelRepository.download(modelId)
}

class SelectSpeechModelUseCase(
    private val modelRepository: ModelRepository,
) {
    suspend operator fun invoke(modelId: String) = modelRepository.select(modelId)
}

class ObserveModelCatalogUseCase(
    private val modelRepository: ModelRepository,
) {
    operator fun invoke() = modelRepository.catalog
}
