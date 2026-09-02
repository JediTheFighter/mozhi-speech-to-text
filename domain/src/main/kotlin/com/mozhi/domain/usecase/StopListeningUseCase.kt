package com.mozhi.domain.usecase

import com.mozhi.domain.repository.SpeechRepository

class StopListeningUseCase(
    private val speechRepository: SpeechRepository,
) {
    suspend operator fun invoke() {
        speechRepository.stop()
    }
}
