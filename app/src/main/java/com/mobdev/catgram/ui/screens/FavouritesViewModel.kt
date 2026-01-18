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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FavouritesViewModel(
    private val favouritesRepository: FavouritesRepository
) : ViewModel() {
    var items by mutableStateOf<List<CatCardData>?>(listOf())
        private set
    var likes by mutableStateOf<Map<String, Long>>(mapOf())
        private set
    var isLoading by mutableStateOf(false)
    var isUpdating by mutableStateOf<Map<String, Boolean>>(mapOf())
    var loadingJob: Job? = null

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    init {
        if (isSignedIn()) {
            initialize()
        }
    }

    fun initialize() {
        favouritesRepository.initialize()
        fetchFavourites()
    }

    fun addToFavourites(item: CatCardData) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isUpdating[item.id] ?: false) return@launch
            isUpdating = isUpdating.plus(item.id to true)

            val curItems = items
            items = items?.let { listOf(item) + it } ?: listOf(item)
            val curLikes = likes
            val newCounter = (likes[item.id] ?: 0) + 1
            likes = likes.plus(item.id to newCounter)

            try {
                val newCounter = withContext(Dispatchers.IO) {
                    favouritesRepository.addToFavourites(item)
                }
                likes = likes.plus(item.id to newCounter)
                logger.d("addToFavourites Transaction succeeded! $items $likes")
            } catch (e: Throwable) {
                items = curItems
                likes = curLikes
                logger.e("addToFavourites Transaction failed: ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(item.id to false)
            }
        }
    }

    fun removeFromFavourites(item: CatCardData) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isUpdating[item.id] ?: false) return@launch
            isUpdating = isUpdating.plus(item.id to true)

            val curItems = items
            items?.let { local ->
                items = local.filterNot { it.id == item.id }
            }
            val curLikes = likes
            val newCounter = (likes[item.id] ?: 1) - 1
            likes = likes.plus(item.id to newCounter.coerceAtLeast(0))

            try {
                val newCounter = withContext(Dispatchers.IO) {
                    favouritesRepository.removeFromFavourites(item.id)
                }

                likes = likes.plus(item.id to newCounter)
                logger.d("removeFromFavourites Transaction succeeded! $items $likes")
            } catch (e: Throwable) {
                items = curItems
                likes = curLikes
                logger.e("removeFromFavourites Transaction failed: ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(item.id to false)
            }
        }
    }

    fun fetchFavourites() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            loadingJob = viewModelScope.launch(Dispatchers.Main.immediate) {
                if (isLoading) return@launch
                isLoading = true

                try {
                    val newItems = withContext(Dispatchers.IO) {
                        favouritesRepository.fetchFavourites()
                    }
                    items = newItems
                    logger.d("fetchFavourites success: $items")
                } catch (e: Throwable) {
                    logger.e("fetchFavourites error: ${e.message}", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun checkInFavourites(itemId: String): Boolean {
        return items?.find { it.id == itemId } != null
    }

    fun checkIsUpdating(itemId: String): Boolean {
        return isUpdating[itemId] ?: false
    }

    fun getLikesCount(itemId: String): Long? {
        likes[itemId]?.let {
            return it
        }

        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isUpdating[itemId] ?: false) return@launch
            isUpdating = isUpdating.plus(itemId to true)

            try {
                val count = withContext(Dispatchers.IO) {
                    favouritesRepository.getLikesCount(itemId)
                }
                likes = likes.plus(itemId to count)
                logger.d("getLikesCount succeeded $likes")

            } catch (e: Throwable) {
                logger.e("getLikesCount failed ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(itemId to false)
            }
        }

        return null
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            loadingJob?.cancel()
            items = listOf()
            likes = mapOf()
            scrollPositionIndex = 0
            scrollPositionOffset = 0
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            likes = mapOf()
            fetchFavourites()
        }
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