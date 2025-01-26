package com.mobdev.catgram

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import java.util.concurrent.TimeUnit

class MainViewModel : ViewModel() {
    private lateinit var ruStoreAppUpdateManager: RuStoreAppUpdateManager

    private val _events = MutableSharedFlow<Event>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    private val installStateUpdateListener = InstallStateUpdateListener { installState ->
        when (installState.installStatus) {
            InstallStatus.DOWNLOADED -> {
                _events.tryEmit(Event.UpdateCompleted)
            }

            InstallStatus.DOWNLOADING -> {
                val totalBytes = installState.totalBytesToDownload
                val bytesDownloaded = installState.bytesDownloaded

                // Здесь можно отобразить прогресс скачивания
            }

            InstallStatus.FAILED -> {
                Log.e(TAG, "Downloading error")
            }
        }
    }

    private var isUpdatesCheckWasLaunched = false
    private var isReviewFlowWasStarted = false

    override fun onCleared() {
        super.onCleared()
        ruStoreAppUpdateManager.unregisterListener(installStateUpdateListener)
    }

    fun startCheckForUpdates(context: Context) {
        if (!isUpdatesCheckWasLaunched) {
            Log.d(TAG, "startCheckForUpdates")
            isUpdatesCheckWasLaunched = true

            ruStoreAppUpdateManager = RuStoreAppUpdateManagerFactory.create(context)
            ruStoreAppUpdateManager
                .getAppUpdateInfo()
                .addOnSuccessListener { appUpdateInfo ->
                    Log.d(TAG, "getAppUpdateInfo success")
                    if (appUpdateInfo.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
                        Log.d(TAG, "update available ${appUpdateInfo.availableVersionCode}")
                        ruStoreAppUpdateManager.registerListener(installStateUpdateListener)
                        ruStoreAppUpdateManager
                            .startUpdateFlow(appUpdateInfo, AppUpdateOptions.Builder().build())
                            .addOnSuccessListener { resultCode ->
                                if (resultCode == Activity.RESULT_CANCELED) {
                                    // Пользователь отказался от скачивания
                                    Log.d(TAG, "startUpdateFlow user cancelled update")
                                }
                                Log.d(TAG, "startUpdateFlow success")
                            }
                            .addOnFailureListener { throwable ->
                                Log.e(TAG, "startUpdateFlow error", throwable)
                            }
                    }
                }
                .addOnFailureListener { throwable ->
                    Log.e(TAG, "getAppUpdateInfo error", throwable)
                }
        }
    }

    fun completeUpdateRequested() {
        ruStoreAppUpdateManager.completeUpdate()
    }

    fun startReviewFlow(context: Context) {
        if (!isReviewFlowWasStarted) {
            Log.d(TAG, "start review flow")
            isReviewFlowWasStarted = true

            val launchTime = System.currentTimeMillis()
            viewModelScope.launch {
                val prefs = context.dataStore.data.first()
                if (!checkShowReviewDelayMet(prefs, context, launchTime)) {
                    return@launch
                }

                val reviewManager = RuStoreReviewManagerFactory.create(context)
                reviewManager.requestReviewFlow()
                    .addOnSuccessListener { reviewInfo ->
                        Log.d(TAG, "request review success")
                        reviewManager.launchReviewFlow(reviewInfo)
                            .addOnSuccessListener {
                                Log.d(TAG, "review launch success")
                            }.addOnFailureListener { throwable ->
                                Log.e(TAG, "review launch failure", throwable)
                            }
                    }
                    .addOnFailureListener { throwable ->
                        Log.e(TAG, "request review failure", throwable)
                    }
            }
        }
    }

    fun askForPostNotificationsPermissionIfNeeded(context: Context, askForPermission: () -> Unit) {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            prefs[firstLaunchTimeKey]?.let { _ ->
                return@launch
            }
            askForPermission()
        }
    }

    private suspend fun setFirstLaunchedTime(context: Context, newValue: Long) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(firstLaunchTimeKey, newValue)
            }
        }
    }

    private suspend fun setReviewWasShown(context: Context) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(reviewWasShown, true)
            }
        }
    }

    private suspend fun checkShowReviewDelayMet(
        prefs: Preferences,
        context: Context,
        launchTime: Long
    ): Boolean {
        prefs[firstLaunchTimeKey]?.let { time ->
            val timeFromFirstStartMs = launchTime - time
            val timeFromFirstStartHours = TimeUnit.MILLISECONDS.toHours(timeFromFirstStartMs)
            if (timeFromFirstStartHours < REVIEW_SHOW_DELAY_HOURS) {
                return false
            }
        } ?: run {
            setFirstLaunchedTime(context, launchTime)
            return false
        }

        return true
    }

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("common")
        private val firstLaunchTimeKey = longPreferencesKey("first_launch_time")
        private val reviewWasShown = booleanPreferencesKey("review_was_shown")
        private const val REVIEW_SHOW_DELAY_HOURS = 48
    }
}

sealed class Event {
    data object UpdateCompleted : Event()
}