package com.mobdev.catgram.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobdev.catgram.R
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.notifications.NotificationParams
import com.mobdev.catgram.notifications.makeNotification

class FirebaseMessageReceiver : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        logger.d( "Refreshed FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
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