package com.mozhi.domain.catalog

import com.mozhi.domain.model.SpeechModel

object SpeechModelCatalog {
    val models: List<SpeechModel> = listOf(
        SpeechModel(
            id = "ggml-tiny-q5_1",
            displayName = "Whisper Tiny Q5_1",
            description = "Smallest on-device multilingual Whisper. Force language=ml. Best first download (~31 MB).",
            huggingFaceRepo = "ggerganov/whisper.cpp",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
            sizeBytes = 31L * 1024 * 1024,
            quantization = "Q5_1",
            languageHint = "ml",
            recommended = true,
            malayalamFinetuned = false,
        ),
        SpeechModel(
            id = "ggml-base-q5_1",
            displayName = "Whisper Base Q5_1",
            description = "Better Malayalam script fidelity than Tiny, still phone-friendly.",
            huggingFaceRepo = "ggerganov/whisper.cpp",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            sizeBytes = 57L * 1024 * 1024,
            quantization = "Q5_1",
            languageHint = "ml",
            recommended = false,
            malayalamFinetuned = false,
        ),
        SpeechModel(
            id = "ggml-small-q5_1",
            displayName = "Whisper Small Q5_1",
            description = "Highest quality of the bundled GGML downloads. Heavier on RAM/CPU.",
            huggingFaceRepo = "ggerganov/whisper.cpp",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            sizeBytes = 181L * 1024 * 1024,
            quantization = "Q5_1",
            languageHint = "ml",
            recommended = false,
            malayalamFinetuned = false,
        ),
    )

    fun byId(id: String): SpeechModel =
        models.first { it.id == id }

    const val DEFAULT_MODEL_ID = "ggml-tiny-q5_1"
}
