package com.mobdev.catgram.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobdev.catgram.R
import com.mobdev.catgram.TAG
import com.mobdev.catgram.notifications.NotificationParams
import com.mobdev.catgram.notifications.makeNotification

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
            makeNotification(
                this,
                notification.title ?: getString(R.string.default_notification_title),
                notification.body ?: getString(R.string.default_notification_body),
                NotificationParams(channelName, channelDesc, channelId, notificationId)
            )
        }
    }

    private val channelName: CharSequence = "Verbose FCM Notifications"
    private val channelDesc = "Shows notifications whenever message receiver"
    private val channelId = "VERBOSE_NOTIFICATION"
    private val notificationId = 1
}