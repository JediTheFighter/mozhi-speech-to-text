package com.mozhi.domain.model

data class TranscriptionSnapshot(
    val committedText: String = "",
    val partialText: String = "",
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val audioLevel: Float = 0f,
    val elapsedMillis: Long = 0L,
) {
    val displayText: String
        get() = when {
            committedText.isBlank() -> partialText
            partialText.isBlank() -> committedText
            else -> "$committedText $partialText".trim()
        }
}

enum class ListeningState {
    Idle,
    RequestingPermission,
    Listening,
    Processing,
    Error,
}

data class SpeechSessionError(
    val message: String,
    val cause: Throwable? = null,
)
