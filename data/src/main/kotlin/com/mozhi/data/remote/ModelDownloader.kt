package com.mozhi.data.remote

import com.mozhi.domain.model.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun download(
        model: SpeechModel,
        dest: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val request = Request.Builder().url(model.downloadUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download failed (${response.code}) for ${model.id}")
            }
            val body = response.body ?: error("Empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        if (total > 0) onProgress(readTotal.toFloat() / total)
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        check(tmp.renameTo(dest)) { "Could not move downloaded model into place" }
        onProgress(1f)
    }
}
