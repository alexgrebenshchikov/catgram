package com.mobdev.catgram.ui.screens

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.coroutines.TestDispatcherProvider
import com.mobdev.catgram.data.CatgramApiRepository
import com.mobdev.catgram.data.UserPostsRepository
import com.mobdev.catgram.ml.CatDetector
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatsData.CatsApiData
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import com.mobdev.catgram.network.ImageUploadResult
import com.mobdev.catgram.network.ImageUploader
import com.mobdev.catgram.ui.common.CatCardData
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testScope: TestScope
    private lateinit var catgramApiRepository: CatgramApiRepository
    private lateinit var userPostsRepository: UserPostsRepository
    private lateinit var imageUploader: ImageUploader
    private lateinit var catDetector: CatDetector
    private lateinit var authProvider: AuthProvider
    private lateinit var mockApplication: Application
    private lateinit var mockFirebaseUser: FirebaseUser

    private val testDispatcher = StandardTestDispatcher()

    private val testBreeds = listOf(
        BreedInfo("abys", "Abyssinian", "Test description 1"),
        BreedInfo("beng", "Bengal", "Test description 2"),
        BreedInfo("siam", "Siamese", "Test description 3")
    )

    private val testCatsApiData = listOf(
        CatsApiData("cat1", "http://example.com/cat1.jpg", listOf(testBreeds[0])),
        CatsApiData("cat2", "http://example.com/cat2.jpg", listOf(testBreeds[1])),
        CatsApiData("cat3", "http://example.com/cat3.jpg", listOf(testBreeds[2]))
    )

    private val testUserPosts = listOf(
        CatsUserPostData(
            "post1",
            "user1",
            "http://example.com/post1.jpg",
            "My cat",
            "John",
            null,
            null
        ),
        CatsUserPostData(
            "post2",
            "user2",
            "http://example.com/post2.jpg",
            "Cute kitty",
            "Jane",
            "http://avatar.jpg",
            Timestamp.now()
        )
    )

    private val testDefaultFilterState =
        FeedViewModel.DefaultFilterState(mapOf(), FeedViewModel.FilterType.CATS_BY_BREED, false)

    private val testDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create test scope for DataStore
        testScope = TestScope(testDispatcher + Job())

        // Create test DataStore with UnconfinedTestDispatcher to avoid deadlocks
        // UnconfinedTestDispatcher executes coroutines eagerly, preventing deadlock
        // when ViewModel coroutines wait for DataStore operations
        val testFile = File(tempFolder.root, "test_prefs.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            produceFile = { testFile }
        )

        // Mock dependencies
        catgramApiRepository = mockk(relaxed = true)
        userPostsRepository = mockk(relaxed = true)
        imageUploader = mockk(relaxed = true)
        catDetector = mockk(relaxed = true)
        authProvider = mockk(relaxed = true)
        mockApplication = mockk(relaxed = true)
        mockFirebaseUser = mockk(relaxed = true)

        // Setup common mocks
        every { mockFirebaseUser.uid } returns "test-user-id"
        every { mockFirebaseUser.displayName } returns "Test User"
        every { mockApplication.getString(R.string.snackbar_upload_cat_image) } returns "Please upload an image of a cat"
        every { mockApplication.getString(R.string.snackbar_post_created) } returns "Post created successfully!"
        every { mockApplication.getString(R.string.snackbar_post_create_failed) } returns "Failed to create post"
        every { mockApplication.getString(R.string.snackbar_post_delete_failed) } returns "Failed to delete post"
        every { mockApplication.getString(R.string.snackbar_load_data_failed) } returns "Failed to load data"
        every { mockApplication.getString(R.string.snackbar_check_date_time) } returns "Check date time"

        // Setup AuthProvider mock
        every { authProvider.getCurrentUser() } returns mockFirebaseUser
        every { authProvider.getCurrentUserOrThrow() } returns mockFirebaseUser
        every { authProvider.isSignedIn() } returns true

        // Default repository responses
        coEvery { catgramApiRepository.getBreedList() } returns testBreeds
        coEvery { catgramApiRepository.getCatsData(any(), any(), any()) } returns testCatsApiData
        coEvery { userPostsRepository.getNextUserPostsDataPage(any(), any()) } returns testUserPosts
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ============ Helper Methods ============

    private fun createViewModel(isSignedIn: Boolean = true, filterType: FeedViewModel.FilterType = FeedViewModel.FilterType.CATS_BY_BREED): FeedViewModel {
        every { authProvider.isSignedIn() } returns isSignedIn

        return FeedViewModel(
            catgramApiRepository = catgramApiRepository,
            userPostsRepository = userPostsRepository,
            imageUploader = imageUploader,
            catDetector = catDetector,
            authProvider = authProvider,
            dataStore = testDataStore,
            context = mockApplication,
            dispatcherProvider = testDispatcherProvider,
            defaultFilterState = testDefaultFilterState.copy(filterType = filterType),
        )
    }

    // ============ Init Tests ============

    @Test
    fun `init loads filter state when signed in`() = runTest {
        val viewModel = createViewModel(isSignedIn = true)
        advanceUntilIdle()

        // Should have loaded breeds
        assertTrue(viewModel.choosedBreeds.isNotEmpty())
        assertEquals(FeedViewModel.FeedUiState.Ready, viewModel.uiState)
    }

    @Test
    fun `init does not load filter state when not signed in`() = runTest {
        val viewModel = createViewModel(isSignedIn = false)
        advanceUntilIdle()

        // Should not have loaded breeds
        assertTrue(viewModel.choosedBreeds.isEmpty())
    }

    // ============ loadDataPageIfNeeded Tests ============

    @Test
    fun `loadDataPageIfNeeded does nothing when already loading`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Manually set loading state
        viewModel.uiState = FeedViewModel.FeedUiState.Loading(false)

        // When: Try to load more
        viewModel.loadDataPageIfNeeded(page = 1)
        advanceUntilIdle()

        coVerify(exactly = 1) { catgramApiRepository.getCatsData(any(), any(), any()) }
    }

    @Test
    fun `loadDataPageIfNeeded does nothing when in error state and checkErrorState is true`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Set error state
            viewModel.uiState = FeedViewModel.FeedUiState.Error

            viewModel.loadDataPageIfNeeded(page = 1, checkErrorState = true)
            advanceUntilIdle()

            // Should still be in error state
            assertEquals(FeedViewModel.FeedUiState.Error, viewModel.uiState)
        }

    @Test
    fun `loadDataPageIfNeeded loads when in error state but checkErrorState is false`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Set error state
        viewModel.uiState = FeedViewModel.FeedUiState.Error

        viewModel.loadDataPageIfNeeded(page = 1, checkErrorState = false)
        advanceUntilIdle()

        // Should have attempted to load
        coVerify { catgramApiRepository.getCatsData(any(), any(), 1) }
    }

    @Test
    fun `loadDataPageIfNeeded does not reload same page`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        clearMocks(catgramApiRepository, answers = false)
        coEvery { catgramApiRepository.getCatsData(any(), any(), any()) } returns testCatsApiData

        // Load page 0 again (already loaded in init)
        viewModel.loadDataPageIfNeeded(page = 0)
        advanceUntilIdle()

        // Should not make another API call for page 0
        coVerify(exactly = 0) { catgramApiRepository.getCatsData(any(), any(), 0) }
    }

    // ============ createUserPost Tests ============

    @Test
    fun `createUserPost shows error when image is not a cat`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockUri = mockk<Uri>()
        coEvery { catDetector.isCatImage(any(), mockUri) } returns Result.success(false)

        viewModel.createUserPost(mockUri, "Test post")
        advanceUntilIdle()

        assertEquals("Please upload an image of a cat", viewModel.snackbarMessage)
        assertFalse(viewModel.isCreatingPost)
    }

    @Test
    fun `createUserPost shows success when cat image is uploaded`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockUri = mockk<Uri>()
        coEvery { catDetector.isCatImage(any(), mockUri) } returns Result.success(true)
        coEvery { imageUploader.uploadImage(mockUri, any()) } returns Result.success(
            ImageUploadResult("http://uploaded.jpg")
        )
        coEvery { userPostsRepository.addUserPost(any(), any(), any()) } just runs

        viewModel.createUserPost(mockUri, "My cute cat")
        advanceUntilIdle()

        assertEquals("Post created successfully!", viewModel.snackbarMessage)
        assertFalse(viewModel.isCreatingPost)
        coVerify { userPostsRepository.addUserPost("http://uploaded.jpg", "My cute cat", any()) }
    }

    @Test
    fun `createUserPost shows error when cat detection fails`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockUri = mockk<Uri>()
        coEvery {
            catDetector.isCatImage(
                any(),
                mockUri
            )
        } returns Result.failure(Exception("Detection failed"))

        viewModel.createUserPost(mockUri, "Test post")
        advanceUntilIdle()

        assertEquals("Failed to create post", viewModel.snackbarMessage)
        assertFalse(viewModel.isCreatingPost)

        viewModel.clearSnackbarMessage()

        assertNull(viewModel.snackbarMessage)
    }

    @Test
    fun `createUserPost shows error when image upload fails`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockUri = mockk<Uri>()
        coEvery { catDetector.isCatImage(any(), mockUri) } returns Result.success(true)
        coEvery {
            imageUploader.uploadImage(
                mockUri,
                any()
            )
        } returns Result.failure(Exception("Upload failed"))

        viewModel.createUserPost(mockUri, "Test post")
        advanceUntilIdle()

        assertEquals("Failed to create post", viewModel.snackbarMessage)
        assertFalse(viewModel.isCreatingPost)
    }

    @Test
    fun `createUserPost sets isCreatingPost during upload`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val mockUri = mockk<Uri>()

        // Use a slow response to check isCreatingPost state
        coEvery { catDetector.isCatImage(any(), mockUri) } coAnswers {
            // During this call, isCreatingPost should be true
            assertTrue(viewModel.isCreatingPost)
            Result.success(true)
        }
        coEvery { imageUploader.uploadImage(mockUri, any()) } returns Result.success(
            ImageUploadResult("http://test.jpg")
        )

        assertFalse(viewModel.isCreatingPost)
        viewModel.createUserPost(mockUri, "Test")
        advanceUntilIdle()
        assertFalse(viewModel.isCreatingPost)
    }

    @Test
    fun `createUserPost refreshes data when filter type is USERS_POSTS`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Switch to user posts filter
        viewModel.updateFilterType(FeedViewModel.FilterType.USERS_POSTS)
        advanceUntilIdle()

        val mockUri = mockk<Uri>()
        coEvery { catDetector.isCatImage(any(), mockUri) } returns Result.success(true)
        coEvery { imageUploader.uploadImage(mockUri, any()) } returns Result.success(
            ImageUploadResult("http://test.jpg")
        )

        clearMocks(userPostsRepository, answers = false)
        coEvery { userPostsRepository.getNextUserPostsDataPage(any(), any()) } returns testUserPosts

        viewModel.createUserPost(mockUri, "Test")
        advanceUntilIdle()

        // Should trigger refresh which calls getNextUserPostsDataPage
        coVerify { userPostsRepository.getNextUserPostsDataPage(any(), any()) }
        assertTrue(viewModel.shouldScrollToTop)

        viewModel.onScrolledToTop()

        assertFalse(viewModel.shouldScrollToTop)
    }

    // ============ deleteUserPost Tests ============

    @Test
    fun `deleteUserPost removes item from list on success`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateFilterType(FeedViewModel.FilterType.USERS_POSTS)

        val userPost = CatsUserPostData("post1", "user1", "url", "text", "name", null, null)
        coEvery { userPostsRepository.getNextUserPostsDataPage(any(), any()) } returns listOf(
            userPost
        )

        viewModel.refreshData()
        advanceUntilIdle()

        coEvery { userPostsRepository.deleteUserPost("post1") } just runs

        var onSuccessCalled = false
        viewModel.deleteUserPost(userPost.id) {
            onSuccessCalled = true
        }
        advanceUntilIdle()

        assertTrue(viewModel.items.isEmpty())
        assertNull(viewModel.snackbarMessage)
        assertTrue(onSuccessCalled)
    }

    @Test
    fun `deleteUserPost shows error on failure`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val userPost = CatCardData.UserPost("post1", "user1", "url", "text", "name", null, null)
        coEvery { userPostsRepository.deleteUserPost("post1") } throws Exception("Delete failed")

        var onSuccessCalled = false
        viewModel.deleteUserPost(userPost.id) {
            onSuccessCalled = true
        }
        advanceUntilIdle()

        assertEquals("Failed to delete post", viewModel.snackbarMessage)
        assertFalse(onSuccessCalled)
    }

    // ============ updateChoosedBreeds Tests ============

    @Test
    fun `updateChoosedBreeds updates breed selection`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateChoosedBreeds("abys", true)
        advanceUntilIdle()

        assertTrue(viewModel.choosedBreeds["abys"] == true)
    }

    @Test
    fun `updateChoosedBreeds can unselect breed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateChoosedBreeds("abys", true)
        advanceUntilIdle()
        viewModel.updateChoosedBreeds("abys", false)
        advanceUntilIdle()

        assertTrue(viewModel.choosedBreeds["abys"] == false)
    }

    // ============ updateFilterType Tests ============

    @Test
    fun `updateFilterType changes filter type`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedViewModel.FilterType.CATS_BY_BREED, viewModel.selectedFilterType)

        viewModel.updateFilterType(FeedViewModel.FilterType.USERS_POSTS)
        advanceUntilIdle()

        assertEquals(FeedViewModel.FilterType.USERS_POSTS, viewModel.selectedFilterType)
    }

    // ============ updateShowOnlyMyPosts Tests ============

    @Test
    fun `updateShowOnlyMyPosts changes flag`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.showOnlyMyPosts)

        viewModel.updateShowOnlyMyPosts(true)
        advanceUntilIdle()

        assertTrue(viewModel.showOnlyMyPosts)
    }

    // ============ refreshData Tests ============

    @Test
    fun `refreshData resets state and reloads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Add some items
        viewModel.loadDataPageIfNeeded(page = 0, replace = false)
        advanceUntilIdle()

        clearMocks(catgramApiRepository, answers = false)
        coEvery { catgramApiRepository.getBreedList() } returns testBreeds
        coEvery { catgramApiRepository.getCatsData(any(), any(), any()) } returns testCatsApiData

        viewModel.refreshData()
        advanceUntilIdle()

        // Should reload page 0
        coVerify { catgramApiRepository.getCatsData(any(), any(), 0) }
    }

    // ============ reset Tests ============

    @Test
    fun `reset clears all state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.reset()
        advanceUntilIdle()

        assertTrue(viewModel.items.isEmpty())
        assertTrue(viewModel.choosedBreeds.isEmpty())
        assertEquals(0, viewModel.scrollPositionIndex)
        assertEquals(0, viewModel.scrollPositionOffset)
    }

    // ============ toBreedName Tests ============

    @Test
    fun `toBreedName returns correct name`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Abyssinian", viewModel.toBreedName("abys"))
        assertEquals("Bengal", viewModel.toBreedName("beng"))
    }

    @Test(expected = IllegalStateException::class)
    fun `toBreedName throws for unknown breed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toBreedName("unknown")
    }

    // ============ Edge Cases and Error Handling ============
    @Test
    fun `handles network error gracefully`() = runTest {
        coEvery { catgramApiRepository.getBreedList() } throws RuntimeException("Network error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedViewModel.FeedUiState.Error, viewModel.uiState)
    }

    @Test
    fun `getBreedList network error ignored when filter type is users posts`() = runTest {
        coEvery { catgramApiRepository.getBreedList() } throws RuntimeException("Network error")

        val viewModel = createViewModel(filterType = FeedViewModel.FilterType.USERS_POSTS)
        advanceUntilIdle()

        assertEquals(FeedViewModel.FeedUiState.Ready, viewModel.uiState)
        coVerify { userPostsRepository.getNextUserPostsDataPage(any(), any()) }
    }

    @Test
    fun `handles SSLHandshakeException with specific message`() = runTest {
        coEvery { catgramApiRepository.getBreedList() } throws SSLHandshakeException("Certificate error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FeedViewModel.FeedUiState.Error, viewModel.uiState)
        assertEquals("Check date time", viewModel.snackbarMessage)
    }

    @Test
    fun `pagination loads next page correctly`() = runTest {
        val page0Cats = listOf(CatsApiData("cat0", "url0", emptyList()))
        val page1Cats = listOf(CatsApiData("cat1", "url1", emptyList()))

        coEvery { catgramApiRepository.getCatsData(any(), any(), 0) } returns page0Cats
        coEvery { catgramApiRepository.getCatsData(any(), any(), 1) } returns page1Cats

        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialCount = viewModel.items.size

        viewModel.loadDataPageIfNeeded(page = 1)
        advanceUntilIdle()

        assertTrue(viewModel.items.size > initialCount)
    }

    @Test
    fun `empty page marks all data loaded`() = runTest {
        coEvery { catgramApiRepository.getCatsData(any(), any(), 0) } returns testCatsApiData
        coEvery { catgramApiRepository.getCatsData(any(), any(), 1) } returns emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadDataPageIfNeeded(page = 1)
        advanceUntilIdle()

        // Try to load page 2
        clearMocks(catgramApiRepository, answers = false)
        coEvery { catgramApiRepository.getCatsData(any(), any(), any()) } returns testCatsApiData

        viewModel.loadDataPageIfNeeded(page = 2)
        advanceUntilIdle()

        // Should not call API since all data is loaded
        coVerify(exactly = 0) { catgramApiRepository.getCatsData(any(), any(), 2) }
    }

    @Test
    fun `page with only duplicate items marks all data loaded`() = runTest {
        // Edge case: if server returns items we already have, we stop pagination
        // This prevents infinite loops when API keeps returning same data
        val page0Cats = listOf(CatsApiData("cat1", "url1", emptyList()))

        coEvery { catgramApiRepository.getCatsData(any(), any(), 0) } returns page0Cats
        // Page 1 returns the same item (duplicate)
        coEvery { catgramApiRepository.getCatsData(any(), any(), 1) } returns page0Cats

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadDataPageIfNeeded(page = 1)
        advanceUntilIdle()

        // After receiving only duplicates, further loading should be prevented
        clearMocks(catgramApiRepository, answers = false)
        coEvery { catgramApiRepository.getCatsData(any(), any(), any()) } returns listOf(
            CatsApiData("cat2", "url2", emptyList())
        )

        viewModel.loadDataPageIfNeeded(page = 2)
        advanceUntilIdle()

        // Note: This is current behavior - duplicates mark loading as complete
        // to prevent infinite loops. New items on page 2 won't be loaded.
        coVerify(exactly = 0) { catgramApiRepository.getCatsData(any(), any(), 2) }
    }

    @Test
    fun `page parameter null uses next page after lastLoadedPage`() = runTest {
        coEvery { catgramApiRepository.getCatsData(any(), any(), 0) } returns testCatsApiData
        coEvery { catgramApiRepository.getCatsData(any(), any(), 1) } returns listOf(
            CatsApiData("cat4", "url4", emptyList())
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Call without explicit page - should load page 1
        viewModel.loadDataPageIfNeeded()
        advanceUntilIdle()

        coVerify { catgramApiRepository.getCatsData(any(), any(), 1) }
    }

    // ============ DataStore Persistence Tests ============

    @Test
    fun `breed selection persists across viewModel instances`() = runTest {
        // First ViewModel - select a breed
        val viewModel1 = createViewModel()
        advanceUntilIdle()

        viewModel1.updateChoosedBreeds("abys", true)
        advanceUntilIdle()

        // Create second ViewModel with same DataStore
        val viewModel2 = FeedViewModel(
            catgramApiRepository = catgramApiRepository,
            userPostsRepository = userPostsRepository,
            imageUploader = imageUploader,
            catDetector = catDetector,
            authProvider = authProvider,
            dataStore = testDataStore,
            context = mockApplication,
            dispatcherProvider = testDispatcherProvider,
            defaultFilterState = testDefaultFilterState,
        )
        advanceUntilIdle()

        // Should have persisted breed selection
        assertTrue(viewModel2.choosedBreeds["abys"] == true)
    }

    @Test
    fun `filter type persists across viewModel instances`() = runTest {
        // First ViewModel - change filter type
        val viewModel1 = createViewModel()
        advanceUntilIdle()

        viewModel1.updateFilterType(FeedViewModel.FilterType.USERS_POSTS)
        advanceUntilIdle()

        // Create second ViewModel with same DataStore
        val viewModel2 = FeedViewModel(
            catgramApiRepository = catgramApiRepository,
            userPostsRepository = userPostsRepository,
            imageUploader = imageUploader,
            catDetector = catDetector,
            authProvider = authProvider,
            dataStore = testDataStore,
            context = mockApplication,
            dispatcherProvider = testDispatcherProvider,
            defaultFilterState = testDefaultFilterState,
        )
        advanceUntilIdle()

        // Should have persisted filter type
        assertEquals(FeedViewModel.FilterType.USERS_POSTS, viewModel2.selectedFilterType)
    }
}
