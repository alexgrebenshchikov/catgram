package com.mobdev.catgram.worker

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG
import com.mobdev.catgram.notifications.NotificationParams
import com.mobdev.catgram.notifications.makeNotification


class OpenAppReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "do work")
        with(applicationContext) {
            makeNotification(
                this,
                getString(R.string.app_name),
                getString(R.string.reminder_notification_text),
                NotificationParams(channelName, channelDesc, channelId, notificationId)
            )
        }

        return Result.success()
    }

    companion object {
        private val channelName: CharSequence = "Verbose open app reminder Notifications"
        private val channelDesc = "Shows notifications when it's time to remind user to open app"
        private val channelId = "VERBOSE_REMINDER_NOTIFICATION"
        private val notificationId = 2
    }
}