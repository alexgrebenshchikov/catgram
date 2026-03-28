package com.mobdev.catgram.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.newPostsDataStore: DataStore<Preferences> by preferencesDataStore("new-posts-worker")

object NewPostsPreferences {
    private val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
    private val LAST_NOTIFICATION_TIMESTAMP = longPreferencesKey("last_notification_timestamp")

    private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L

    suspend fun getLastCheckedTimestamp(context: Context): Long? {
        val prefs = context.newPostsDataStore.data.first()
        return prefs[LAST_CHECKED_TIMESTAMP]
    }

    suspend fun setLastCheckedTimestamp(context: Context, timestamp: Long) {
        context.newPostsDataStore.edit { prefs ->
            prefs[LAST_CHECKED_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastNotificationTimestamp(context: Context): Long {
        val prefs = context.newPostsDataStore.data.first()
        return prefs[LAST_NOTIFICATION_TIMESTAMP] ?: 0L
    }

    suspend fun setLastNotificationTimestamp(context: Context, timestamp: Long) {
        context.newPostsDataStore.edit { prefs ->
            prefs[LAST_NOTIFICATION_TIMESTAMP] = timestamp
        }
    }

    suspend fun canSendNotification(context: Context): Boolean {
        val lastNotification = getLastNotificationTimestamp(context)
        val now = System.currentTimeMillis()
        return (now - lastNotification) >= ONE_DAY_MILLIS
    }
}
