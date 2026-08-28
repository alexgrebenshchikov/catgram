package com.mobdev.catgram.ui.screens

import com.google.firebase.Timestamp
import com.mobdev.catgram.data.ActivityItem
import com.mobdev.catgram.data.ActivityRepository
import com.mobdev.catgram.data.ActivityType
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ActivityRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unread activity is exposed and can be marked read`() = runTest(dispatcher) {
        val item = ActivityItem(
            id = "activity-1",
            type = ActivityType.LIKE,
            actorUid = "actor",
            actorName = "Cat Fan",
            actorAvatarUrl = null,
            postId = "post-1",
            postPreviewUrl = null,
            commentId = null,
            commentText = null,
            createdAt = Timestamp.now(),
            readAt = null,
        )
        every { repository.observeActivity(any()) } returns flowOf(listOf(item))
        val viewModel = ActivityViewModel(repository)

        viewModel.initialize()
        advanceUntilIdle()
        assertTrue(viewModel.hasUnread)

        viewModel.markRead(item.id)
        advanceUntilIdle()
        assertFalse(viewModel.hasUnread)
        coVerify(exactly = 1) { repository.markRead(item.id) }
    }

    @Test
    fun `pending mark all read survives an older unread snapshot`() = runTest(dispatcher) {
        val item = ActivityItem(
            id = "activity-1",
            type = ActivityType.COMMENT,
            actorUid = "actor",
            actorName = "Cat Fan",
            actorAvatarUrl = null,
            postId = "post-1",
            postPreviewUrl = null,
            commentId = "comment-1",
            commentText = "Nice cat",
            createdAt = Timestamp.now(),
            readAt = null,
        )
        val snapshots = MutableSharedFlow<List<ActivityItem>>(replay = 1)
        val finishWrite = CompletableDeferred<Unit>()
        every { repository.observeActivity(any()) } returns snapshots
        coEvery { repository.markAllRead() } coAnswers { finishWrite.await() }
        snapshots.emit(listOf(item))
        val viewModel = ActivityViewModel(repository)

        viewModel.initialize()
        runCurrent()
        assertTrue(viewModel.hasUnread)

        viewModel.markAllRead()
        runCurrent()
        assertFalse(viewModel.hasUnread)

        snapshots.emit(listOf(item))
        runCurrent()
        assertFalse(viewModel.hasUnread)

        finishWrite.complete(Unit)
        runCurrent()
        snapshots.emit(listOf(item.copy(readAt = Timestamp.now())))
        runCurrent()
        assertFalse(viewModel.hasUnread)
        coVerify(exactly = 1) { repository.markAllRead() }
    }
}
