package com.mozhi.feature.transcribe

import com.mozhi.domain.catalog.SpeechModelCatalog

object MalayalamCopy {
    const val AppSubtitle = "ഓൺലൈൻ അല്ലാതെ മലയാളം തിരിച്ചറിയൽ"
    const val Models = "മോഡലുകൾ"
    const val Copy = "എഴുത്ത് പകർത്തുക"
    const val Live = "തത്സമയം"
    const val Transcript = "എഴുത്ത്"
    const val Decoding = "പൊരുത്തപ്പെടുത്തുന്നു…"
    const val PlaceholderIdle = "മലയാളത്തിൽ സംസാരിക്കൂ. തിരിച്ചറിഞ്ഞ വാക്കുകൾ ഇവിടെ തത്സമയം വരും."
    const val PlaceholderListening = "ശബ്ദം തിരിച്ചറിയുന്നു…"
    const val HintListening = "കേൾക്കുന്നു — മലയാളത്തിൽ സംസാരിക്കൂ"
    const val HintNoModel = "ആദ്യം Whisper മോഡൽ ഡൗൺലോഡ് ചെയ്യുക"
    const val HintPermission = "മൈക്രോഫോൺ അനുമതി ആവശ്യമാണ്"
    const val HintTap = "മൈക്ക് തൊട്ട് സംസാരിക്കാം"
    const val HintSilent = "മൈക്കിൽ ശബ്ദം കിട്ടുന്നില്ല. എമുലേറ്റർ Extended controls-ൽ Microphone ഓണാക്കുക."
    const val OpenMicSettings = "ക്രമീകരണത്തിൽ മൈക്രോഫോൺ അനുവദിക്കുക"
    const val LocalWhisper = "പ്രാദേശിക Whisper"
    const val NoModel = "മോഡൽ ഇല്ല"
    const val DialogTitle = "Whisper മോഡൽ ആവശ്യമാണ്"
    const val DialogBody =
        "മലയാളം സംസാരം ഫോണിൽ തന്നെ തിരിച്ചറിയാൻ Whisper Tiny മോഡൽ (ഏകദേശം 31 MB) ഡൗൺലോഡ് ചെയ്യണം. ഇത് Hugging Face-ൽ നിന്ന് whisper.cpp GGML ആയി ലഭിക്കും."
    const val DialogOk = "ഡൗൺലോഡ് ചെയ്യുക"
    const val DialogCancel = "പിന്നീട്"
    const val LoaderTitle = "ഡൗൺലോഡ് ചെയ്യുന്നു"
    const val LoaderBody = "Whisper Tiny മോഡൽ ലഭ്യമാക്കുന്നു. ദയവായി കാത്തിരിക്കുക."
    const val DownloadFailed = "ഡൗൺലോഡ് പരാജയപ്പെട്ടു. വീണ്ടും ശ്രമിക്കുക."
    const val PermissionDenied = "സംസാരം തിരിച്ചറിയാൻ മൈക്രോഫോൺ അനുമതി വേണം."
    const val StartFailed = "കേൾക്കൽ തുടങ്ങിയില്ല"
    const val DefaultModelId = SpeechModelCatalog.DEFAULT_MODEL_ID
}
