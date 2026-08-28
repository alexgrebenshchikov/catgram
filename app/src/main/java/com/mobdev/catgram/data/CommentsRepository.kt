package com.mobdev.catgram.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.AuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

const val MAX_COMMENT_LENGTH = 500

data class Comment(
    val id: String,
    val postId: String,
    val authorUid: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val createdAt: Timestamp?,
)

interface CommentsRepository {
    fun observeComments(
        postId: String,
        limit: Long = 50,
        fromInclusive: CommentCursor? = null,
    ): Flow<List<Comment>>
    suspend fun getOlderComments(
        postId: String,
        before: CommentCursor,
        limit: Long = 50,
    ): CommentsPage
    suspend fun addComment(postId: String, text: String)
    suspend fun deleteComment(postId: String, commentId: String)
    suspend fun getCommentsCount(postId: String): Long
}

data class CommentsPage(
    val items: List<Comment>,
    val hasMore: Boolean,
)

data class CommentCursor(
    val createdAt: Timestamp,
    val id: String,
)

fun Comment.cursorOrNull(): CommentCursor? = createdAt?.let { CommentCursor(it, id) }

class FirebaseCommentsRepository(
    private val authProvider: AuthProvider,
    private val context: Context,
) : CommentsRepository {
    private val firestore = Firebase.firestore

    override fun observeComments(
        postId: String,
        limit: Long,
        fromInclusive: CommentCursor?,
    ): Flow<List<Comment>> = callbackFlow {
        require(postId.isNotBlank()) { "Post id must not be blank" }
        require(limit > 0) { "Comment limit must be positive" }

        val ordered = commentsCollection(postId)
            .orderBy(CREATED_AT, Query.Direction.ASCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        val query = fromInclusive?.let { cursor ->
            ordered.startAt(cursor.createdAt, cursor.id)
        } ?: ordered.limitToLast(limit)
        val registration = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val comments = snapshot?.documents.orEmpty().mapNotNull { it.toComment(postId) }
                trySend(comments)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getOlderComments(
        postId: String,
        before: CommentCursor,
        limit: Long,
    ): CommentsPage {
        require(limit > 0) { "Comment limit must be positive" }
        val snapshot = commentsCollection(postId)
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .startAfter(before.createdAt, before.id)
            .limit(limit)
            .get()
            .await()
        return CommentsPage(
            items = snapshot.documents.mapNotNull { it.toComment(postId) }.reversed(),
            hasMore = snapshot.size().toLong() == limit,
        )
    }

    override suspend fun addComment(postId: String, text: String) {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "Comment must not be empty" }
        require(normalizedText.length <= MAX_COMMENT_LENGTH) {
            "Comment must be at most $MAX_COMMENT_LENGTH characters"
        }

        val user = authProvider.getCurrentUserOrThrow()
        val postRef = firestore.collection(USER_POSTS_COL).document(postId)
        val post = postRef.get().await()
        check(post.exists()) { "Post does not exist" }
        val ownerUid = post.getString(USER_ID).orEmpty()
        check(ownerUid.isNotBlank()) { "Post owner is missing" }

        val authorName = normalizedActivityName(user.displayName)
        val authorAvatarUrl = normalizedActivityUrl(
            authProvider.getAvatarUrl(context)?.toString(),
        )
        val commentRef = commentsCollection(postId).document()
        val batch = firestore.batch()
        batch.set(
            commentRef,
            mapOf(
                "authorUid" to user.uid,
                "authorName" to authorName,
                "authorAvatarUrl" to authorAvatarUrl,
                "text" to normalizedText,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )

        if (ownerUid != user.uid) {
            val activityId = commentActivityDocumentId(postId, commentRef.id)
            val activityRef = firestore.collection(USERS)
                .document(ownerUid)
                .collection(ACTIVITY)
                .document(activityId)
            batch.set(
                activityRef,
                activityDocument(
                    type = ActivityType.COMMENT,
                    actorUid = user.uid,
                    actorName = authorName,
                    actorAvatarUrl = authorAvatarUrl,
                    postId = postId,
                    postPreviewUrl = normalizedActivityUrl(post.getString(URL)),
                    commentId = commentRef.id,
                    // Activity keeps a reference to the comment, not a second
                    // copy of user-authored text that could outlive deletion.
                    commentText = null,
                ),
            )
        }
        batch.commit().await()
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val currentUid = authProvider.getCurrentUserOrThrow().uid
        val postRef = firestore.collection(USER_POSTS_COL).document(postId)
        val commentRef = commentsCollection(postId).document(commentId)
        val post = postRef.get(Source.SERVER).await()
        check(post.exists()) { "Post does not exist" }
        val ownerUid = post.getString(USER_ID).orEmpty()
        check(ownerUid.isNotBlank()) { "Post owner is missing" }
        val comment = commentRef.get(Source.SERVER).await()
            .toObject(FirebaseComment::class.java) ?: return
        check(comment.authorUid == currentUid) { "Cannot delete another user's comment" }

        val batch = firestore.batch()
        batch.delete(commentRef)
        if (ownerUid != currentUid) {
            batch.delete(
                firestore.collection(USERS)
                    .document(ownerUid)
                    .collection(ACTIVITY)
                    .document(commentActivityDocumentId(postId, commentId)),
            )
        }
        batch.commit().await()
    }

    override suspend fun getCommentsCount(postId: String): Long =
        commentsCollection(postId).count().get(AggregateSource.SERVER).await().count

    private fun commentsCollection(postId: String) =
        firestore.collection(USER_POSTS_COL).document(postId).collection(COMMENTS)

    private fun DocumentSnapshot.toComment(postId: String): Comment? {
        val comment = toObject(FirebaseComment::class.java) ?: return null
        return Comment(
            id = id,
            postId = postId,
            authorUid = comment.authorUid,
            authorName = comment.authorName,
            authorAvatarUrl = comment.authorAvatarUrl,
            text = comment.text,
            createdAt = comment.createdAt as? Timestamp,
        )
    }

    private companion object {
        const val COMMENTS = "comments"
        const val CREATED_AT = "createdAt"
        const val USER_ID = "userId"
        const val URL = "url"
        const val USERS = "users"
        const val ACTIVITY = "activity"
    }
}

data class FirebaseComment(
    val authorUid: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val text: String = "",
    val createdAt: Any? = null,
) {
    constructor() : this("", "", null, "", null)
}
