package com.mozhi.domain.transcription

object GeminiUserErrors {
    const val Quota =
        "API പരിധി തീർന്നു. ദയവായി കുറച്ച് കഴിഞ്ഞ് വീണ്ടും ശ്രമിക്കുക."
    const val Timeout =
        "തിരിച്ചറിയൽ സമയം കഴിഞ്ഞു. നെറ്റ്‌വർക്ക് പരിശോധിച്ച് വീണ്ടും ശ്രമിക്കുക."
    const val Network =
        "നെറ്റ്‌വർക്ക് ലഭ്യമല്ല. കണക്ഷൻ പരിശോധിച്ച് വീണ്ടും ശ്രമിക്കുക."
    const val TooShort =
        "റെക്കോർഡിങ് വളരെ ചെറുതാണ്. സംസാരിച്ച ശേഷം നിർത്തുക."
    const val Empty =
        "ഒന്നും തിരിച്ചറിഞ്ഞില്ല. വീണ്ടും സംസാരിച്ച് നോക്കുക."
    const val KeyMissing =
        "API കീ ലഭ്യമല്ല. ആപ്പ് വീണ്ടും ബിൽഡ് ചെയ്യുക."
    const val Mic =
        "മൈക്രോഫോൺ ലഭ്യമായില്ല. അനുമതി പരിശോധിക്കുക."
    const val Generic =
        "തിരിച്ചറിയൽ പരാജയപ്പെട്ടു. ദയവായി വീണ്ടും ശ്രമിക്കുക."

    fun from(raw: String?): String {
        val text = raw.orEmpty().lowercase()
        if (text.isBlank()) return Generic
        return when {
            "429" in text || "resource_exhausted" in text || "quota" in text ||
                "rate limit" in text || "resource exhausted" in text -> Quota
            "timed out" in text || "timeout" in text -> Timeout
            "unable to resolve" in text || "failed to connect" in text ||
                "network" in text || "unreachable" in text || "unknownhost" in text -> Network
            "too short" in text -> TooShort
            "no transcript" in text || "returned no" in text -> Empty
            "api key" in text || "key missing" in text -> KeyMissing
            "mic" in text -> Mic
            else -> Generic
        }
    }
}
