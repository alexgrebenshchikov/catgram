package com.mobdev.catgram.logging

import android.util.Log

val logger: CatgramLogger = CatgramLoggerStub()

interface CatgramLogger {
    fun d(message: String)
    fun e(message: String)
}

class CatgramLoggerImpl : CatgramLogger {
    override fun d(message: String) {
        Log.d(TAG, message)
    }

    override fun e(message: String) {
        Log.e(TAG, message)
    }
}

class CatgramLoggerStub : CatgramLogger {
    override fun d(message: String) = Unit

    override fun e(message: String) = Unit
}

private const val TAG = "Catgram"