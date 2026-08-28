package com.mobdev.catgram.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.R
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ui.AppDeepLinks

fun makeNotification(
    context: Context,
    title: String,
    message: String,
    params: NotificationParams,
    target: NotificationTarget? = null,
): Boolean {
    return with(params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                channelId,
                verboseNotificationChannelName,
                importance
            )
            channel.description = verboseNotificationChannelDescription

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

            notificationManager?.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = createPendingIntent(context, target, notificationId)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.star_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(LongArray(0))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            logger.d( "Notification create")
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        } catch (e: SecurityException) {
            logger.e( "Notifications permission not granted: ${e.message}", e)
            false
        }
    }
}

fun createPendingIntent(
    appContext: Context,
    target: NotificationTarget? = null,
    requestCode: Int = DEFAULT_REQUEST_CODE,
): PendingIntent {
    val intent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        target?.let {
            action = Intent.ACTION_VIEW
            data = AppDeepLinks.postUri(it.postId, it.activityId)
            putExtra(AppDeepLinks.EXTRA_POST_ID, it.postId)
            putExtra(AppDeepLinks.EXTRA_ACTIVITY_ID, it.activityId)
        }
    }

    // Flag to detect unsafe launches of intents for Android 12 and higher
    // to improve platform security
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    return PendingIntent.getActivity(
        appContext,
        requestCode,
        intent,
        flags
    )
}

data class NotificationParams(
    val verboseNotificationChannelName: CharSequence,
    val verboseNotificationChannelDescription: String,
    val channelId: String,
    val notificationId: Int
)

data class NotificationTarget(
    val postId: String,
    val activityId: String? = null,
)

private const val DEFAULT_REQUEST_CODE = 0
