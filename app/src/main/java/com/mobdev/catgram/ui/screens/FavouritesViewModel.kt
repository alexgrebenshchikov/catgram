package com.mobdev.catgram.ui.screens

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
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
        ///decreaseLikesCounter(CatsData("id1", "sds", listOf()), {}, {})
        //getLikesCount("id1", { Log.d(TAG, "res: $it")})
    }

    fun initialize() {
        val currentUser =
            Firebase.auth.currentUser ?: throw IllegalStateException("User unauthorized")
        userDocRef = firestore.collection("users").document(currentUser.uid)
        fetchFavourites {}
    }

    fun addToFavourites(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val dataFromDb = snapshot.get(FAVOURITES_KEY)
            if (dataFromDb != null) {
                val currentFavourites = dataFromDb as List<HashMap<String, Any>>
                items = currentFavourites.toCatsDataList() + item
                transaction.update(userDocRef, FAVOURITES_KEY, items)
            } else {
                items = listOf(item)
                transaction.set(userDocRef, hashMapOf(FAVOURITES_KEY to items))
            }
        }.addOnSuccessListener {
            Log.d(TAG, "addToFavourites Transaction succeeded! $items")
            onSuccess()
        }.addOnFailureListener { exception ->
            items = items.minus(item)
            onFailure()
            Log.e(TAG, "addToFavourites Transaction failed: ${exception.message}")
        }
    }

    fun removeFromFavourites(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val currentFavourites =
                snapshot.get(FAVOURITES_KEY)?.let { it as List<HashMap<String, Any>> }
                    ?: run {
                        onFailure()
                        return@runTransaction
                    }
            items = currentFavourites.toCatsDataList().filterNot { it.id == item.id }
            transaction.update(userDocRef, FAVOURITES_KEY, items)
        }.addOnSuccessListener {
            Log.d(TAG, "removeFromFavourites Transaction succeeded! $items")
            onSuccess()
        }.addOnFailureListener { exception ->
            items = items.plus(item)
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
                Log.w(TAG, "fetchFavourites errort", e)
            }
    }

    fun checkInFavourites(itemId: String): Boolean {
        return items.map { it.id }.contains(itemId)
    }

    fun increaseLikesCounter(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            val likesDocRef = likesColRef.document(item.id)
            val snapshot = transaction.get(likesDocRef)
            val dataFromDb = snapshot.get(COUNTER_KEY)
            if (dataFromDb != null) {
                val updatedCounter = dataFromDb as Long + 1
                likes = likes.plus(item.id to updatedCounter)
                transaction.update(likesDocRef, COUNTER_KEY, updatedCounter)
            } else {
                likes = likes.plus(item.id to 1)
                transaction.set(likesDocRef, hashMapOf(COUNTER_KEY to 1))
            }
        }.addOnSuccessListener {
            Log.d(TAG, "increaseLikesCounter Transaction succeeded! $likes")
            onSuccess()
        }.addOnFailureListener { exception ->
            likes[item.id]?.let {
                likes = likes.plus(item.id to it - 1)
            }
            onFailure()
            Log.e(TAG, "increaseLikesCounter Transaction failed: ${exception.message}")
        }
    }

    fun decreaseLikesCounter(item: CatsData, onSuccess: () -> Unit, onFailure: () -> Unit) {
        firestore.runTransaction { transaction ->
            val likesDocRef = likesColRef.document(item.id)
            val snapshot = transaction.get(likesDocRef)
            val dataFromDb = snapshot.get(COUNTER_KEY)
            if (dataFromDb != null) {
                val updatedCounter = dataFromDb as Long - 1
                likes = likes.plus(item.id to updatedCounter)
                transaction.update(likesDocRef, COUNTER_KEY, updatedCounter)
            } else {
                onFailure()
                return@runTransaction
            }
        }.addOnSuccessListener {
            Log.d(TAG, "decrease LikesCounter Transaction succeeded! $likes")
            onSuccess()
        }.addOnFailureListener { exception ->
            likes[item.id]?.let {
                likes = likes.plus(item.id to it + 1)
            }
            onFailure()
            Log.e(TAG, "increaseLikesCounter Transaction failed: ${exception.message}")
        }
    }

    fun getLikesCount(itemId: String, onResult: (Long) -> Unit) {
        likes[itemId]?.let {
            Log.d(TAG, "getLikesCount cache hit")
            onResult(it)
            return
        }
        likesColRef.document(itemId).get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "getLikesCount succeded")
                val dataFromDb = snapshot.get(COUNTER_KEY)?.let { it as? Long } ?: 0
                likes = likes.plus(itemId to dataFromDb)
                onResult(dataFromDb)
            }
            .addOnFailureListener {
                Log.d(TAG, "getLikesCount failed")
                onResult(0)
            }
    }

    fun reset() {
        items = listOf()
        scrollPositionIndex = 0
        scrollPositionOffset = 0
    }

    //private fun addToFavourites

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