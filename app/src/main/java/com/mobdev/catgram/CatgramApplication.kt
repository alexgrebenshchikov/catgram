package com.mobdev.catgram

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mobdev.catgram.data.AppContainer
import com.mobdev.catgram.data.DefaultAppContainer

class CatgramApplication : Application() {
    lateinit var container: AppContainer

    var isAppInForeground: Boolean = false
        private set

    private var activityReferences = 0

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        registerActivityLifecycleCallbacks(AppLifecycleCallbacks())
    }

    private inner class AppLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            activityReferences++
            isAppInForeground = true
        }

        override fun onActivityStopped(activity: Activity) {
            activityReferences--
            isAppInForeground = activityReferences > 0
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}