package com.mobdev.catgram.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import com.mobdev.catgram.worker.NewPostsPreferences
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface UserPostsRepository {
    suspend fun getNextUserPostsDataPage(
        pageSize: Int,
        showOnlyMyPosts: Boolean,
    ): List<CatsUserPostData>

    suspend fun addUserPost(url: String, text: String, context: Context)
    suspend fun getUserPost(postId: String): CatsUserPostData?
    suspend fun deleteUserPost(postId: String)
    suspend fun hasNewPostsSince(timestampMillis: Long): Boolean
    fun reset()
}

class PostDeletionRequiresConnectionException(cause: Throwable) :
    IllegalStateException("Post deletion requires an internet connection", cause)

class FirebaseUserPostsRepository(
    private val authProvider: AuthProvider,
    private val context: Context,
) : UserPostsRepository {
    private val firestore = Firebase.firestore
    private val userPostsColRef = firestore.collection(USER_POSTS_COL)
    private val paginationMutex = Mutex()
    private var lastFetchedPost: DocumentSnapshot? = null

    override suspend fun getNextUserPostsDataPage(
        pageSize: Int,
        showOnlyMyPosts: Boolean,
    ): List<CatsUserPostData> = paginationMutex.withLock {
        val lastPost = lastFetchedPost
        lastPost?.let {
            getNextPage(pageSize.toLong(), it, showOnlyMyPosts)
        } ?: getFirstPage(pageSize.toLong(), showOnlyMyPosts)
    }

    override suspend fun addUserPost(
        url: String,
        text: String,
        context: Context,
    ) {
        val currentUser = authProvider.getCurrentUserOrThrow()
        val avatarUrl = authProvider.getAvatarUrl(context)?.toString()
        addUserPost(
            FirebaseUserPost(
                userId = currentUser.uid,
                url = url,
                text = text,
                displayName = currentUser.displayName ?: UNKNOWN_USER,
                avatarUrl = avatarUrl,
                createdAt = FieldValue.serverTimestamp()
            )
        )
    }

    override suspend fun getUserPost(postId: String): CatsUserPostData? {
        val document = userPostsColRef.document(postId).get().await()
        val post = document.toObject(FirebaseUserPost::class.java) ?: return null
        if (post.deletingAt != null) return null
        return post.toCatsUserPostData(document.id)
    }

    override suspend fun deleteUserPost(postId: String) {
        try {
            val currentUser = authProvider.getCurrentUserOrThrow()
            val postRef = userPostsColRef.document(postId)
            val post = postRef.get(Source.SERVER).await().toObject(FirebaseUserPost::class.java)
                ?: return
            check(post.userId == currentUser.uid) { "Cannot delete another user's post" }

            // Marking the post first makes deletion resumable and lets security
            // rules reject new comments before cleanup begins.
            if (post.deletingAt == null) {
                postRef.update(DELETING_AT, FieldValue.serverTimestamp()).await()
            }

            deleteCommentsInPages(postId, currentUser.uid)
            deleteActivityInPages(postId, currentUser.uid)

            firestore.batch().apply {
                delete(postRef)
                delete(firestore.collection(LIKES).document(postId))
                delete(
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection(FAVOURITES_SUBCOLLECTION)
                    .document(favouriteDocumentId(postId, FavouriteItemType.USER_POST))
                )
            }.commit().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                throw PostDeletionRequiresConnectionException(e)
            }
            throw e
        }
    }

    private suspend fun deleteCommentsInPages(postId: String, ownerUid: String) {
        val comments = userPostsColRef.document(postId).collection(COMMENTS)
        val activity = firestore.collection(USERS).document(ownerUid).collection(ACTIVITY)
        while (true) {
            val page = comments.limit(COMMENT_DELETE_PAGE_SIZE.toLong())
                .get(Source.SERVER)
                .await()
            if (page.isEmpty) return

            firestore.batch().apply {
                page.documents.forEach { comment ->
                    delete(comment.reference)
                    delete(activity.document(commentActivityDocumentId(postId, comment.id)))
                }
            }.commit().await()
        }
    }

    private suspend fun deleteActivityInPages(postId: String, ownerUid: String) {
        val activity = firestore.collection(USERS).document(ownerUid).collection(ACTIVITY)
        while (true) {
            val page = activity.whereEqualTo(POST_ID, postId)
                .limit(ACTIVITY_DELETE_PAGE_SIZE.toLong())
                .get(Source.SERVER)
                .await()
            if (page.isEmpty) return

            firestore.batch().apply {
                page.documents.forEach { delete(it.reference) }
            }.commit().await()
        }
    }

    override fun reset() {
        lastFetchedPost = null
    }

    override suspend fun hasNewPostsSince(timestampMillis: Long): Boolean {
        val firestoreTimestamp = Timestamp(java.util.Date(timestampMillis))
        val snapshot = userPostsColRef
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .whereGreaterThan(CREATED_AT, firestoreTimestamp)
            .limit(1)
            .get()
            .await()
        return !snapshot.isEmpty
    }

    private suspend fun getFirstPage(
        pageSize: Long,
        showOnlyMyPosts: Boolean
    ): List<CatsUserPostData> {
        val currentUser = authProvider.getCurrentUserOrThrow()
        val snapshot = userPostsColRef
            .let {
                if (showOnlyMyPosts) {
                    it.whereIn(USER_ID, listOf(currentUser.uid))
                } else {
                    it
                }
            }
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(pageSize)
            .get()
            .await()

        NewPostsPreferences.setLastCheckedTimestamp(
            context,
            System.currentTimeMillis()
        )

        lastFetchedPost = snapshot.documents.lastOrNull()
        return snapshot.toCatsUserPostDataList()
    }

    private suspend fun getNextPage(
        pageSize: Long,
        last: DocumentSnapshot,
        showOnlyMyPosts: Boolean
    ): List<CatsUserPostData> {
        val currentUser = authProvider.getCurrentUserOrThrow()
        val snapshot = userPostsColRef
            .let {
                if (showOnlyMyPosts) {
                    it.whereIn(USER_ID, listOf(currentUser.uid))
                } else {
                    it
                }
            }
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .startAfter(last)
            .limit(pageSize)
            .get()
            .await()

        lastFetchedPost = snapshot.documents.lastOrNull() ?: return emptyList()
        return snapshot.toCatsUserPostDataList()
    }

    private suspend fun addUserPost(post: FirebaseUserPost) {
        val doc = userPostsColRef.add(post).await()
        logger.d( "user post added id: ${doc.id}")
    }

    private fun QuerySnapshot.toCatsUserPostDataList(): List<CatsUserPostData> =
        toObjects(FirebaseUserPost::class.java).zip(documents).map { (post, doc) ->
            post to doc
        }.filter { (post, _) -> post.deletingAt == null }
            .map { (post, doc) -> post.toCatsUserPostData(doc.id) }

    private fun FirebaseUserPost.toCatsUserPostData(id: String) = CatsUserPostData(
        id,
        userId,
        url,
        text,
        displayName,
        avatarUrl,
        createdAt as? Timestamp,
    )


    companion object {
        private const val CREATED_AT = "createdAt"
        private const val USER_ID = "userId"
        private const val POST_ID = "postId"
        private const val UNKNOWN_USER = "Unknown"
        private const val COMMENTS = "comments"
        private const val USERS = "users"
        private const val ACTIVITY = "activity"
        private const val LIKES = "likes"
        private const val DELETING_AT = "deletingAt"
        // Each comment page uses two writes: the comment and its activity copy.
        private const val COMMENT_DELETE_PAGE_SIZE = 200
        private const val ACTIVITY_DELETE_PAGE_SIZE = 400
    }
}

data class FirebaseUserPost(
    val userId: String = "",
    val url: String = "",
    val text: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val createdAt: Any? = null,
    val deletingAt: Any? = null,
) {
    constructor() : this("", "", "", "", null, null, null)
}
