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
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.coroutines.DispatcherProvider
import com.mobdev.catgram.data.FavouritesRepository
import com.mobdev.catgram.data.FavouritesPage
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FavouritesViewModel(
    private val favouritesRepository: FavouritesRepository,
    private val authProvider: AuthProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    var items by mutableStateOf<List<CatCardData>?>(listOf())
        private set
    var likes by mutableStateOf<Map<String, Long>>(mapOf())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isUpdating by mutableStateOf<Map<String, Boolean>>(mapOf())
        private set
    var loadingJob: Job? = null
        private set
    private var isAllFavouritesLoaded = false
    private var hasLoadedInitialPage by mutableStateOf(false)
    private var favouriteMembership by mutableStateOf<Map<String, Boolean>>(mapOf())

    val isReady: Boolean
        get() = hasLoadedInitialPage

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0
    val currentUser
        get() = authProvider.getCurrentUser()

    init {
        if (authProvider.isSignedIn()) {
            initialize()
        }
    }

    fun initialize() {
        if (hasLoadedInitialPage || isLoading) return
        favouritesRepository.initialize()
        fetchFavourites()
    }

    fun addToFavourites(item: CatCardData) {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            if (isUpdating[item.id] ?: false) return@launch
            isUpdating = isUpdating.plus(item.id to true)

            val curItems = items
            items = items?.let { listOf(item) + it } ?: listOf(item)
            val membershipKey = item.membershipKey()
            val previousMembership = favouriteMembership[membershipKey]
            favouriteMembership = favouriteMembership.plus(membershipKey to true)
            val curLikes = likes
            val newCounter = (likes[item.id] ?: 0) + 1
            likes = likes.plus(item.id to newCounter)

            try {
                val newCounter = withContext(dispatcherProvider.io) {
                    favouritesRepository.addToFavourites(item)
                }
                likes = likes.plus(item.id to newCounter)
                logger.d("addToFavourites transaction succeeded")
            } catch (e: Throwable) {
                items = curItems
                favouriteMembership = if (previousMembership == null) {
                    favouriteMembership.minus(membershipKey)
                } else {
                    favouriteMembership.plus(membershipKey to previousMembership)
                }
                likes = curLikes
                logger.e("addToFavourites Transaction failed: ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(item.id to false)
            }
        }
    }

    fun removeFromFavourites(item: CatCardData) {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            if (isUpdating[item.id] ?: false) return@launch
            isUpdating = isUpdating.plus(item.id to true)

            val curItems = items
            items?.let { local ->
                items = local.filterNot { it.id == item.id }
            }
            val membershipKey = item.membershipKey()
            val previousMembership = favouriteMembership[membershipKey]
            favouriteMembership = favouriteMembership.plus(membershipKey to false)
            val curLikes = likes
            val newCounter = (likes[item.id] ?: 1) - 1
            likes = likes.plus(item.id to newCounter.coerceAtLeast(0))

            try {
                val newCounter = withContext(dispatcherProvider.io) {
                    favouritesRepository.removeFromFavourites(item)
                }

                likes = likes.plus(item.id to newCounter)
                logger.d("removeFromFavourites transaction succeeded")
            } catch (e: Throwable) {
                items = curItems
                favouriteMembership = if (previousMembership == null) {
                    favouriteMembership.minus(membershipKey)
                } else {
                    favouriteMembership.plus(membershipKey to previousMembership)
                }
                likes = curLikes
                logger.e("removeFromFavourites Transaction failed: ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(item.id to false)
            }
        }
    }

    fun fetchFavourites() {
        if (isLoading) return
        isLoading = true
        isRefreshing = true
        hasLoadedInitialPage = false
        loadingJob = viewModelScope.launch(dispatcherProvider.mainImmediate) {
            try {
                val page = withContext(dispatcherProvider.io) {
                    favouritesRepository.resetPagination()
                    fetchNonEmptyPage()
                }
                items = page.items
                isAllFavouritesLoaded = !page.hasMore
                favouriteMembership = mapOf()
                markItemsAsFavourites(page.items)
                hasLoadedInitialPage = true
                logger.d("fetchFavourites success; count=${page.items.size}")
            } catch (e: Throwable) {
                logger.e("fetchFavourites error: ${e.message}", e)
            } finally {
                isRefreshing = false
                isLoading = false
            }
        }
    }

    fun loadNextPageIfNeeded() {
        if (isLoading || isAllFavouritesLoaded) return
        isLoading = true
        loadingJob = viewModelScope.launch(dispatcherProvider.mainImmediate) {
            try {
                val page = withContext(dispatcherProvider.io) { fetchNonEmptyPage() }
                val existingIds = items.orEmpty().mapTo(mutableSetOf()) { it.membershipKey() }
                val newItems = page.items.filter { existingIds.add(it.membershipKey()) }
                items = items.orEmpty() + newItems
                isAllFavouritesLoaded = !page.hasMore
                markItemsAsFavourites(newItems)
                logger.d("fetch next favourites page success; count=${newItems.size}")
            } catch (e: Throwable) {
                logger.e("fetch next favourites page error: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun checkInFavourites(item: CatCardData): Boolean {
        val membershipKey = item.membershipKey()
        favouriteMembership[membershipKey]?.let { return it }
        if (items.orEmpty().any { it.membershipKey() == membershipKey }) {
            return true
        }
        if (!hasLoadedInitialPage) return false

        if (isUpdating[item.id] != true) {
            viewModelScope.launch(dispatcherProvider.mainImmediate) {
                if (favouriteMembership.containsKey(membershipKey) || isUpdating[item.id] == true) {
                    return@launch
                }
                isUpdating = isUpdating.plus(item.id to true)
                try {
                    val isFavourite = withContext(dispatcherProvider.io) {
                        favouritesRepository.isFavourite(item)
                    }
                    favouriteMembership = favouriteMembership.plus(membershipKey to isFavourite)
                } catch (e: Throwable) {
                    logger.e("checkInFavourites failed: ${e.message}", e)
                } finally {
                    isUpdating = isUpdating.plus(item.id to false)
                }
            }
        }
        return false
    }

    fun checkIsUpdating(itemId: String): Boolean {
        return isUpdating[itemId] ?: false
    }

    fun getLikesCount(itemId: String): Long? {
        likes[itemId]?.let {
            return it
        }

        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            if (isUpdating[itemId] ?: false) return@launch
            isUpdating = isUpdating.plus(itemId to true)

            try {
                val count = withContext(dispatcherProvider.io) {
                    favouritesRepository.getLikesCount(itemId)
                }
                likes = likes.plus(itemId to count)
                logger.d("getLikesCount succeeded")

            } catch (e: Throwable) {
                logger.e("getLikesCount failed ${e.message}", e)
            } finally {
                isUpdating = isUpdating.plus(itemId to false)
            }
        }

        return null
    }

    fun reset() {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            loadingJob?.cancel()
            favouritesRepository.resetPagination()
            items = listOf()
            likes = mapOf()
            favouriteMembership = mapOf()
            isAllFavouritesLoaded = false
            hasLoadedInitialPage = false
            isLoading = false
            isRefreshing = false
            scrollPositionIndex = 0
            scrollPositionOffset = 0
        }
    }

    fun refreshData() {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            likes = mapOf()
            fetchFavourites()
        }
    }

    private suspend fun fetchNonEmptyPage(): FavouritesPage {
        var page: FavouritesPage
        do {
            page = favouritesRepository.fetchNextFavouritesPage(PAGE_SIZE)
        } while (page.items.isEmpty() && page.hasMore)
        return page
    }

    private fun markItemsAsFavourites(items: List<CatCardData>) {
        favouriteMembership = favouriteMembership + items.associate { it.membershipKey() to true }
    }

    private fun CatCardData.membershipKey(): String = when (this) {
        is CatCardData.CatsApi -> "CATS_API:$id"
        is CatCardData.UserPost -> "USER_POST:$id"
    }

    companion object {
        private const val PAGE_SIZE = 10
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CatgramApplication).container
                FavouritesViewModel(
                    favouritesRepository = container.favouritesRepository,
                    authProvider = container.authProvider,
                    dispatcherProvider = container.dispatcherProvider,
                )
            }
        }
    }
}
