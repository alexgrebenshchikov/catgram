package com.mobdev.catgram.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.data.ActivityItem
import com.mobdev.catgram.data.ActivityRepository
import com.mobdev.catgram.data.ActivityType
import com.mobdev.catgram.logging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.activityNotificationDataStore: DataStore<Preferences> by preferencesDataStore(
    "activity-notifications",
)

class ActivityNotificationCoordinator(
    private val context: Context,
    private val authProvider: AuthProvider,
    private val activityRepository: ActivityRepository,
) {
    private var observeJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return
        val uid = authProvider.getCurrentUser()?.uid ?: return
        observeJob = scope.launch {
            try {
                activityRepository.observeActivity(ACTIVITY_NOTIFICATION_PAGE_SIZE).collect {
                    notifyUnseenActivity(context, uid, it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("Activity notification observer failed: ${e.message}", e)
            }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }
}

suspend fun notifyUnseenActivity(
    context: Context,
    uid: String,
    items: List<ActivityItem>,
): Int {
    val newItems = ActivityNotificationTracker.claim(context, uid, items)
    newItems.forEach { item -> showActivityNotification(context, item) }
    return newItems.size
}

private fun showActivityNotification(context: Context, item: ActivityItem): Boolean {
    val message = when (item.type) {
        ActivityType.LIKE -> context.getString(R.string.activity_liked_post, item.actorName)
        ActivityType.COMMENT -> context.getString(
            R.string.activity_commented_post,
            item.actorName,
        )
    }
    return makeNotification(
        context = context,
        title = context.getString(R.string.activity_screen_title),
        message = message,
        params = NotificationParams(
            context.getString(R.string.activity_notification_channel_name),
            context.getString(R.string.activity_notification_channel_description),
            ACTIVITY_NOTIFICATION_CHANNEL_ID,
            item.id.hashCode().and(Int.MAX_VALUE),
        ),
        target = NotificationTarget(item.postId, item.id),
    )
}

private object ActivityNotificationTracker {
    private const val MAX_TRACKED_EVENTS = 200
    private val mutex = Mutex()

    suspend fun claim(
        context: Context,
        uid: String,
        items: List<ActivityItem>,
    ): List<ActivityItem> = mutex.withLock {
        val timestampedItems = items
            .filter { it.createdAt != null }
            .sortedBy { it.createdAt?.toDate()?.time }
        val currentTokens = timestampedItems.map { it.notificationToken() }.toSet()
        val initializedKey = booleanPreferencesKey("initialized_$uid")
        val eventsKey = stringSetPreferencesKey("events_$uid")
        val preferences = context.activityNotificationDataStore.data.first()
        val wasInitialized = preferences[initializedKey] == true
        val previousTokens = preferences[eventsKey].orEmpty()
        val newItems = if (wasInitialized) {
            timestampedItems.filter { it.notificationToken() !in previousTokens }
        } else {
            emptyList()
        }

        context.activityNotificationDataStore.edit { mutablePreferences ->
            val retainedOlderTokens = previousTokens
                .asSequence()
                .filterNot(currentTokens::contains)
                .toList()
                .takeLast((MAX_TRACKED_EVENTS - currentTokens.size).coerceAtLeast(0))
                .toSet()
            mutablePreferences[initializedKey] = true
            mutablePreferences[eventsKey] = retainedOlderTokens + currentTokens
        }
        newItems
    }

    private fun ActivityItem.notificationToken(): String {
        val timestamp = requireNotNull(createdAt)
        return "$id:${timestamp.seconds}:${timestamp.nanoseconds}"
    }
}

const val ACTIVITY_NOTIFICATION_PAGE_SIZE = 50L
private const val ACTIVITY_NOTIFICATION_CHANNEL_ID = "POST_ACTIVITY_NOTIFICATION"
