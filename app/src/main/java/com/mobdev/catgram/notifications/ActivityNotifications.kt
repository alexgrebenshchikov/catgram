package com.mobdev.catgram.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Timestamp
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.data.ActivityCursor
import com.mobdev.catgram.data.ActivityItem
import com.mobdev.catgram.data.ActivityRepository
import com.mobdev.catgram.data.ActivityType
import com.mobdev.catgram.data.cursorOrNull
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
                    try {
                        notifyPendingActivity(context, uid, activityRepository)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // Keep the live listener active; the next snapshot or
                        // periodic worker run will retry from the same cursor.
                        logger.e("Activity notification delivery failed: ${e.message}", e)
                    }
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

suspend fun notifyPendingActivity(
    context: Context,
    uid: String,
    activityRepository: ActivityRepository,
): Int = notificationDeliveryMutex.withLock {
    val initialState = ActivityNotificationTracker.read(context, uid)
    if (!initialState.initialized) {
        val latestCursor = activityRepository.getRecentActivity(1)
            .firstOrNull()
            ?.cursorOrNull()
        ActivityNotificationTracker.initialize(context, uid, latestCursor)
        return@withLock 0
    }

    var cursor = initialState.cursor
    var delivered = 0
    var hasMore = true
    while (hasMore) {
        val page = activityRepository.getNewerActivity(
            after = cursor,
            limit = ACTIVITY_NOTIFICATION_PAGE_SIZE,
        )
        if (page.items.isEmpty()) return@withLock delivered

        for (item in page.items) {
            val itemCursor = item.cursorOrNull() ?: continue
            if (!showActivityNotification(context, item)) {
                // Leave the cursor before this item so a later worker run can retry.
                return@withLock delivered
            }
            ActivityNotificationTracker.advance(context, uid, itemCursor)
            cursor = itemCursor
            delivered += 1
        }

        hasMore = page.hasMore
    }
    delivered
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

private val notificationDeliveryMutex = Mutex()

private data class ActivityNotificationState(
    val initialized: Boolean,
    val cursor: ActivityCursor?,
)

private object ActivityNotificationTracker {
    suspend fun read(context: Context, uid: String): ActivityNotificationState {
        val preferences = context.activityNotificationDataStore.data.first()
        val seconds = preferences[cursorSecondsKey(uid)]
        val nanoseconds = preferences[cursorNanosecondsKey(uid)]
        val id = preferences[cursorIdKey(uid)]
        val cursor = if (seconds != null && nanoseconds != null && id != null) {
            ActivityCursor(Timestamp(seconds, nanoseconds), id)
        } else null
        return ActivityNotificationState(
            initialized = preferences[initializedKey(uid)] == true,
            cursor = cursor,
        )
    }

    suspend fun initialize(context: Context, uid: String, cursor: ActivityCursor?) {
        context.activityNotificationDataStore.edit { mutablePreferences ->
            mutablePreferences[initializedKey(uid)] = true
            if (cursor != null) {
                mutablePreferences[cursorSecondsKey(uid)] = cursor.createdAt.seconds
                mutablePreferences[cursorNanosecondsKey(uid)] = cursor.createdAt.nanoseconds
                mutablePreferences[cursorIdKey(uid)] = cursor.id
            } else {
                mutablePreferences.remove(cursorSecondsKey(uid))
                mutablePreferences.remove(cursorNanosecondsKey(uid))
                mutablePreferences.remove(cursorIdKey(uid))
            }
        }
    }

    suspend fun advance(context: Context, uid: String, cursor: ActivityCursor) {
        context.activityNotificationDataStore.edit { mutablePreferences ->
            mutablePreferences[initializedKey(uid)] = true
            mutablePreferences[cursorSecondsKey(uid)] = cursor.createdAt.seconds
            mutablePreferences[cursorNanosecondsKey(uid)] = cursor.createdAt.nanoseconds
            mutablePreferences[cursorIdKey(uid)] = cursor.id
        }
    }

    private fun initializedKey(uid: String) = booleanPreferencesKey("cursor_initialized_$uid")
    private fun cursorSecondsKey(uid: String) = longPreferencesKey("cursor_seconds_$uid")
    private fun cursorNanosecondsKey(uid: String) = intPreferencesKey("cursor_nanos_$uid")
    private fun cursorIdKey(uid: String) = stringPreferencesKey("cursor_id_$uid")
}

const val ACTIVITY_NOTIFICATION_PAGE_SIZE = 50L
private const val ACTIVITY_NOTIFICATION_CHANNEL_ID = "POST_ACTIVITY_NOTIFICATION"
