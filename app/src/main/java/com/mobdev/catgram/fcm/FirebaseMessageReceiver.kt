package com.mobdev.catgram.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobdev.catgram.MainActivity
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG

class FirebaseMessageReceiver : FirebaseMessagingService() {
    init {
        Log.d(TAG, "init FirebaseMessageReceiver")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "message received ${message.notification?.title} ${message.notification?.body}")
        val notification = message.notification
        if (notification != null) {
            makeFCMNotification(
                notification.title ?: getString(R.string.default_notification_title),
                notification.body ?: getString(R.string.default_notification_body)
            )
        }
    }

    private fun makeFCMNotification(
        title: String,
        message: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID,
                VERBOSE_NOTIFICATION_CHANNEL_NAME,
                importance
            )
            channel.description = VERBOSE_NOTIFICATION_CHANNEL_DESCRIPTION

            val notificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

            notificationManager?.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = createPendingIntent(this)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.star_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(LongArray(0))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            Log.d(TAG, "Notification create")
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Notifications permission not granted", e)
        }
    }

    private fun createPendingIntent(appContext: Context): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Flag to detect unsafe launches of intents for Android 12 and higher
        // to improve platform security
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            intent,
            flags
        )
    }
}