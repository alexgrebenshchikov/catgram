package com.mobdev.catgram.ui.screens

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mobdev.catgram.TAG
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatsData


class FavouritesViewModel : ViewModel() {
    private val firestore = Firebase.firestore
    private lateinit var userDocRef: DocumentReference
    private val likesColRef = firestore.collection("likes")

    var items by mutableStateOf<List<CatsData>>(listOf())
        private set
    var likes by mutableStateOf<Map<String, Long>>(mapOf())
        private set

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    init {
        Log.d(TAG, "fav vm init")
        if (isSignedIn()) {
            initialize()
        }
    }

    fun initialize() {
        val currentUser =
            Firebase.auth.currentUser ?: throw IllegalStateException("User unauthorized")
        userDocRef = firestore.collection("users").document(currentUser.uid)
        fetchFavourites {}
    }

    fun addToFavourites(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            addToFavouritesDb(transaction, item)
        }.addOnSuccessListener {
            Log.d(TAG, "addToFavourites Transaction succeeded! $items $likes")
            onSuccess()
        }.addOnFailureListener { exception ->
            onFailure()
            Log.e(TAG, "addToFavourites Transaction failed: ${exception.message}")
        }
    }

    fun removeFromFavourites(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            removeFromFavouritesDb(transaction, item, onFailure)
        }.addOnSuccessListener {
            Log.d(TAG, "removeFromFavourites Transaction succeeded! $items $likes")
            onSuccess()
        }.addOnFailureListener { exception ->
            onFailure()
            Log.e(TAG, "removeFromFavourites Transaction failed: ${exception.message}")
        }
    }

    fun fetchFavourites(onComplete: () -> Unit) {
        userDocRef.get()
            .addOnSuccessListener { snapshot ->
                val currentFavourites =
                    snapshot.get(FAVOURITES_KEY)?.let { it as List<HashMap<String, Any>> }
                        ?: listOf()
                items = currentFavourites.toCatsDataList()
                onComplete()
                Log.d(TAG, "fetchFavourites success: $items")
            }
            .addOnFailureListener { e ->
                onComplete()
                Log.e(TAG, "fetchFavourites error", e)
            }
    }

    fun checkInFavourites(itemId: String): Boolean {
        return items.map { it.id }.contains(itemId)
    }

    fun getLikesCount(itemId: String, onSuccess: (Long) -> Unit) {
        likes[itemId]?.let {
            onSuccess(it)
            return
        }

        likesColRef.document(itemId).get()
            .addOnSuccessListener { snapshot ->
                val dataFromDb = snapshot.get(COUNTER_KEY)?.let { it as? Long } ?: 0
                likes = likes.plus(itemId to dataFromDb)
                onSuccess(dataFromDb)
                Log.d(TAG, "getLikesCount succeded $likes")
            }
            .addOnFailureListener {
                Log.d(TAG, "getLikesCount failed")
            }
    }

    fun reset() {
        items = listOf()
        likes = mapOf()
        scrollPositionIndex = 0
        scrollPositionOffset = 0
    }

    fun refreshData() {
        likes = mapOf()
        fetchFavourites {  }
    }

    private fun addToFavouritesDb(transaction: Transaction, item: CatsData) {
        val favDataFromDb = transaction.get(userDocRef).get(FAVOURITES_KEY)
        val likesDocRef = likesColRef.document(item.id)
        val likesDataFromDb = transaction.get(likesDocRef).get(COUNTER_KEY)

        lateinit var newItems: List<CatsData>
        if (favDataFromDb != null) {
            val currentFavourites = favDataFromDb as List<HashMap<String, Any>>
            newItems = currentFavourites.toCatsDataList() + item
            transaction.update(userDocRef, FAVOURITES_KEY, newItems)
        } else {
            newItems = listOf(item)
            transaction.set(userDocRef, hashMapOf(FAVOURITES_KEY to newItems))
        }

        lateinit var newLikes: Map<String, Long>
        if (likesDataFromDb != null) {
            val updatedCounter = likesDataFromDb as Long + 1
            newLikes = likes.plus(item.id to updatedCounter)
            transaction.update(likesDocRef, COUNTER_KEY, updatedCounter)
        } else {
            newLikes = likes.plus(item.id to 1)
            transaction.set(likesDocRef, hashMapOf(COUNTER_KEY to 1))
        }

        items = newItems
        likes = newLikes
    }

    private fun removeFromFavouritesDb(
        transaction: Transaction,
        item: CatsData,
        onFailure: () -> Unit
    ) {
        val currentFavourites =
            transaction.get(userDocRef).get(FAVOURITES_KEY)
                ?.let { it as List<HashMap<String, Any>> }
                ?: run {
                    onFailure()
                    return
                }
        val likesDocRef = likesColRef.document(item.id)
        val likesDataFromDb = transaction.get(likesDocRef).get(COUNTER_KEY)


        lateinit var newLikes: Map<String, Long>
        if (likesDataFromDb != null) {
            val updatedCounter = likesDataFromDb as Long - 1
            if (updatedCounter < 0) {
                onFailure()
                return
            }
            newLikes = likes.plus(item.id to updatedCounter)
            transaction.update(likesDocRef, COUNTER_KEY, updatedCounter)
        } else {
            onFailure()
            return
        }

        val newItems = currentFavourites.toCatsDataList() - item
        transaction.update(userDocRef, FAVOURITES_KEY, newItems)

        items = newItems
        likes = newLikes
    }

    private fun HashMap<String, Any>.toCatsData(): CatsData? {
        val id = get("id") as? String ?: return null
        val url = get("url") as? String ?: return null
        val breeds = get("breeds") as? List<*> ?: return null
        val castedBreeds = breeds.map { (it as HashMap<String, Any>).toBreedInfo() ?: return null }
        return CatsData(id, url, castedBreeds)
    }

    private fun HashMap<String, Any>.toBreedInfo(): BreedInfo? {
        val id = get("id") as? String ?: return null
        val name = get("name") as? String ?: return null
        val description = get("description") as? String ?: return null
        return BreedInfo(id, name, description)
    }

    private fun List<HashMap<String, Any>>.toCatsDataList(): List<CatsData> {
        return map {
            it.toCatsData()
                ?: throw IllegalStateException("Received data cannot be casted")
        }
    }

    companion object {
        private const val FAVOURITES_KEY = "favourites"
        private const val COUNTER_KEY = "counter"
    }
}