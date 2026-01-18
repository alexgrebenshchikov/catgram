package com.mobdev.catgram.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.tasks.await
import kotlin.collections.plus

interface FavouritesRepository {
    fun initialize()
    suspend fun fetchFavourites(): List<CatCardData>
    suspend fun getLikesCount(itemId: String): Long
    suspend fun addToFavourites(item: CatCardData): Long
    suspend fun removeFromFavourites(itemId: String): Long
}

class FirebaseFavouritesRepository(
    private val authProvider: AuthProvider
) : FavouritesRepository {
    private val firestore = Firebase.firestore
    private lateinit var userDocRef: DocumentReference
    private val likesColRef = firestore.collection("likes")
    private val userPostsColRef = firestore.collection(USER_POSTS_COL)
    private val catsApiColRef = firestore.collection("cats_api")

    override fun initialize() {
        val currentUser = authProvider.getCurrentUserOrThrow()
        userDocRef = firestore.collection("users").document(currentUser.uid)
    }

    override suspend fun fetchFavourites(): List<CatCardData> {
        val userDoc = userDocRef.get().await()
        //val favourites = userDoc.toObject(FirebaseFavourites::class.java) ?: return emptyList()

        return userDoc
            .toObject(FirebaseFavourites::class.java)
            ?.toCatCardDataList2()
            ?.reversed()
            ?: emptyList()
    }

    override suspend fun getLikesCount(itemId: String): Long {
        val likesDoc = likesColRef.document(itemId).get().await()
        return likesDoc.toObject(FirebaseLikesCounter::class.java)?.counter ?: 0
    }

    override suspend fun addToFavourites(item: CatCardData): Long {
        val newCounter = firestore.runTransaction { transaction ->
            val userDoc = transaction.get(userDocRef)
            val likesDocRef = likesColRef.document(item.id)
            val likesDoc = transaction.get(likesDocRef)

            val curFavourites = userDoc.toObject(FirebaseFavourites::class.java)
            val curLikesCounter = likesDoc.toObject(FirebaseLikesCounter::class.java)

            val existingFav = curFavourites?.favourites?.find { it.id == item.id }
            val existingFavId = curFavourites?.favouritesIds?.find { it.itemId == item.id }
            if (existingFav != null || existingFavId != null) {
                return@runTransaction curLikesCounter
                    ?: throw Throwable("No counter for provided id")
            }

            when (item) {
                is CatCardData.CatsApi -> {
                    val itemDocRef = catsApiColRef.document(item.id)
                    if (!transaction.get(itemDocRef).exists()) {
                        transaction.set(itemDocRef, item.toFirebaseItem())
                    }
                }

                is CatCardData.UserPost -> {
                    val itemDocRef = userPostsColRef.document(item.id)
                    if (!transaction.get(itemDocRef).exists()) {
                        throw Throwable("User post with provided id not exists")
                    }
                }
            }

            val firebaseItem = item.toFirebaseFavouriteId()
            if (curFavourites != null) {
                transaction.update(
                    userDocRef,
                    FAVOURITES_IDS_KEY,
                    curFavourites.favouritesIds + firebaseItem
                )
            } else {
                transaction.set(userDocRef, FirebaseFavourites(listOf(), listOf(firebaseItem)))
            }


            val newCounter = curLikesCounter?.copy(counter = curLikesCounter.counter + 1)?.also {
                transaction.update(likesDocRef, COUNTER_KEY, it.counter)
            } ?: FirebaseLikesCounter(1).also {
                transaction.set(likesDocRef, it)
            }

            newCounter
        }.await()
        return newCounter.counter
    }

    override suspend fun removeFromFavourites(itemId: String): Long {
        val newCounter = firestore.runTransaction { transaction ->
            val userDoc = transaction.get(userDocRef)
            val likesDocRef = likesColRef.document(itemId)
            val likesDoc = transaction.get(likesDocRef)

            val curFavourites = userDoc.toObject(FirebaseFavourites::class.java)
            val curLikesCounter = likesDoc.toObject(FirebaseLikesCounter::class.java) ?: FirebaseLikesCounter(0)

            val existingFav = curFavourites?.favourites?.find { it.id == itemId }
            val existingFavId = curFavourites?.favouritesIds?.find { it.itemId == itemId }
            if (existingFav == null && existingFavId == null) {
                return@runTransaction curLikesCounter
            }

            if (existingFav != null) {
                transaction.update(
                    userDocRef, FAVOURITES_KEY,
                    curFavourites.favourites.filterNot { it.id == itemId })
            } else {
                transaction.update(
                    userDocRef, FAVOURITES_IDS_KEY,
                    curFavourites.favouritesIds.filterNot { it.itemId == itemId })
            }


            if (curLikesCounter.counter <= 0) {
                throw Throwable("Zero or negative counter for provided id")
            }

            val newCounter = curLikesCounter.copy(counter = curLikesCounter.counter - 1).also {
                transaction.update(likesDocRef, COUNTER_KEY, it.counter)
            }

            newCounter
        }.await()
        return newCounter.counter
    }

    private suspend fun FirebaseFavourites.toCatCardDataList2(): List<CatCardData> {
        val fromFavs = favourites.map {
            it.toCatsApiCardData()
        }
        val (catsApiIds, userPostsIds) = favouritesIds.partition { it.type == FavouriteItemType.CATS_API }


        val catsApiSnapshot = catsApiIds.takeIf { it.isNotEmpty() }?.map { it.itemId }?.let { ids ->
            catsApiColRef.whereIn(FieldPath.documentId(), ids).get().await()
        }
        val userPostsSnapshot =
            userPostsIds.takeIf { it.isNotEmpty() }?.map { it.itemId }?.let { ids ->
                userPostsColRef.whereIn(FieldPath.documentId(), ids).get().await()
            }

        val catsApiMap = catsApiSnapshot?.toObjects(FirebaseFavouriteCatsApi::class.java)
            ?.map { it.toCatsApiCardData() }?.associateBy { it.id } ?: emptyMap()
        val userPostsMap =
            userPostsSnapshot?.toUserPostCatCardDataList()?.associateBy { it.id } ?: emptyMap()
        return fromFavs + favouritesIds.mapNotNull {
            when (it.type) {
                FavouriteItemType.CATS_API -> catsApiMap[it.itemId]
                FavouriteItemType.USER_POST -> userPostsMap[it.itemId]
            }
        }
    }

    private fun CatCardData.CatsApi.toFirebaseItem() =
        FirebaseFavouriteCatsApi(
            id,
            url,
            breeds.map { b -> FirebaseBreedInfo(b.id, b.name, b.description) })

    private fun CatCardData.toFirebaseFavouriteId() =
        when (this) {
            is CatCardData.CatsApi -> FirebaseFavouriteId(id, FavouriteItemType.CATS_API)
            is CatCardData.UserPost -> FirebaseFavouriteId(id, FavouriteItemType.USER_POST)
        }


    private fun FirebaseFavouriteCatsApi.toCatsApiCardData() =
        CatCardData.CatsApi(
            id,
            url,
            breeds.map { b -> BreedInfo(b.id, b.name, b.description) })

    private fun QuerySnapshot.toUserPostCatCardDataList(): List<CatCardData.UserPost> =
        toObjects(FirebaseUserPost::class.java).zip(documents).map { (post, doc) ->
            CatCardData.UserPost(
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
        private const val FAVOURITES_KEY = "favourites"
        private const val FAVOURITES_IDS_KEY = "favouritesIds"
        private const val COUNTER_KEY = "counter"
    }
}

data class FirebaseFavourites(
    val favourites: List<FirebaseFavouriteCatsApi> = listOf(),
    val favouritesIds: List<FirebaseFavouriteId> = listOf(),
) {
    constructor() : this(listOf(), listOf())
}

data class FirebaseFavouriteId(
    val itemId: String = "",
    val type: FavouriteItemType = FavouriteItemType.CATS_API,
) {
    constructor() : this("", FavouriteItemType.CATS_API)
}

enum class FavouriteItemType {
    CATS_API,
    USER_POST,
}

data class FirebaseFavouriteCatsApi(
    val id: String = "",
    val url: String = "",
    val breeds: List<FirebaseBreedInfo> = listOf()
) {
    constructor() : this("", "", listOf())
}

data class FirebaseBreedInfo(
    val id: String = "",
    val name: String = "",
    val description: String = ""
) {
    constructor() : this("", "", "")
}

data class FirebaseLikesCounter(
    val counter: Long = 0
) {
    constructor() : this(0)
}