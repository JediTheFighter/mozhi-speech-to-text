package com.mozhi.feature.transcribe

import com.mozhi.domain.catalog.SpeechModelCatalog

object MalayalamCopy {
    const val AppSubtitle = "Gemini കൊണ്ട് മലയാളം തിരിച്ചറിയൽ"
    const val Models = "Gemini കീ"
    const val Copy = "എഴുത്ത് പകർത്തുക"
    const val Live = "തത്സമയം"
    const val Transcript = "എഴുത്ത്"
    const val Decoding = "പൊരുത്തപ്പെടുത്തുന്നു…"
    const val PlaceholderIdle = "മലയാളത്തിൽ സംസാരിക്കൂ. തിരിച്ചറിഞ്ഞ വാക്കുകൾ ഇവിടെ തത്സമയം വരും."
    const val PlaceholderListening = "ശബ്ദം തിരിച്ചറിയുന്നു…"
    const val HintListening = "കേൾക്കുന്നു — മലയാളത്തിൽ സംസാരിക്കൂ"
    const val HintNoModel = "ആദ്യം Gemini API കീ നൽകുക"
    const val HintPermission = "മൈക്രോഫോൺ അനുമതി ആവശ്യമാണ്"
    const val HintTap = "മൈക്ക് തൊട്ട് സംസാരിക്കാം"
    const val OpenMicSettings = "ക്രമീകരണത്തിൽ മൈക്രോഫോൺ അനുവദിക്കുക"
    const val LocalWhisper = "ക്ലൗഡ് Gemini Flash"
    const val GeminiEngine = "Gemini Flash"
    const val NoModel = "കീ ഇല്ല"
    const val DialogTitle = "Gemini API കീ"
    const val DialogBody =
        "Google AI Studio-യിൽ നിന്ന് API കീ ഒട്ടിക്കുക. സംസാരം Gemini-ലേക്ക് അയച്ച് വേഗത്തിൽ മലയാളം എഴുത്താക്കും. കീ ഫോണിൽ മാത്രം സൂക്ഷിക്കും."
    const val DialogOk = "സേവ് ചെയ്യുക"
    const val DialogCancel = "പിന്നീട്"
    const val KeyLabel = "API key"
    const val KeyMissing = "API കീ നൽകുക"
    const val LoaderTitle = "ഡൗൺലോഡ് ചെയ്യുന്നു"
    const val LoaderBody = "Whisper Tiny മോഡൽ ലഭ്യമാക്കുന്നു. ദയവായി കാത്തിരിക്കുക."
    const val DownloadFailed = "ഡൗൺലോഡ് പരാജയപ്പെട്ടു. വീണ്ടും ശ്രമിക്കുക."
    const val PermissionDenied = "സംസാരം തിരിച്ചറിയാൻ മൈക്രോഫോൺ അനുമതി വേണം."
    const val StartFailed = "കേൾക്കൽ തുടങ്ങിയില്ല"
    const val DefaultModelId = SpeechModelCatalog.DEFAULT_MODEL_ID
}
