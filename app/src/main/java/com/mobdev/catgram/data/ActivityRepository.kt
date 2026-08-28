package com.mobdev.catgram.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.AuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class ActivityType {
    LIKE,
    COMMENT,
}

data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val actorUid: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val postId: String,
    val postPreviewUrl: String?,
    val commentId: String?,
    val commentText: String?,
    val createdAt: Timestamp?,
    val readAt: Timestamp?,
) {
    val isUnread: Boolean get() = readAt == null
}

interface ActivityRepository {
    fun observeActivity(limit: Long = 50): Flow<List<ActivityItem>>
    suspend fun getRecentActivity(limit: Long = 50): List<ActivityItem>
    suspend fun getOlderActivity(before: Timestamp, limit: Long = 50): ActivityPage
    suspend fun markRead(activityId: String)
    suspend fun markAllRead()
}

data class ActivityPage(
    val items: List<ActivityItem>,
    val hasMore: Boolean,
)

class FirebaseActivityRepository(
    private val authProvider: AuthProvider,
) : ActivityRepository {
    private val firestore = Firebase.firestore

    override fun observeActivity(limit: Long): Flow<List<ActivityItem>> = callbackFlow {
        require(limit > 0) { "Activity limit must be positive" }
        val registration = activityCollection()
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toActivityItem() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getRecentActivity(limit: Long): List<ActivityItem> {
        require(limit > 0) { "Activity limit must be positive" }
        return activityCollection()
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .mapNotNull { it.toActivityItem() }
    }

    override suspend fun getOlderActivity(before: Timestamp, limit: Long): ActivityPage {
        require(limit > 0) { "Activity limit must be positive" }
        val snapshot = activityCollection()
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .whereLessThan(CREATED_AT, before)
            .limit(limit)
            .get()
            .await()
        return ActivityPage(
            items = snapshot.documents.mapNotNull { it.toActivityItem() },
            hasMore = snapshot.size().toLong() == limit,
        )
    }

    override suspend fun markRead(activityId: String) {
        activityCollection().document(activityId)
            .update(READ_AT, FieldValue.serverTimestamp())
            .await()
    }

    override suspend fun markAllRead() {
        val unread = activityCollection()
            .whereEqualTo(READ_AT, null)
            .limit(MAX_MARK_ALL_BATCH.toLong())
            .get()
            .await()
        if (unread.isEmpty) return

        val batch = firestore.batch()
        unread.documents.forEach { document ->
            batch.update(document.reference, READ_AT, FieldValue.serverTimestamp())
        }
        batch.commit().await()
    }

    private fun activityCollection(): CollectionReference {
        val uid = authProvider.getCurrentUserOrThrow().uid
        return firestore.collection(USERS).document(uid).collection(ACTIVITY)
    }

    private fun DocumentSnapshot.toActivityItem(): ActivityItem? {
        val record = toObject(FirebaseActivity::class.java) ?: return null
        return ActivityItem(
            id = id,
            type = record.type,
            actorUid = record.actorUid,
            actorName = record.actorName,
            actorAvatarUrl = record.actorAvatarUrl,
            postId = record.postId,
            postPreviewUrl = record.postPreviewUrl,
            commentId = record.commentId,
            commentText = record.commentText,
            createdAt = record.createdAt as? Timestamp,
            readAt = record.readAt as? Timestamp,
        )
    }

    private companion object {
        const val USERS = "users"
        const val ACTIVITY = "activity"
        const val CREATED_AT = "createdAt"
        const val READ_AT = "readAt"
        const val MAX_MARK_ALL_BATCH = 400
    }
}

data class FirebaseActivity(
    val type: ActivityType = ActivityType.LIKE,
    val actorUid: String = "",
    val actorName: String = "",
    val actorAvatarUrl: String? = null,
    val postId: String = "",
    val postPreviewUrl: String? = null,
    val commentId: String? = null,
    val commentText: String? = null,
    val createdAt: Any? = null,
    val readAt: Any? = null,
) {
    constructor() : this(ActivityType.LIKE, "", "", null, "", null, null, null, null, null)
}

internal const val MAX_ACTIVITY_NAME_LENGTH = 100
internal const val MAX_ACTIVITY_URL_LENGTH = 2048

internal fun normalizedActivityName(displayName: String?): String = displayName
    .orEmpty()
    .trim()
    .ifBlank { "Someone" }
    .take(MAX_ACTIVITY_NAME_LENGTH)

internal fun normalizedActivityUrl(url: String?): String? = url
    ?.takeIf(String::isNotBlank)
    ?.take(MAX_ACTIVITY_URL_LENGTH)

internal fun likeActivityDocumentId(actorUid: String, postId: String): String =
    "like:$actorUid:$postId"

internal fun commentActivityDocumentId(postId: String, commentId: String): String =
    "comment:$postId:$commentId"

internal fun activityDocument(
    type: ActivityType,
    actorUid: String,
    actorName: String,
    actorAvatarUrl: String?,
    postId: String,
    postPreviewUrl: String?,
    commentId: String? = null,
    commentText: String? = null,
): Map<String, Any?> = mapOf(
    "type" to type.name,
    "actorUid" to actorUid,
    "actorName" to actorName,
    "actorAvatarUrl" to actorAvatarUrl,
    "postId" to postId,
    "postPreviewUrl" to postPreviewUrl,
    "commentId" to commentId,
    "commentText" to commentText,
    "createdAt" to FieldValue.serverTimestamp(),
    "readAt" to null,
)
