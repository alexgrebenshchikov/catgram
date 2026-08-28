package com.mobdev.catgram.ui.screens

import com.google.firebase.firestore.FirebaseFirestoreException
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.coroutines.TestDispatcherProvider
import com.mobdev.catgram.data.FavouritesRepository
import com.mobdev.catgram.data.FavouritesPage
import com.mobdev.catgram.ui.common.CatCardData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FavouritesRepository
    private lateinit var authProvider: AuthProvider

    private val item = CatCardData.CatsApi("cat-1", "https://example.test/cat.jpg", emptyList())
    private val secondItem =
        CatCardData.CatsApi("cat-2", "https://example.test/cat-2.jpg", emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        authProvider = mockk(relaxed = true)
        every { authProvider.isSignedIn() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed in initialization loads favourites`() = runTest(dispatcher) {
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returns
            FavouritesPage(listOf(item), hasMore = false)

        val viewModel = createViewModel()
        advanceUntilIdle()

        verify(exactly = 1) { repository.initialize() }
        assertEquals(listOf(item), viewModel.items)
        assertTrue(viewModel.isReady)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `add favourite commits server counter`() = runTest(dispatcher) {
        coEvery { repository.addToFavourites(item) } returns 7
        val viewModel = createViewModel()

        viewModel.addToFavourites(item)
        advanceUntilIdle()

        assertTrue(viewModel.checkInFavourites(item))
        assertEquals(7L, viewModel.likes[item.id])
        assertFalse(viewModel.checkIsUpdating(item.id))
    }

    @Test
    fun `failed add rolls optimistic state back`() = runTest(dispatcher) {
        coEvery { repository.addToFavourites(item) } throws IllegalStateException("offline")
        val viewModel = createViewModel()

        viewModel.addToFavourites(item)
        advanceUntilIdle()

        assertFalse(viewModel.checkInFavourites(item))
        assertFalse(viewModel.likes.containsKey(item.id))
    }

    @Test
    fun `remove passes typed item and applies server counter`() = runTest(dispatcher) {
        coEvery { repository.addToFavourites(item) } returns 2
        coEvery { repository.removeFromFavourites(item) } returns 1
        val viewModel = createViewModel()
        viewModel.addToFavourites(item)
        advanceUntilIdle()

        viewModel.removeFromFavourites(item)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.removeFromFavourites(item) }
        assertFalse(viewModel.checkInFavourites(item))
        assertEquals(1L, viewModel.likes[item.id])
    }

    @Test
    fun `likes count is fetched once then cached`() = runTest(dispatcher) {
        coEvery { repository.getLikesCount(item.id) } returns 4
        val viewModel = createViewModel()

        assertEquals(null, viewModel.getLikesCount(item.id))
        advanceUntilIdle()
        assertEquals(4L, viewModel.getLikesCount(item.id))

        coVerify(exactly = 1) { repository.getLikesCount(item.id) }
    }

    @Test
    fun `next favourites page is appended`() = runTest(dispatcher) {
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returnsMany listOf(
            FavouritesPage(listOf(item), hasMore = true),
            FavouritesPage(listOf(secondItem), hasMore = false),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadNextPageIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(item, secondItem), viewModel.items)
        coVerify(exactly = 2) { repository.fetchNextFavouritesPage(any()) }
    }

    @Test
    fun `unloaded favourite membership is fetched and cached`() = runTest(dispatcher) {
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returns
            FavouritesPage(listOf(secondItem), hasMore = true)
        coEvery { repository.isFavourite(item) } returns true
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.checkInFavourites(item))
        advanceUntilIdle()
        assertTrue(viewModel.checkInFavourites(item))

        coVerify(exactly = 1) { repository.isFavourite(item) }
    }

    @Test
    fun `fully loaded favourites do not query absent membership`() = runTest(dispatcher) {
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returns
            FavouritesPage(emptyList(), hasMore = false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.checkInFavourites(item))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.isFavourite(any()) }
    }

    @Test
    fun `offline membership failure is cached until refresh`() = runTest(dispatcher) {
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returns
            FavouritesPage(listOf(secondItem), hasMore = true)
        coEvery { repository.isFavourite(item) } throws FirebaseFirestoreException(
            "Client is offline",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.checkInFavourites(item))
        advanceUntilIdle()
        assertFalse(viewModel.checkInFavourites(item))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.isFavourite(item) }
    }

    @Test
    fun `deleted user post is removed from favourites without refetching`() = runTest(dispatcher) {
        val userPost = CatCardData.UserPost(
            id = "post-1",
            userId = "user-1",
            url = "https://example.test/post.jpg",
            text = "Cat",
            displayName = "User",
            avatarUrl = null,
            timestamp = null,
        )
        every { authProvider.isSignedIn() } returns true
        coEvery { repository.fetchNextFavouritesPage(any()) } returns
            FavouritesPage(listOf(userPost), hasMore = false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUserPostDeleted(userPost.id)

        assertEquals(emptyList<CatCardData>(), viewModel.items)
        assertFalse(viewModel.checkInFavourites(userPost))
        coVerify(exactly = 1) { repository.fetchNextFavouritesPage(any()) }
    }

    private fun createViewModel() = FavouritesViewModel(
        favouritesRepository = repository,
        authProvider = authProvider,
        dispatcherProvider = TestDispatcherProvider(dispatcher),
    )
}
