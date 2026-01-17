package com.mobdev.catgram.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.data.FavouritesRepository
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FavouritesViewModel(
    private val favouritesRepository: FavouritesRepository
) : ViewModel() {
    var items by mutableStateOf<List<CatCardData>?>(listOf())
        private set
    var likes by mutableStateOf<Map<String, Long>>(mapOf())
        private set

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    init {
        if (isSignedIn()) {
            initialize()
        }
    }

    fun initialize() {
        favouritesRepository.initialize()
        fetchFavourites {}
    }

    fun addToFavourites(item: CatCardData, onFinish: () -> Unit) {
        val curItems = items
        items = items?.let { listOf(item) + it } ?: listOf(item)
        val curLikes = likes
        val newCounter = (likes[item.id] ?: 0) + 1
        likes = likes.plus(item.id to newCounter)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newCounter = favouritesRepository.addToFavourites(item)
                likes = likes.plus(item.id to newCounter)
                logger.d( "addToFavourites Transaction succeeded! $items $likes")
                onFinish()
            } catch (e: Throwable) {
                items = curItems
                likes = curLikes
                logger.e( "addToFavourites Transaction failed: ${e.message}", e)
                onFinish()
            }
        }
    }

    fun removeFromFavourites(item: CatCardData, onFinish: () -> Unit) {
        val curItems = items
        items?.let { local ->
            items = local.filterNot { it.id == item.id }
        }
        val curLikes = likes
        val newCounter = (likes[item.id] ?: 1) - 1
        likes = likes.plus(item.id to newCounter.coerceAtLeast(0))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newCounter = favouritesRepository.removeFromFavourites(item.id)

                likes = likes.plus(item.id to newCounter)
                logger.d( "removeFromFavourites Transaction succeeded! $items $likes")
                onFinish()
            } catch (e: Throwable) {
                items = curItems
                likes = curLikes
                logger.e( "removeFromFavourites Transaction failed: ${e.message}", e)
                onFinish()
            }
        }
    }

    fun fetchFavourites(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newItems = favouritesRepository.fetchFavourites()
                withContext(Dispatchers.Main) {
                    items = newItems
                    onComplete()
                    logger.d( "fetchFavourites success: $items")
                }
            } catch (e: Throwable) {
                onComplete()
                logger.e( "fetchFavourites error: ${e.message}", e)
            }
        }
    }

    fun checkInFavourites(itemId: String): Boolean {
        return items?.find { it.id == itemId } != null
    }

    fun getLikesCount(itemId: String): Long? {
        likes[itemId]?.let {
            return it
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = favouritesRepository.getLikesCount(itemId)
                withContext(Dispatchers.Main) {
                    likes = likes.plus(itemId to count)
                    logger.d( "getLikesCount succeeded $likes")
                }
            } catch (e: Throwable) {
                logger.e( "getLikesCount failed ${e.message}", e)
            }
        }
        return null
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

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as CatgramApplication)
                val favouritesRepository = application.container.favouritesRepository
                FavouritesViewModel(favouritesRepository)
            }
        }
    }
}