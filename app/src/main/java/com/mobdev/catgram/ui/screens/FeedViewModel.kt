package com.mobdev.catgram.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.getCurrentUserOrThrow
import com.mobdev.catgram.auth.isSignedIn
import com.mobdev.catgram.data.CatgramApiRepository
import com.mobdev.catgram.data.UserPostsRepository
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ml.CatDetector
import com.mobdev.catgram.network.CatsData.CatsApiData
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import com.mobdev.catgram.network.ImageUploader
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class FeedViewModel(
    private val catgramApiRepository: CatgramApiRepository,
    private val userPostsRepository: UserPostsRepository,
    private val imageUploader: ImageUploader,
    private val catDetector: CatDetector,
    private val context: Application,
) : ViewModel() {
    val pageSize = 10

    var items by mutableStateOf<List<CatCardData>>(listOf())
        private set
    private var itemsIds = mutableSetOf<String>()
    private var lastLoadedPage = -1

    var uiState: FeedUiState by mutableStateOf(FeedUiState.Ready)
        private set
    var snackbarMessage: String? by mutableStateOf(null)
        private set
    private var loadingJob: Job? = null
    private var isAllCatsDataLoaded = false

    var selectedFilterType by mutableStateOf(FilterType.CATS_BY_BREED)
        private set
    var showOnlyMyPosts by mutableStateOf(false)
        private set
    var choosedBreeds by mutableStateOf<Map<String, Boolean>>(mapOf())
        private set
    private var breedIdToName: Map<String, String> = mapOf()

    private val json = Json { ignoreUnknownKeys = true }

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    var shouldScrollToTop: Boolean by mutableStateOf(false)
        private set

    fun onScrolledToTop() {
        shouldScrollToTop = false
    }

    private val breedsKey: Preferences.Key<String>
            by lazy {
                val userUid = getCurrentUserOrThrow().uid
                stringPreferencesKey("$userUid:breeds")
            }

    private val filterTypeKey: Preferences.Key<String>
            by lazy {
                val userUid = getCurrentUserOrThrow().uid
                stringPreferencesKey("$userUid:filterType")
            }

    private val showOnlyMyPostsKey: Preferences.Key<String>
            by lazy {
                val userUid = getCurrentUserOrThrow().uid
                stringPreferencesKey("$userUid:showOnlyMyPosts")
            }


    init {
        if (isSignedIn()) {
            loadFilterState()
        }
    }

    fun loadFilterState() {
        uiState = FeedUiState.Loading(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val breedInfoList = catgramApiRepository.getBreedList()
                val breeds = breedInfoList.map { it.id }

                context.dataStore.data.first().let { prefs ->
                    val breedsFromStore = getBreedsFromStore(prefs)
                    val result = breeds.associateWith { false }.let {
                        it.plus(breedsFromStore.filter { entry -> breeds.contains(entry.key) && entry.value })
                    }
                    updateBreedsDataStore(result)
                    val filterType = getFilterTypeFromStore(prefs)
                    val showOnlyMyPostsEnabled = getShowOnlyMyPostsFromStore(prefs)
                    withContext(Dispatchers.Main) {
                        selectedFilterType = filterType
                        showOnlyMyPosts = showOnlyMyPostsEnabled
                        breedIdToName = breedInfoList.associate { it.id to it.name }
                        choosedBreeds = result
                        uiState = FeedUiState.Ready
                        loadDataPageIfNeeded(page = 0,)
                    }
                }
            } catch (e: Throwable) {
                logger.e( "load choosed breeds failed: ${e.message}")
                uiState = FeedUiState.Error
            }
        }
    }

    fun loadDataPageIfNeeded(
        page: Int? = null,
        checkErrorState: Boolean = true,
        replace: Boolean = false
    ) {
        logger.d( "loadMoreCatsItemsIfNeeded page: $page, uiState: $uiState")
        if (uiState is FeedUiState.Loading) {
            return
        }

        if (checkErrorState && uiState == FeedUiState.Error) {
            return
        }

        val page = page ?: (lastLoadedPage + 1)
        if (!isAllCatsDataLoaded && isFilterStateLoaded() && page > lastLoadedPage) {
            loadCatsDataPage(page, replace)
        }
    }

    fun updateChoosedBreeds(breed: String, isChoosed: Boolean) {
        val newChoosedBreeds = choosedBreeds.plus(breed to isChoosed)
        viewModelScope.launch(Dispatchers.IO) {
            updateBreedsDataStore(newChoosedBreeds)
            withContext(Dispatchers.Main) {
                choosedBreeds = newChoosedBreeds
            }
        }
    }

    fun updateFilterType(type: FilterType) {
        viewModelScope.launch(Dispatchers.IO) {
            updateFilterTypeDataStore(type)
            withContext(Dispatchers.Main) {
                selectedFilterType = type
            }
        }
    }

    fun updateShowOnlyMyPosts(newValue: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            updateShowOnlyMyPostsDataStore(newValue)
            withContext(Dispatchers.Main) {
                showOnlyMyPosts = newValue
            }
        }
    }

    fun createUserPost(imageUri: Uri, postText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val catDetectionResult = catDetector.isCatImage(context, imageUri)
                val isCat = catDetectionResult.getOrThrow()
                if (!isCat) {
                    snackbarMessage = context.getString(R.string.snackbar_upload_cat_image)
                    return@launch
                }

                val result = imageUploader.uploadImage(imageUri, context)
                logger.d( "img update res $result")
                val imageUrl = result.getOrThrow().url
                userPostsRepository.addUserPost(imageUrl, postText, context)
                if (selectedFilterType == FilterType.USERS_POSTS) {
                    refreshData()
                }
                snackbarMessage = context.getString(R.string.snackbar_post_created)
            } catch (e: Throwable) {
                logger.e( "post create failed: ${e.message}")
                snackbarMessage = context.getString(R.string.snackbar_post_create_failed)
            }
        }
    }

    fun clearSnackbarMessage() {
        snackbarMessage = null
    }

    fun deleteUserPost(post: CatCardData.UserPost) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userPostsRepository.deleteUserPost(post.id)
                withContext(Dispatchers.Main) {
                    items = items.filter { it.id != post.id }
                }
            } catch (e: Throwable) {
                snackbarMessage = context.getString(R.string.snackbar_post_delete_failed)
                logger.e( "Failed to delete post ${e.message}")
            }
        }
    }

    fun refreshData() {
        resetItems()
        if (!isFilterStateLoaded()) {
            loadFilterState()
        } else {
            loadDataPageIfNeeded(page = 0, replace = true)
        }
    }

    fun reset() {
        resetItems()
        items = listOf()
        choosedBreeds = mapOf()
        breedIdToName = mapOf()
    }

    private fun isFilterStateLoaded() = choosedBreeds.isNotEmpty()

    private fun resetItems() {
        uiState = FeedUiState.Ready
        itemsIds = mutableSetOf()
        isAllCatsDataLoaded = false
        lastLoadedPage = -1
        userPostsRepository.reset()
        loadingJob?.cancel()
        scrollPositionIndex = 0
        scrollPositionOffset = 0
    }

    fun toBreedName(breedId: String): String {
        return breedIdToName[breedId] ?: throw IllegalStateException("Wrong breed id")
    }

    private fun loadCatsDataPage(currentPage: Int, replace: Boolean) {
        logger.d( "loadCatsDataPage $currentPage")
        uiState = FeedUiState.Loading(currentPage == 0)

        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val catsData = when (selectedFilterType) {
                    FilterType.USERS_POSTS -> userPostsRepository.getNextUserPostsDataPage(
                        pageSize,
                        showOnlyMyPosts
                    ).toUserPostCardDataList()

                    FilterType.CATS_BY_BREED -> catgramApiRepository.getCatsData(
                        pageSize,
                        choosedBreeds.filter { it.value }.keys.toList(),
                        currentPage
                    ).toCatsApiCardDataList()
                }

                withContext(Dispatchers.Main) {
                    val newItems = catsData.filter { !itemsIds.contains(it.id) }
                    if (newItems.isEmpty()) {
                        isAllCatsDataLoaded = true
                        uiState = FeedUiState.Ready
                        return@withContext
                    }
                    lastLoadedPage = currentPage
                    itemsIds.addAll(newItems.map { it.id })
                    items = if (replace) newItems else items + newItems
                    logger.d( "items: ${items.size}")
                    uiState = FeedUiState.Ready
                    if (currentPage == 0) {
                        shouldScrollToTop = true
                    }
                }
            } catch (error: Throwable) {
                logger.e( "load cats data failed: ${error.message}")
                uiState = FeedUiState.Error
            }
        }
    }

    private suspend fun updateBreedsDataStore(newValue: Map<String, Boolean>) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(breedsKey, json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getBreedsFromStore(prefs: Preferences): Map<String, Boolean> {
        return prefs[breedsKey]?.let {
            try {
                json.decodeFromString<Map<String, Boolean>>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: mapOf()
    }

    private suspend fun updateFilterTypeDataStore(newValue: FilterType) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(filterTypeKey, json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getFilterTypeFromStore(prefs: Preferences): FilterType {
        return prefs[filterTypeKey]?.let {
            try {
                json.decodeFromString<FilterType>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: FilterType.CATS_BY_BREED
    }

    private suspend fun updateShowOnlyMyPostsDataStore(newValue: Boolean) {
        context.dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(showOnlyMyPostsKey, json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getShowOnlyMyPostsFromStore(prefs: Preferences): Boolean {
        return prefs[showOnlyMyPostsKey]?.let {
            try {
                json.decodeFromString<Boolean>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: false
    }

    private fun List<CatsApiData>.toCatsApiCardDataList(): List<CatCardData> =
        map { CatCardData.CatsApi(it.id, it.url, it.breeds) }

    private fun List<CatsUserPostData>.toUserPostCardDataList(): List<CatCardData> =
        map {
            CatCardData.UserPost(
                it.id,
                it.userId,
                it.url,
                it.text,
                it.displayName,
                it.avatarUrl,
                it.timestamp
            )
        }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as CatgramApplication)
                val catgramRepository = application.container.catgramApiRepository
                val userPostsRepository = application.container.userPostsRepository
                val imageUploader = application.container.imageUploader
                val catDetector = application.container.catDetector
                FeedViewModel(
                    catgramApiRepository = catgramRepository,
                    userPostsRepository = userPostsRepository,
                    imageUploader = imageUploader,
                    catDetector = catDetector,
                    context = application
                )
            }
        }
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("feed-filter")
    }

    sealed interface FeedUiState {
        data object Ready : FeedUiState
        data class Loading(val isFirstPage: Boolean) : FeedUiState
        data object Error : FeedUiState
    }

    enum class FilterType {
        USERS_POSTS,
        CATS_BY_BREED
    }
}