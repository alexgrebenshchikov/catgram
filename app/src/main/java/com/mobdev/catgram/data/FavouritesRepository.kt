package com.mobdev.catgram.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val FAVOURITES_SUBCOLLECTION = "favourites_v2"

internal fun favouriteDocumentId(itemId: String, type: FavouriteItemType): String =
    "${type.name}:$itemId"

interface FavouritesRepository {
    fun initialize()
    fun resetPagination()
    suspend fun fetchNextFavouritesPage(pageSize: Int): FavouritesPage
    suspend fun isFavourite(item: CatCardData): Boolean
    suspend fun getLikesCount(itemId: String): Long
    suspend fun addToFavourites(item: CatCardData): Long
    suspend fun removeFromFavourites(item: CatCardData): Long
}

data class FavouritesPage(
    val items: List<CatCardData>,
    val hasMore: Boolean,
)

class FirebaseFavouritesRepository(
    private val authProvider: AuthProvider,
) : FavouritesRepository {
    private val firestore = Firebase.firestore
    private lateinit var userDocRef: DocumentReference
    private lateinit var favouritesColRef: CollectionReference
    private val likesColRef = firestore.collection("likes")
    private val userPostsColRef = firestore.collection(USER_POSTS_COL)
    private val catsApiColRef = firestore.collection("cats_api")
    private val paginationMutex = Mutex()
    private var lastFetchedFavourite: DocumentSnapshot? = null
    private var migrationChecked = false

    override fun initialize() {
        val currentUser = authProvider.getCurrentUserOrThrow()
        userDocRef = firestore.collection("users").document(currentUser.uid)
        favouritesColRef = userDocRef.collection(FAVOURITES_SUBCOLLECTION)
        lastFetchedFavourite = null
        migrationChecked = false
    }

    override fun resetPagination() {
        lastFetchedFavourite = null
    }

    override suspend fun fetchNextFavouritesPage(pageSize: Int): FavouritesPage =
        paginationMutex.withLock {
            require(pageSize > 0) { "Page size must be positive" }
            if (!migrationChecked) {
                migrateLegacyFavouritesIfNeeded()
                migrationChecked = true
            }

            var query = favouritesColRef
                .orderBy(CREATED_AT_MILLIS, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
            lastFetchedFavourite?.let { query = query.startAfter(it) }

            val snapshot = query.get().await()
            lastFetchedFavourite = snapshot.documents.lastOrNull() ?: lastFetchedFavourite
            val references = snapshot.toObjects(FirebaseFavouriteReference::class.java)
            FavouritesPage(
                items = references.toCatCardDataList(),
                hasMore = snapshot.documents.size == pageSize,
            )
        }

    override suspend fun isFavourite(item: CatCardData): Boolean {
        val reference = favouritesColRef.document(
            favouriteDocumentId(item.id, item.favouriteType()),
        )
        return reference.get().await().exists()
    }

    override suspend fun getLikesCount(itemId: String): Long {
        val likesDoc = likesColRef.document(itemId).get().await()
        return likesDoc.toObject(FirebaseLikesCounter::class.java)?.counter ?: 0
    }

    override suspend fun addToFavourites(item: CatCardData): Long {
        val actor = authProvider.getCurrentUserOrThrow()
        val type = item.favouriteType()
        val favouriteRef = favouritesColRef.document(favouriteDocumentId(item.id, type))
        val likesDocRef = likesColRef.document(item.id)

        return firestore.runTransaction { transaction ->
            val favouriteDoc = transaction.get(favouriteRef)
            val likesDoc = transaction.get(likesDocRef)
            val currentCounter = likesDoc.toObject(FirebaseLikesCounter::class.java)?.counter ?: 0
            if (favouriteDoc.exists()) return@runTransaction currentCounter

            val itemDocRef = when (item) {
                is CatCardData.CatsApi -> catsApiColRef.document(item.id)
                is CatCardData.UserPost -> userPostsColRef.document(item.id)
            }
            val itemDoc = transaction.get(itemDocRef)
            when (item) {
                is CatCardData.CatsApi -> if (!itemDoc.exists()) {
                    transaction.set(itemDocRef, item.toFirebaseItem())
                }

                is CatCardData.UserPost -> check(itemDoc.exists()) {
                    "User post with provided id does not exist"
                }
            }

            transaction.set(
                favouriteRef,
                FirebaseFavouriteReference(
                    itemId = item.id,
                    type = type,
                    createdAtMillis = System.currentTimeMillis(),
                    source = FavouriteSource.USER_ACTION,
                    createdAt = FieldValue.serverTimestamp(),
                ),
            )
            val newCounter = currentCounter + 1
            transaction.set(likesDocRef, FirebaseLikesCounter(newCounter))

            if (item is CatCardData.UserPost) {
                val ownerUid = itemDoc.getString(USER_ID).orEmpty()
                check(ownerUid.isNotBlank()) { "Post owner is missing" }
                if (ownerUid == actor.uid) return@runTransaction newCounter
                val activityRef = firestore.collection(USERS)
                    .document(ownerUid)
                    .collection(ACTIVITY)
                    .document(likeActivityDocumentId(actor.uid, item.id))
                transaction.set(
                    activityRef,
                    activityDocument(
                        type = ActivityType.LIKE,
                        actorUid = actor.uid,
                        actorName = normalizedActivityName(actor.displayName),
                        actorAvatarUrl = normalizedActivityUrl(actor.photoUrl?.toString()),
                        postId = item.id,
                        postPreviewUrl = normalizedActivityUrl(itemDoc.getString(URL)),
                    ),
                )
            }
            newCounter
        }.await()
    }

    override suspend fun removeFromFavourites(item: CatCardData): Long {
        val favouriteRef = favouritesColRef.document(
            favouriteDocumentId(item.id, item.favouriteType()),
        )
        val likesDocRef = likesColRef.document(item.id)

        return firestore.runTransaction { transaction ->
            val favouriteDoc = transaction.get(favouriteRef)
            val likesDoc = transaction.get(likesDocRef)
            val currentCounter = likesDoc.toObject(FirebaseLikesCounter::class.java)?.counter ?: 0
            if (!favouriteDoc.exists()) return@runTransaction currentCounter

            transaction.delete(favouriteRef)
            val newCounter = (currentCounter - 1).coerceAtLeast(0)
            transaction.set(likesDocRef, FirebaseLikesCounter(newCounter))
            newCounter
        }.await()
    }

    /** Migrates legacy arrays using deterministic, idempotent writes. */
    private suspend fun migrateLegacyFavouritesIfNeeded() {
        val userDoc = userDocRef.get().await()
        if ((userDoc.getLong(SCHEMA_VERSION_KEY) ?: 0) >= SCHEMA_VERSION) return

        val legacy = userDoc.toObject(FirebaseFavourites::class.java) ?: FirebaseFavourites()
        val baseTime = System.currentTimeMillis() - legacy.favouritesIds.size - legacy.favourites.size

        legacy.favourites.chunked(MIGRATION_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
            val batch = firestore.batch()
            chunk.forEachIndexed { itemIndex, item ->
                val order = chunkIndex * MIGRATION_BATCH_SIZE + itemIndex
                batch.set(catsApiColRef.document(item.id), item, SetOptions.merge())
                batch.set(
                    favouritesColRef.document(
                        favouriteDocumentId(item.id, FavouriteItemType.CATS_API),
                    ),
                    FirebaseFavouriteReference(
                        itemId = item.id,
                        type = FavouriteItemType.CATS_API,
                        createdAtMillis = baseTime + order,
                        source = FavouriteSource.MIGRATION,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }

        legacy.favouritesIds.chunked(MIGRATION_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
            val batch = firestore.batch()
            chunk.forEachIndexed { itemIndex, item ->
                val order = legacy.favourites.size + chunkIndex * MIGRATION_BATCH_SIZE + itemIndex
                batch.set(
                    favouritesColRef.document(favouriteDocumentId(item.itemId, item.type)),
                    FirebaseFavouriteReference(
                        itemId = item.itemId,
                        type = item.type,
                        createdAtMillis = baseTime + order,
                        source = FavouriteSource.MIGRATION,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }

        userDocRef.set(mapOf(SCHEMA_VERSION_KEY to SCHEMA_VERSION), SetOptions.merge()).await()
    }

    private suspend fun List<FirebaseFavouriteReference>.toCatCardDataList(): List<CatCardData> {
        val (catsApiRefs, userPostRefs) = partition { it.type == FavouriteItemType.CATS_API }
        val catsApiMap = fetchCatsApiItems(catsApiRefs.map { it.itemId }).associateBy { it.id }
        val userPostsMap = fetchUserPostItems(userPostRefs.map { it.itemId }).associateBy { it.id }

        val missingReferences = mutableListOf<FirebaseFavouriteReference>()
        val result = mapNotNull { reference ->
            val item = when (reference.type) {
                FavouriteItemType.CATS_API -> catsApiMap[reference.itemId]
                FavouriteItemType.USER_POST -> userPostsMap[reference.itemId]
            }
            if (item == null) missingReferences += reference
            item
        }

        missingReferences.chunked(MIGRATION_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { reference ->
                batch.delete(
                    favouritesColRef.document(
                        favouriteDocumentId(reference.itemId, reference.type),
                    ),
                )
            }
            batch.commit().await()
        }
        return result
    }

    private suspend fun fetchCatsApiItems(ids: List<String>): List<CatCardData.CatsApi> =
        ids.distinct().chunked(FIRESTORE_QUERY_BATCH_SIZE).flatMap { chunk ->
            catsApiColRef.whereIn(FieldPath.documentId(), chunk).get().await()
                .toObjects(FirebaseFavouriteCatsApi::class.java)
                .map { it.toCatsApiCardData() }
        }

    private suspend fun fetchUserPostItems(ids: List<String>): List<CatCardData.UserPost> =
        ids.distinct().chunked(FIRESTORE_QUERY_BATCH_SIZE).flatMap { chunk ->
            userPostsColRef.whereIn(FieldPath.documentId(), chunk).get().await()
                .toUserPostCatCardDataList()
        }

    private fun CatCardData.favouriteType(): FavouriteItemType = when (this) {
        is CatCardData.CatsApi -> FavouriteItemType.CATS_API
        is CatCardData.UserPost -> FavouriteItemType.USER_POST
    }

    private fun CatCardData.CatsApi.toFirebaseItem() = FirebaseFavouriteCatsApi(
        id,
        url,
        breeds.map { FirebaseBreedInfo(it.id, it.name, it.description) },
    )

    private fun FirebaseFavouriteCatsApi.toCatsApiCardData() = CatCardData.CatsApi(
        id,
        url,
        breeds.map { BreedInfo(it.id, it.name, it.description) },
    )

    private fun QuerySnapshot.toUserPostCatCardDataList(): List<CatCardData.UserPost> =
        toObjects(FirebaseUserPost::class.java).zip(documents).map { (post, doc) ->
            CatCardData.UserPost(
                doc.id,
                post.userId,
                post.url,
                post.text,
                post.displayName,
                post.avatarUrl,
                post.createdAt as? Timestamp,
            )
        }

    companion object {
        private const val SCHEMA_VERSION_KEY = "favouritesSchemaVersion"
        private const val SCHEMA_VERSION = 2L
        private const val CREATED_AT_MILLIS = "createdAtMillis"
        private const val MIGRATION_BATCH_SIZE = 200
        private const val FIRESTORE_QUERY_BATCH_SIZE = 10
        private const val USER_ID = "userId"
        private const val URL = "url"
        private const val USERS = "users"
        private const val ACTIVITY = "activity"
    }
}

data class FirebaseFavouriteReference(
    val itemId: String = "",
    val type: FavouriteItemType = FavouriteItemType.CATS_API,
    val createdAtMillis: Long = 0,
    val source: FavouriteSource? = null,
    val createdAt: Any? = null,
) {
    constructor() : this("", FavouriteItemType.CATS_API, 0, null, null)
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

enum class FavouriteSource {
    USER_ACTION,
    MIGRATION,
}

data class FirebaseFavouriteCatsApi(
    val id: String = "",
    val url: String = "",
    val breeds: List<FirebaseBreedInfo> = listOf(),
) {
    constructor() : this("", "", listOf())
}

data class FirebaseBreedInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
) {
    constructor() : this("", "", "")
}

data class FirebaseLikesCounter(
    val counter: Long = 0,
) {
    constructor() : this(0)
}
