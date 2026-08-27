package com.mobdev.catgram.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.R
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.notifications.NotificationParams
import com.mobdev.catgram.notifications.makeNotification
import java.util.concurrent.TimeUnit

class NewPostsNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app by lazy { applicationContext as CatgramApplication }
    private val userPostsRepository by lazy { app.container.userPostsRepository }

    override suspend fun doWork(): Result {
        return try {
            logger.d("Start new posts notification worker")

            // Skip notification if app is in foreground
            if (app.isAppInForeground) {
                logger.d("App is in foreground, skipping notification")
                return Result.success()
            }

            val lastCheckedTimestamp =
                NewPostsPreferences.getLastCheckedTimestamp(applicationContext) ?: run {
                    NewPostsPreferences.setLastCheckedTimestamp(
                        applicationContext,
                        System.currentTimeMillis()
                    )
                    return Result.success()
                }
            val hasNewPosts = userPostsRepository.hasNewPostsSince(lastCheckedTimestamp)

            if (hasNewPosts) {
                logger.d("New posts found, checking if can send notification")

                if (NewPostsPreferences.canSendNotification(applicationContext)) {
                    if (sendNewPostsNotification()) {
                        NewPostsPreferences.setLastNotificationTimestamp(
                            applicationContext,
                            System.currentTimeMillis()
                        )
                        logger.d("Notification sent for new posts")
                    } else {
                        return Result.success()
                    }
                } else {
                    logger.d("Notification throttled - already sent within 24 hours")
                    return Result.success()
                }
            } else {
                logger.d("No new posts found")
            }

            NewPostsPreferences.setLastCheckedTimestamp(
                applicationContext,
                System.currentTimeMillis()
            )

            Result.success()
        } catch (e: Exception) {
            logger.e("NewPostsNotificationWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun sendNewPostsNotification(): Boolean =
        with(applicationContext) {
            return@with makeNotification(
                this,
                getString(R.string.new_posts_notification_title),
                getString(R.string.new_posts_notification_text),
                NotificationParams(CHANNEL_NAME, CHANNEL_DESC, CHANNEL_ID, NOTIFICATION_ID)
            )
        }

    companion object {
        private val CHANNEL_NAME: CharSequence = "New Posts Notifications"
        private const val CHANNEL_DESC = "Notifications when new cat posts are available"
        private const val CHANNEL_ID = "NEW_POSTS_NOTIFICATION"
        private const val NOTIFICATION_ID = 3
    }
}

fun scheduleNewPostsNotificationWorker(context: Context) {
    logger.d("schedule new posts notification")
    val workRequest = PeriodicWorkRequestBuilder<NewPostsNotificationWorker>(
        2, TimeUnit.HOURS
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "new_posts_notification",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
