package com.mobdev.catgram.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.getCurrentUserOrThrow
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import kotlinx.coroutines.tasks.await

interface UserPostsRepository {
    suspend fun getNextUserPostsDataPage(
        pageSize: Int,
        showOnlyMyPosts: Boolean,
    ): List<CatsUserPostData>

    suspend fun addUserPost(url: String, text: String, context: Context)
    suspend fun deleteUserPost(postId: String)
    fun reset()
}

class FirebaseUserPostsRepository() : UserPostsRepository {
    private val firestore = Firebase.firestore
    private val userPostsColRef = firestore.collection(USER_POSTS_COL)
    private var lastFetchedPost: DocumentSnapshot? = null

    override suspend fun getNextUserPostsDataPage(
        pageSize: Int,
        showOnlyMyPosts: Boolean,
    ): List<CatsUserPostData> {
        return lastFetchedPost?.let {
            getNextPage(pageSize.toLong(), it, showOnlyMyPosts)
        } ?: getFirstPage(pageSize.toLong(), showOnlyMyPosts)
    }

    override suspend fun addUserPost(url: String, text: String, context: Context) {
        val currentUser = getCurrentUserOrThrow()
        val avatarUrl = GoogleSignIn.getLastSignedInAccount(context)?.photoUrl?.toString()
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

    override suspend fun deleteUserPost(postId: String) {
        userPostsColRef.document(postId).delete().await()
    }

    override fun reset() {
        lastFetchedPost = null
    }

    private suspend fun getFirstPage(
        pageSize: Long,
        showOnlyMyPosts: Boolean
    ): List<CatsUserPostData> {
        val currentUser = getCurrentUserOrThrow()
        val snapshot = userPostsColRef
            .apply { if (showOnlyMyPosts) whereIn(USER_ID, listOf(currentUser.uid)) }
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(pageSize)
            .get()
            .await()

        lastFetchedPost = snapshot.documents.lastOrNull()
        return snapshot.toCatsUserPostDataList()
    }

    private suspend fun getNextPage(
        pageSize: Long,
        last: DocumentSnapshot,
        showOnlyMyPosts: Boolean
    ): List<CatsUserPostData> {
        val currentUser = getCurrentUserOrThrow()
        val snapshot = userPostsColRef
            .apply { if (showOnlyMyPosts) whereIn(USER_ID, listOf(currentUser.uid)) }
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
            CatsUserPostData(
                doc.id,
                post.userId,
                post.url,
                post.text,
                post.displayName,
                post.avatarUrl,
                post.createdAt as? Timestamp
            )
        }


    companion object {
        private const val CREATED_AT = "createdAt"
        private const val USER_ID = "userId"
        private const val UNKNOWN_USER = "Unknown"
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

