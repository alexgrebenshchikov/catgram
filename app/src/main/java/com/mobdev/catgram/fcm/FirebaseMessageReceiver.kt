package com.mobdev.catgram.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobdev.catgram.TAG

class FirebaseMessageReceiver: FirebaseMessagingService() {
    init {
        Log.d(TAG, "init FirebaseMessageReceiver")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "message received ${message.notification?.title} ${message.notification?.body}")
    }
}