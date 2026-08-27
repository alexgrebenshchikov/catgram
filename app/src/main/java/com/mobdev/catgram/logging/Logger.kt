package com.mobdev.catgram.logging

import android.util.Log
import com.mobdev.catgram.BuildConfig

val logger: CatgramLogger = CatgramLoggerImpl()

interface CatgramLogger {
    fun d(message: String)
    fun e(message: String, e: Throwable)
}

class CatgramLoggerImpl : CatgramLogger {
    override fun d(message: String) {
        Log.d(TAG, message)
    }

    override fun e(message: String, e: Throwable) {
        Log.e(TAG, message, e)
    }
}

class CatgramLoggerStub : CatgramLogger {
    override fun d(message: String) = Unit

    override fun e(message: String, e: Throwable) = Unit
}

private const val TAG = "Catgram"
