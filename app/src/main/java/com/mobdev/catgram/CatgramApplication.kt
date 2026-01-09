package com.mobdev.catgram

import android.app.Application
import com.mobdev.catgram.data.AppContainer
import com.mobdev.catgram.data.DefaultAppContainer

class CatgramApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}