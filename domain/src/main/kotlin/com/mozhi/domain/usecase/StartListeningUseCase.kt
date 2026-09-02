package com.mozhi.domain.usecase

import com.mozhi.domain.repository.SpeechRepository

class StartListeningUseCase(
    private val speechRepository: SpeechRepository,
) {
    suspend operator fun invoke() {
        speechRepository.ensureEngineReady()
        speechRepository.start()
    }
}
