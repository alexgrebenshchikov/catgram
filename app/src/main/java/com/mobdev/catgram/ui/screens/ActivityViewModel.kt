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
import com.mobdev.catgram.data.ActivityCursor
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.data.ActivityItem
import com.mobdev.catgram.data.ActivityRepository
import com.mobdev.catgram.data.cursorOrNull
import com.mobdev.catgram.logging.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val activityRangeStart = MutableStateFlow<ActivityCursor?>(null)
    private var isMarkingAllRead = false

    fun initialize() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            isLoading = true
            isError = false
            try {
                try {
                    activityRepository.redactLegacyCommentBodies()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Redaction is idempotent and will be retried next time.
                    logger.e("Legacy activity redaction failed: ${e.message}", e)
                }
                activityRangeStart.collectLatest { rangeStart ->
                    activityRepository.observeActivity(
                        limit = PAGE_SIZE.toLong(),
                        fromInclusive = rangeStart,
                    ).collect { snapshot ->
                        removeConfirmedPendingReads(snapshot)
                        latestItems = snapshot
                        if (rangeStart == null) {
                            hasMore = snapshot.size >= PAGE_SIZE
                        } else {
                            olderItems = emptyList()
                        }
                        rebuildItems()
                        isLoading = false
                    }
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
        val oldestCursor = items.asReversed()
            .firstNotNullOfOrNull(ActivityItem::cursorOrNull) ?: return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val page = activityRepository.getOlderActivity(oldestCursor, PAGE_SIZE.toLong())
                removeConfirmedPendingReads(page.items)
                olderItems = (olderItems + page.items).distinctBy(ActivityItem::id)
                hasMore = page.hasMore
                rebuildItems()
                items.asReversed().firstNotNullOfOrNull(ActivityItem::cursorOrNull)?.let {
                    activityRangeStart.value = it
                }
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
        if (isMarkingAllRead) return
        val newlyPendingIds = (latestItems + olderItems)
            .asSequence()
            .filter(ActivityItem::isUnread)
            .map(ActivityItem::id)
            .filterNot(pendingReadIds::contains)
            .toSet()
        pendingReadIds += newlyPendingIds
        rebuildItems()
        isMarkingAllRead = true
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
            } finally {
                isMarkingAllRead = false
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
        activityRangeStart.value = null
        isMarkingAllRead = false
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
            .sortedWith(
                compareByDescending<ActivityItem> { it.createdAt?.seconds ?: Long.MIN_VALUE }
                    .thenByDescending { it.createdAt?.nanoseconds ?: Int.MIN_VALUE }
                    .thenByDescending(ActivityItem::id),
            )
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
