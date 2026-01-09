package com.mobdev.catgram

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobdev.catgram.logging.logger
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
                logger.e( "Downloading error")
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
            logger.d( "startCheckForUpdates")
            isUpdatesCheckWasLaunched = true

            ruStoreAppUpdateManager = RuStoreAppUpdateManagerFactory.create(context)
            ruStoreAppUpdateManager
                .getAppUpdateInfo()
                .addOnSuccessListener { appUpdateInfo ->
                    logger.d( "getAppUpdateInfo success")
                    if (appUpdateInfo.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
                        logger.d( "update available ${appUpdateInfo.availableVersionCode}")
                        ruStoreAppUpdateManager.registerListener(installStateUpdateListener)
                        ruStoreAppUpdateManager
                            .startUpdateFlow(appUpdateInfo, AppUpdateOptions.Builder().build())
                            .addOnSuccessListener { resultCode ->
                                if (resultCode == Activity.RESULT_CANCELED) {
                                    // Пользователь отказался от скачивания
                                    logger.d( "startUpdateFlow user cancelled update")
                                }
                                logger.d( "startUpdateFlow success")
                            }
                            .addOnFailureListener { throwable ->
                                logger.e( "startUpdateFlow error: ${throwable.message}")
                            }
                    }
                }
                .addOnFailureListener { throwable ->
                    logger.e( "getAppUpdateInfo error: ${throwable.message}")
                }
        }
    }

    fun completeUpdateRequested() {
        ruStoreAppUpdateManager.completeUpdate()
    }

    fun startReviewFlow(context: Context) {
        if (!isReviewFlowWasStarted) {
            logger.d( "start review flow")
            isReviewFlowWasStarted = true

            viewModelScope.launch {
                val prefs = context.dataStore.data.first()
                if (checkReviewWasShown(prefs)) {
                    return@launch
                }

                val reviewManager = RuStoreReviewManagerFactory.create(context)
                reviewManager.requestReviewFlow()
                    .addOnSuccessListener { reviewInfo ->
                        logger.d( "request review success")
                        reviewManager.launchReviewFlow(reviewInfo)
                            .addOnSuccessListener {
                                logger.d( "review launch success")
                                viewModelScope.launch {
                                    setReviewWasShown(context)
                                }
                            }.addOnFailureListener { throwable ->
                                logger.e( "review launch failure: ${throwable.message}")
                            }
                    }
                    .addOnFailureListener { throwable ->
                        logger.e( "request review failure: ${throwable.message}")
                    }
            }
        }
    }

    fun askForPostNotificationsPermissionIfNeeded(context: Context, askForPermission: () -> Unit) {
        val launchTime = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val prefs = context.dataStore.data.first()
                val firstLaunch = prefs[firstLaunchTimeKey]
                if (firstLaunch != null) {
                    return@launch
                }

                setFirstLaunchedTime(context, launchTime)
                askForPermission()
            } catch (e: Throwable) {
                logger.e("Something went wrong: ${e.message}")
            }
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

    private fun checkReviewWasShown(prefs: Preferences): Boolean {
        prefs[reviewWasShown]?.let {
            return true
        }
        return false
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