package com.mobdev.catgram.ui.screens

import com.mobdev.catgram.data.Comment
import com.mobdev.catgram.data.CommentsRepository
import com.mobdev.catgram.data.UserPostsRepository
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var postsRepository: UserPostsRepository
    private lateinit var commentsRepository: CommentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        postsRepository = mockk(relaxed = true)
        commentsRepository = mockk(relaxed = true)
        every { commentsRepository.observeComments(any(), any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `existing post is loaded independently from feed`() = runTest(dispatcher) {
        coEvery { postsRepository.getUserPost("post-1") } returns CatsUserPostData(
            id = "post-1",
            userId = "owner",
            url = "https://example.test/cat.jpg",
            text = "Cat",
            displayName = "Owner",
            avatarUrl = null,
            timestamp = null,
        )

        val viewModel = PostDetailViewModel("post-1", postsRepository, commentsRepository)
        advanceUntilIdle()

        assertNotNull(viewModel.post)
        assertEquals("post-1", viewModel.post?.id)
        assertNull(viewModel.error)
    }

    @Test
    fun `missing post exposes not found state`() = runTest(dispatcher) {
        coEvery { postsRepository.getUserPost("missing") } returns null

        val viewModel = PostDetailViewModel("missing", postsRepository, commentsRepository)
        advanceUntilIdle()

        assertNull(viewModel.post)
        assertEquals(PostDetailViewModel.DetailError.NOT_FOUND, viewModel.error)
    }

    @Test
    fun `blank comment is rejected before repository call`() = runTest(dispatcher) {
        coEvery { postsRepository.getUserPost(any()) } returns null
        val viewModel = PostDetailViewModel("post-1", postsRepository, commentsRepository)
        advanceUntilIdle()

        viewModel.addComment("   ") {}
        advanceUntilIdle()

        assertNotNull(viewModel.commentError)
        coVerify(exactly = 0) { commentsRepository.addComment(any(), any()) }
    }

    @Test
    fun `comment from another user cannot be deleted`() = runTest(dispatcher) {
        coEvery { postsRepository.getUserPost(any()) } returns null
        val viewModel = PostDetailViewModel("post-1", postsRepository, commentsRepository)
        advanceUntilIdle()
        val comment = Comment(
            id = "comment-1",
            postId = "post-1",
            authorUid = "comment-author",
            authorName = "Comment Author",
            authorAvatarUrl = null,
            text = "Nice cat",
            createdAt = null,
        )

        viewModel.deleteComment(comment, currentUid = "post-owner")
        advanceUntilIdle()

        coVerify(exactly = 0) { commentsRepository.deleteComment(any(), any()) }
    }
}
