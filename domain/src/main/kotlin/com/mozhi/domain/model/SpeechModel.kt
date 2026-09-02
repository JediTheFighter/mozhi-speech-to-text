package com.mozhi.domain.model

data class SpeechModel(
    val id: String,
    val displayName: String,
    val description: String,
    val huggingFaceRepo: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val quantization: String,
    val languageHint: String,
    val recommended: Boolean,
    val malayalamFinetuned: Boolean,
) {
    val sizeLabel: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 100) "${mb.toInt()} MB" else "%.0f MB".format(mb)
        }
}

data class ModelInstallState(
    val model: SpeechModel,
    val downloaded: Boolean,
    val selected: Boolean,
    val downloadProgress: Float? = null,
)
