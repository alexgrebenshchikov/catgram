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
import com.google.firebase.Timestamp
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.data.ActivityItem
import com.mobdev.catgram.data.ActivityRepository
import com.mobdev.catgram.logging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ActivityViewModel(
    private val activityRepository: ActivityRepository,
) : ViewModel() {
    var items by mutableStateOf<List<ActivityItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isError by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set

    val hasUnread: Boolean get() = items.any(ActivityItem::isUnread)

    private var observeJob: Job? = null
    private var latestItems: List<ActivityItem> = emptyList()
    private var olderItems: List<ActivityItem> = emptyList()
    private val pendingReadIds = mutableSetOf<String>()

    fun initialize() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            isLoading = true
            isError = false
            try {
                activityRepository.observeActivity().collect {
                    removeConfirmedPendingReads(it)
                    latestItems = it
                    if (olderItems.isEmpty()) hasMore = it.size >= PAGE_SIZE
                    rebuildItems()
                    isLoading = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("observe activity failed: ${e.message}", e)
                isLoading = false
                isError = true
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        val oldestTimestamp = items.lastOrNull()?.createdAt ?: return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val page = activityRepository.getOlderActivity(oldestTimestamp, PAGE_SIZE.toLong())
                removeConfirmedPendingReads(page.items)
                olderItems = (olderItems + page.items).distinctBy(ActivityItem::id)
                hasMore = page.hasMore
                rebuildItems()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("load older activity failed: ${e.message}", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun retry() {
        observeJob?.cancel()
        observeJob = null
        initialize()
    }

    fun markRead(activityId: String) {
        if (!pendingReadIds.add(activityId)) return
        rebuildItems()
        viewModelScope.launch {
            try {
                activityRepository.markRead(activityId)
            } catch (e: CancellationException) {
                pendingReadIds.remove(activityId)
                rebuildItems()
                throw e
            } catch (e: Throwable) {
                pendingReadIds.remove(activityId)
                rebuildItems()
                logger.e("mark activity read failed: ${e.message}", e)
            }
        }
    }

    fun markAllRead() {
        val newlyPendingIds = (latestItems + olderItems)
            .asSequence()
            .filter(ActivityItem::isUnread)
            .map(ActivityItem::id)
            .filterNot(pendingReadIds::contains)
            .toSet()
        pendingReadIds += newlyPendingIds
        rebuildItems()
        viewModelScope.launch {
            try {
                activityRepository.markAllRead()
            } catch (e: CancellationException) {
                pendingReadIds.removeAll(newlyPendingIds)
                rebuildItems()
                throw e
            } catch (e: Throwable) {
                pendingReadIds.removeAll(newlyPendingIds)
                rebuildItems()
                logger.e("mark all activity read failed: ${e.message}", e)
            }
        }
    }

    fun reset() {
        observeJob?.cancel()
        observeJob = null
        items = emptyList()
        latestItems = emptyList()
        olderItems = emptyList()
        pendingReadIds.clear()
        isLoading = false
        isLoadingMore = false
        hasMore = false
        isError = false
    }

    private fun rebuildItems() {
        val optimisticReadAt = Timestamp.now()
        items = (latestItems + olderItems)
            .distinctBy(ActivityItem::id)
            .map { item ->
                if (item.id in pendingReadIds && item.isUnread) {
                    item.copy(readAt = item.createdAt ?: optimisticReadAt)
                } else {
                    item
                }
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
    }

    private fun removeConfirmedPendingReads(serverItems: List<ActivityItem>) {
        pendingReadIds.removeAll(
            serverItems.asSequence()
                .filterNot(ActivityItem::isUnread)
                .map(ActivityItem::id)
                .toSet(),
        )
    }

    companion object {
        private const val PAGE_SIZE = 50
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CatgramApplication).container
                ActivityViewModel(container.activityRepository)
            }
        }
    }
}
