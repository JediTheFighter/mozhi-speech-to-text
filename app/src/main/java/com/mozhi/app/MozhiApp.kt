package com.mozhi.app

import android.app.Application
import android.os.Process
import android.util.Log
import com.mozhi.core.common.MozhiLog
import com.mozhi.domain.repository.GeminiSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MozhiApp : Application() {
    @Inject lateinit var geminiSettings: GeminiSettingsRepository

    override fun onCreate() {
        super.onCreate()
        Log.i("MozhiSTT", "MozhiApp.onCreate pid=${Process.myPid()}")
        MozhiLog.i("MozhiApp.onCreate package=$packageName")
        val baked = BuildConfig.GEMINI_API_KEY
        if (baked.isNotBlank()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (geminiSettings.apiKey().isBlank()) {
                    geminiSettings.setApiKey(baked)
                    MozhiLog.i("seeded Gemini API key from BuildConfig")
                }
            }
        }
    }
}
