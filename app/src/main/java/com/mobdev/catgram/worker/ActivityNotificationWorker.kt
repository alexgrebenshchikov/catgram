package com.mobdev.catgram.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.notifications.notifyPendingActivity
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class ActivityNotificationWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val app by lazy { applicationContext as CatgramApplication }

    override suspend fun doWork(): Result {
        val user = app.container.authProvider.getCurrentUser() ?: return Result.success()
        if (app.isAppInForeground) return Result.success()

        return try {
            app.container.activityRepository.redactLegacyCommentBodies()
            notifyPendingActivity(
                context = applicationContext,
                uid = user.uid,
                activityRepository = app.container.activityRepository,
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e("ActivityNotificationWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}

fun scheduleActivityNotificationWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val workRequest = PeriodicWorkRequestBuilder<ActivityNotificationWorker>(
        15,
        TimeUnit.MINUTES,
    )
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        ACTIVITY_NOTIFICATION_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest,
    )
}

private const val ACTIVITY_NOTIFICATION_WORK_NAME = "activity_notifications"
