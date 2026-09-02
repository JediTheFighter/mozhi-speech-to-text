package com.mozhi.data.remote

import android.util.Base64
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
import com.mozhi.domain.repository.GeminiSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiSpeechClient @Inject constructor(
    private val settings: GeminiSettingsRepository,
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.IO) {
        val key = settings.apiKey()
        check(key.isNotBlank()) { "Gemini API key missing" }
        val wav = WavEncoder.pcm16Mono(samples, AudioConfig.SAMPLE_RATE_HZ)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        MozhiLog.i("gemini request wavBytes=${wav.size} samples=${samples.size} b64=${b64.length}")
        var lastError = "Gemini request failed"
        for (model in MODELS) {
            val result = callModel(key, model, b64)
            if (result.isSuccess) {
                val text = clean(result.getOrThrow())
                MozhiLog.i("gemini ok model=$model chars=${text.length} text='${text.take(80)}'")
                return@withContext text
            }
            lastError = result.exceptionOrNull()?.message ?: lastError
            MozhiLog.w("gemini model=$model failed: $lastError")
        }
        error(lastError)
    }

    private fun callModel(key: String, model: String, b64: String): Result<String> = runCatching {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", PROMPT))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "audio/wav")
                                        .put("data", b64),
                                ),
                            ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0)
                    .put("maxOutputTokens", 1024),
            )
            .toString()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${raw.take(180)}")
            }
            parseText(raw)
        }
    }

    private fun parseText(raw: String): String {
        val root = JSONObject(raw)
        val parts = root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            out.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        return out.toString()
    }

    private fun clean(text: String): String =
        text.trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .joinToString(" ")
            .trim()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
        )
        const val PROMPT =
            "Transcribe this audio. The speaker is talking in Malayalam. " +
                "Return only the Malayalam (Malayalam script) transcript of what was spoken. " +
                "No English translation, no labels, no markdown."
    }
}
