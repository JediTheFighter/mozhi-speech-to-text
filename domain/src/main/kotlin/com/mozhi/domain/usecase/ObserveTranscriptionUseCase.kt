package com.mozhi.domain.usecase

import com.mozhi.domain.model.TranscriptionSnapshot
import com.mozhi.domain.repository.SpeechRepository
import kotlinx.coroutines.flow.Flow

class ObserveTranscriptionUseCase(
    private val speechRepository: SpeechRepository,
) {
    operator fun invoke(): Flow<TranscriptionSnapshot> = speechRepository.transcription
}
