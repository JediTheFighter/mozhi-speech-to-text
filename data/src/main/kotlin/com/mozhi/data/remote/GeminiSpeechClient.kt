package com.mozhi.data.remote

import android.util.Base64
import com.mozhi.core.common.AudioConfig
import com.mozhi.core.common.MozhiLog
import com.mozhi.data.di.GeminiApiKey
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
    @GeminiApiKey private val apiKey: String,
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.IO) {
        check(apiKey.isNotBlank()) {
            "GEMINI_API_KEY missing. Add it to local.properties and rebuild."
        }
        val wav = WavEncoder.pcm16Mono(samples, AudioConfig.SAMPLE_RATE_HZ)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val tried = linkedSetOf<String>()
        var model = PRIMARY_MODEL
        var lastError = "Gemini request failed"
        while (tried.add(model)) {
            MozhiLog.i("gemini request model=$model wavBytes=${wav.size} samples=${samples.size}")
            val result = callModel(model, b64)
            if (result.isSuccess) {
                val text = clean(result.getOrThrow())
                MozhiLog.i("gemini ok model=$model chars=${text.length} text='${text.take(80)}'")
                return@withContext text
            }
            lastError = result.exceptionOrNull()?.message ?: lastError
            MozhiLog.w("gemini model=$model failed: $lastError")
            val suggested = suggestedModel(lastError)
            if (suggested != null && suggested !in tried) {
                model = suggested
                continue
            }
            break
        }
        error(lastError)
    }

    private fun callModel(model: String, b64: String): Result<String> = runCatching {
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
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(formatHttpError(response.code, raw, model))
            }
            parseText(raw)
        }
    }

    private fun formatHttpError(code: Int, raw: String, model: String): String {
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val detail = message.ifBlank { raw.take(180) }
        return "HTTP $code ($model): $detail"
    }

    private fun suggestedModel(error: String): String? {
        val match = SUGGESTED_MODEL.find(error) ?: return null
        return match.groupValues[1].substringAfter("models/")
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
        const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        val SUGGESTED_MODEL = Regex("use models/([a-zA-Z0-9._-]+)", RegexOption.IGNORE_CASE)
        const val PROMPT =
            "Transcribe this audio. The speaker is talking in Malayalam. " +
                "Return only the Malayalam (Malayalam script) transcript of what was spoken. " +
                "No English translation, no labels, no markdown."
    }
}
