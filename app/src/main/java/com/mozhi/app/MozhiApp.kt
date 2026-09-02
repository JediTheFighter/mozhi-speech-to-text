package com.mozhi.app

import android.app.Application
import android.os.Process
import android.util.Log
import com.mozhi.core.common.MozhiLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MozhiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("MozhiSTT", "MozhiApp.onCreate pid=${Process.myPid()}")
        MozhiLog.i("MozhiApp.onCreate package=$packageName")
    }
}
