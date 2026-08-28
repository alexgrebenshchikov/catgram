package com.mobdev.catgram.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
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
        return post.toCatsUserPostData(document.id)
    }

    override suspend fun deleteUserPost(postId: String) {
        val currentUser = authProvider.getCurrentUserOrThrow()
        val postRef = userPostsColRef.document(postId)
        val post = postRef.get().await().toObject(FirebaseUserPost::class.java)
            ?: return
        check(post.userId == currentUser.uid) { "Cannot delete another user's post" }

        val comments = postRef.collection(COMMENTS)
            .limit((MAX_COMMENTS_PER_POST_DELETE + 1).toLong())
            .get()
            .await()
        check(comments.size() <= MAX_COMMENTS_PER_POST_DELETE) {
            "Cannot safely delete a post with more than $MAX_COMMENTS_PER_POST_DELETE comments"
        }

        val batch = firestore.batch()
        comments.documents.forEach { batch.delete(it.reference) }
        batch.delete(postRef)
        batch.delete(firestore.collection("likes").document(postId))
        batch.delete(
            firestore.collection("users")
                .document(currentUser.uid)
                .collection(FAVOURITES_SUBCOLLECTION)
                .document(favouriteDocumentId(postId, FavouriteItemType.USER_POST))
        )
        batch.commit().await()
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
            post.toCatsUserPostData(doc.id)
        }

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
        private const val UNKNOWN_USER = "Unknown"
        private const val COMMENTS = "comments"
        // A Firestore batch supports 500 writes. Reserve three writes for the
        // post, its likes counter, and the owner's favourite document.
        private const val MAX_COMMENTS_PER_POST_DELETE = 497
    }
}

data class FirebaseUserPost(
    val userId: String = "",
    val url: String = "",
    val text: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val createdAt: Any? = null
) {
    constructor() : this("", "", "", "", null, null)
}
