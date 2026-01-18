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
import com.mobdev.catgram.network.BreedInfo
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
import javax.net.ssl.SSLHandshakeException

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
    private var loadingDataPageJob: Job? = null
    private var loadingFilterStateJob: Job? = null
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

    var isCreatingPost: Boolean by mutableStateOf(false)
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
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (uiState is FeedUiState.Loading) return@launch
            uiState = FeedUiState.Loading(true)

            loadingFilterStateJob = viewModelScope.launch(Dispatchers.Main.immediate) {
                try {
                    val (result, filterType, showOnlyMyPostsEnabled, breedInfoList) = withContext(
                        Dispatchers.IO
                    ) {
                        val breedInfoList = catgramApiRepository.getBreedList()
                        val breeds = breedInfoList.map { it.id }
                        val prefs = context.dataStore.data.first()
                        val breedsFromStore = getBreedsFromStore(prefs)
                        val breedsResult = breeds.associateWith { false }.let {
                            it.plus(breedsFromStore.filter { entry -> breeds.contains(entry.key) && entry.value })
                        }
                        updateBreedsDataStore(breedsResult)
                        FilterStateData(
                            breedsResult,
                            getFilterTypeFromStore(prefs),
                            getShowOnlyMyPostsFromStore(prefs),
                            breedInfoList
                        )
                    }

                    selectedFilterType = filterType
                    showOnlyMyPosts = showOnlyMyPostsEnabled
                    breedIdToName = breedInfoList.associate { it.id to it.name }
                    choosedBreeds = result
                    uiState = FeedUiState.Ready
                    loadCatsDataPage(page = 0, replace = true)
                } catch (e: Throwable) {
                    logger.e("load filter state failed: ${e.message}", e)
                    uiState = FeedUiState.Error
                    if (e is SSLHandshakeException || e.cause is SSLHandshakeException) {
                        snackbarMessage = context.getString(R.string.snackbar_check_date_time)
                    }
                }
            }
        }
    }

    fun loadDataPageIfNeeded(
        page: Int? = null,
        checkErrorState: Boolean = true,
        replace: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            logger.d("loadMoreCatsItemsIfNeeded page: $page, uiState: $uiState")
            if (uiState is FeedUiState.Loading) {
                return@launch
            }

            if (checkErrorState && uiState == FeedUiState.Error) {
                return@launch
            }

            val page = page ?: (lastLoadedPage + 1)
            if (!isAllCatsDataLoaded && isFilterStateLoaded() && page > lastLoadedPage) {
                loadCatsDataPage(page, replace)
            }
        }
    }

    fun updateChoosedBreeds(breed: String, isChoosed: Boolean) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val newChoosedBreeds = choosedBreeds.plus(breed to isChoosed)
            choosedBreeds = newChoosedBreeds
            withContext(Dispatchers.IO) {
                updateBreedsDataStore(newChoosedBreeds)
            }
        }
    }

    fun updateFilterType(type: FilterType) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            selectedFilterType = type
            withContext(Dispatchers.IO) {
                updateFilterTypeDataStore(type)
            }
        }
    }

    fun updateShowOnlyMyPosts(newValue: Boolean) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            showOnlyMyPosts = newValue
            withContext(Dispatchers.IO) {
                updateShowOnlyMyPostsDataStore(newValue)
            }
        }
    }

    fun createUserPost(imageUri: Uri, postText: String) {
        // Check and set state on Main thread to prevent race condition
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isCreatingPost) return@launch
            isCreatingPost = true

            try {
                val isCat = withContext(Dispatchers.IO) {
                    catDetector.isCatImage(context, imageUri).getOrThrow()
                }
                if (!isCat) {
                    snackbarMessage = context.getString(R.string.snackbar_upload_cat_image)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val result = imageUploader.uploadImage(imageUri, context)
                    logger.d("img update res $result")
                    val imageUrl = result.getOrThrow().url
                    userPostsRepository.addUserPost(imageUrl, postText, context)
                }

                if (selectedFilterType == FilterType.USERS_POSTS) {
                    refreshData()
                }
                snackbarMessage = context.getString(R.string.snackbar_post_created)
                logger.d("post create success")
            } catch (e: Throwable) {
                logger.e("post create failed: ${e.message}", e)
                snackbarMessage = context.getString(R.string.snackbar_post_create_failed)
            } finally {
                isCreatingPost = false
            }
        }
    }

    fun clearSnackbarMessage() {
        snackbarMessage = null
    }

    fun deleteUserPost(post: CatCardData.UserPost) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                withContext(Dispatchers.IO) {
                    userPostsRepository.deleteUserPost(post.id)
                }
                items = items.filter { it.id != post.id }
                logger.d("post delete success")
            } catch (e: Throwable) {
                snackbarMessage = context.getString(R.string.snackbar_post_delete_failed)
                logger.e("Failed to delete post ${e.message}", e)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            partialReset()
            if (!isFilterStateLoaded()) {
                loadFilterState()
            } else {
                loadCatsDataPage(page = 0, replace = true)
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            partialReset()
            items = listOf()
            choosedBreeds = mapOf()
            breedIdToName = mapOf()
        }
    }

    private fun isFilterStateLoaded() = choosedBreeds.isNotEmpty()

    private fun partialReset() {
        uiState = FeedUiState.Ready
        itemsIds = mutableSetOf()
        isAllCatsDataLoaded = false
        lastLoadedPage = -1
        userPostsRepository.reset()
        loadingDataPageJob?.cancel()
        loadingFilterStateJob?.cancel()
        scrollPositionIndex = 0
        scrollPositionOffset = 0
    }

    fun toBreedName(breedId: String): String {
        return breedIdToName[breedId] ?: throw IllegalStateException("Wrong breed id")
    }

    private fun loadCatsDataPage(page: Int, replace: Boolean) {
        logger.d("loadCatsDataPage $page")

        uiState = FeedUiState.Loading(page == 0)

        val currentFilterType = selectedFilterType
        val currentShowOnlyMyPosts = showOnlyMyPosts
        val currentChoosedBreeds = choosedBreeds.filter { it.value }.keys.toList()

        loadingDataPageJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                val catsData = withContext(Dispatchers.IO) {
                    when (currentFilterType) {
                        FilterType.USERS_POSTS -> userPostsRepository.getNextUserPostsDataPage(
                            pageSize,
                            currentShowOnlyMyPosts
                        ).toUserPostCardDataList()

                        FilterType.CATS_BY_BREED -> catgramApiRepository.getCatsData(
                            pageSize,
                            currentChoosedBreeds,
                            page
                        ).toCatsApiCardDataList()
                    }
                }

                val newItems = catsData.filter { !itemsIds.contains(it.id) }
                if (newItems.isEmpty()) {
                    isAllCatsDataLoaded = true
                    uiState = FeedUiState.Ready
                    return@launch
                }
                lastLoadedPage = page
                itemsIds.addAll(newItems.map { it.id })
                items = if (replace) newItems else items + newItems
                logger.d("items: ${items.size}")
                uiState = FeedUiState.Ready
                if (page == 0) {
                    shouldScrollToTop = true
                }
            } catch (error: Throwable) {
                logger.e("load cats data failed: ${error.message}", error)
                uiState = FeedUiState.Error
                snackbarMessage = context.getString(R.string.snackbar_load_data_failed)
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

    private data class FilterStateData(
        val choosedBreeds: Map<String, Boolean>,
        val filterType: FilterType,
        val showOnlyMyPosts: Boolean,
        val breedInfoList: List<BreedInfo>
    )
}