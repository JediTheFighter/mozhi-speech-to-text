package com.mozhi.core.common

import android.util.Log

object MozhiLog {
    const val TAG = "MozhiSTT"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, error: Throwable? = null) = Log.w(TAG, message, error)
    fun e(message: String, error: Throwable? = null) = Log.e(TAG, message, error)
}
