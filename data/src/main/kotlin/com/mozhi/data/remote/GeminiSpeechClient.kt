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
        val models = modelsToTry()
        MozhiLog.i(
            "gemini request wavBytes=${wav.size} samples=${samples.size} keyLen=${apiKey.length} models=$models",
        )
        var lastError = "Gemini request failed"
        for (model in models) {
            val result = callModel(model, b64)
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

    private fun modelsToTry(): List<String> {
        val listed = listAvailableModels()
        val fromCatalog = PREFERRED.filter { wanted ->
            listed.any { it == wanted || it.endsWith("/$wanted") || it.contains(wanted) }
        }
        if (fromCatalog.isNotEmpty()) return fromCatalog.distinct()
        val flash = listed.map { it.substringAfter("models/") }
            .filter { it.contains("flash", ignoreCase = true) && !it.contains("tts", ignoreCase = true) }
        if (flash.isNotEmpty()) return flash.take(6)
        return PREFERRED
    }

    private fun listAvailableModels(): List<String> {
        cachedModels?.let { return it }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
        val names = runCatching {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    MozhiLog.w("gemini listModels HTTP ${response.code}: ${raw.take(160)}")
                    return@use emptyList()
                }
                val arr = JSONObject(raw).optJSONArray("models") ?: return@use emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val name = arr.optJSONObject(i)?.optString("name").orEmpty()
                        if (name.isNotBlank()) add(name.substringAfter("models/"))
                    }
                }
            }
        }.getOrElse {
            MozhiLog.w("gemini listModels failed: ${it.message}")
            emptyList()
        }
        MozhiLog.i("gemini listModels count=${names.size} sample=${names.take(8)}")
        cachedModels = names
        return names
    }

    @Volatile
    private var cachedModels: List<String>? = null

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val PREFERRED = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-3-flash-preview",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
        )
        const val PROMPT =
            "Transcribe this audio. The speaker is talking in Malayalam. " +
                "Return only the Malayalam (Malayalam script) transcript of what was spoken. " +
                "No English translation, no labels, no markdown."
    }
}
