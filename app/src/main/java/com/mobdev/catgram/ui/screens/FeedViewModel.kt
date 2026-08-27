package com.mobdev.catgram.ui.screens

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobdev.catgram.CatgramApplication
import com.mobdev.catgram.R
import com.mobdev.catgram.auth.AuthProvider
import com.mobdev.catgram.coroutines.DispatcherProvider
import com.mobdev.catgram.data.CatgramApiRepository
import com.mobdev.catgram.data.UserPostsRepository
import com.mobdev.catgram.logging.logger
import com.mobdev.catgram.ml.CatDetector
import com.mobdev.catgram.network.BreedInfo
import com.mobdev.catgram.network.CatApiConfigurationException
import com.mobdev.catgram.network.CatsData.CatsApiData
import com.mobdev.catgram.network.CatsData.CatsUserPostData
import com.mobdev.catgram.network.ImageUploader
import com.mobdev.catgram.ui.common.CatCardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class FeedViewModel(
    private val catgramApiRepository: CatgramApiRepository,
    private val userPostsRepository: UserPostsRepository,
    private val imageUploader: ImageUploader,
    private val catDetector: CatDetector,
    private val authProvider: AuthProvider,
    private val dataStore: DataStore<Preferences>,
    private val context: Application,
    private val dispatcherProvider: DispatcherProvider,
    private val defaultFilterState: DefaultFilterState,
) : ViewModel() {
    val pageSize = 10

    var items by mutableStateOf<List<CatCardData>>(listOf())
        private set
    private var itemsIds = mutableSetOf<String>()
    private var lastLoadedPage = -1

    var uiState: FeedUiState by mutableStateOf(FeedUiState.Ready)
        internal set
    var snackbarMessage: String? by mutableStateOf(null)
        private set
    private var loadingDataPageJob: Job? = null
    private var loadingFilterStateJob: Job? = null
    private var loadingBreedListJob: Job? = null
    private var isAllCatsDataLoaded = false
    private var duplicateOnlyPageCount = 0
    private var isFilterConfigurationLoaded = false

    var selectedFilterType by mutableStateOf(FilterType.CATS_BY_BREED)
        private set
    var showOnlyMyPosts by mutableStateOf(false)
        private set
    var choosedBreeds by mutableStateOf<Map<String, Boolean>>(mapOf())
        private set
    private var breedIdToName: Map<String, String> = mapOf()
    var breedUiState: BreedUiState by mutableStateOf(BreedUiState.Ready)
        private set

    private val json = Json { ignoreUnknownKeys = true }

    var scrollPositionIndex: Int = 0
    var scrollPositionOffset: Int = 0

    var shouldScrollToTop: Boolean by mutableStateOf(false)
        private set

    var isCreatingPost: Boolean by mutableStateOf(false)
        private set

    val currentUser
        get() = authProvider.getCurrentUser()

    fun onScrolledToTop() {
        shouldScrollToTop = false
    }

    private fun breedsKey(userUid: String) = stringPreferencesKey("$userUid:breeds")

    private fun filterTypeKey(userUid: String) = stringPreferencesKey("$userUid:filterType")

    private fun showOnlyMyPostsKey(userUid: String) =
        stringPreferencesKey("$userUid:showOnlyMyPosts")

    private val breedListCacheKey = stringPreferencesKey("breed-list-cache:v1")


    init {
        if (authProvider.isSignedIn()) {
            loadFilterState()
        }
    }

    fun loadFilterState() {
        val userUid = authProvider.getCurrentUser()?.uid ?: return
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            if (loadingFilterStateJob?.isActive == true) return@launch
            uiState = FeedUiState.Loading(true)

            loadingFilterStateJob = viewModelScope.launch(dispatcherProvider.mainImmediate) {
                try {
                    val storedState = withContext(dispatcherProvider.io) {
                        val prefs = dataStore.data.first()
                        StoredFilterState(
                            choosedBreeds = getBreedsFromStore(prefs, userUid),
                            filterType = getFilterTypeFromStore(prefs, userUid),
                            showOnlyMyPosts = getShowOnlyMyPostsFromStore(prefs, userUid),
                            cachedBreedInfoList = getBreedListFromStore(prefs),
                        )
                    }

                    selectedFilterType = storedState.filterType
                    showOnlyMyPosts = storedState.showOnlyMyPosts
                    if (storedState.cachedBreedInfoList.isNotEmpty()) {
                        applyBreedList(
                            storedState.cachedBreedInfoList,
                            storedState.choosedBreeds,
                        )
                    } else {
                        // Preserve saved IDs for the feed request even before their display names
                        // have been refreshed from the API.
                        choosedBreeds = storedState.choosedBreeds
                    }
                    isFilterConfigurationLoaded = true
                    uiState = FeedUiState.Ready
                    loadCatsDataPage(page = 0, replace = true)
                    startBreedListRefresh(storedState.choosedBreeds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.e("load filter state failed: ${e.message}", e)
                    uiState = FeedUiState.Error
                    breedUiState = BreedUiState.Error(
                        reason = classifyBreedLoadError(e),
                        hasCachedData = false,
                    )
                    if (e is SSLHandshakeException) {
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
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
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
        val userUid = authProvider.getCurrentUser()?.uid ?: return
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            val newChoosedBreeds = choosedBreeds.plus(breed to isChoosed)
            choosedBreeds = newChoosedBreeds
            withContext(dispatcherProvider.io) {
                updateBreedsDataStore(newChoosedBreeds, userUid)
            }
        }
    }

    fun updateFilterType(type: FilterType) {
        val userUid = authProvider.getCurrentUser()?.uid ?: return
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            selectedFilterType = type
            withContext(dispatcherProvider.io) {
                updateFilterTypeDataStore(type, userUid)
            }
        }
    }

    fun updateShowOnlyMyPosts(newValue: Boolean) {
        val userUid = authProvider.getCurrentUser()?.uid ?: return
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            showOnlyMyPosts = newValue
            withContext(dispatcherProvider.io) {
                updateShowOnlyMyPostsDataStore(newValue, userUid)
            }
        }
    }

    fun createUserPost(imageUri: Uri, postText: String) {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            if (isCreatingPost) return@launch
            isCreatingPost = true

            try {
                val isCat = withContext(dispatcherProvider.default) {
                    catDetector.isCatImage(context, imageUri).getOrThrow()
                }
                if (!isCat) {
                    snackbarMessage = context.getString(R.string.snackbar_upload_cat_image)
                    return@launch
                }

                withContext(dispatcherProvider.io) {
                    val result = imageUploader.uploadImage(imageUri, context)
                    logger.d("Image upload completed; success=${result.isSuccess}")
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

    fun deleteUserPost(postId: String, onSuccess: () -> Unit) {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            try {
                withContext(dispatcherProvider.io) {
                    userPostsRepository.deleteUserPost(postId)
                }
                items = items.filter { it.id != postId }
                onSuccess()
                logger.d("post delete success")
            } catch (e: Throwable) {
                snackbarMessage = context.getString(R.string.snackbar_post_delete_failed)
                logger.e("Failed to delete post ${e.message}", e)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            partialReset()
            if (!isFilterStateLoaded()) {
                loadFilterState()
            } else {
                loadCatsDataPage(page = 0, replace = true)
            }
        }
    }

    fun reset() {
        viewModelScope.launch(dispatcherProvider.mainImmediate) {
            partialReset()
            loadingBreedListJob?.cancel()
            items = listOf()
            choosedBreeds = mapOf()
            breedIdToName = mapOf()
            breedUiState = BreedUiState.Ready
            isFilterConfigurationLoaded = false
        }
    }

    private fun isFilterStateLoaded() = isFilterConfigurationLoaded

    private fun partialReset() {
        uiState = FeedUiState.Ready
        itemsIds = mutableSetOf()
        isAllCatsDataLoaded = false
        duplicateOnlyPageCount = 0
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

    fun retryBreedList() {
        if (loadingBreedListJob?.isActive == true) return
        startBreedListRefresh(choosedBreeds)
    }

    private fun startBreedListRefresh(savedSelections: Map<String, Boolean>) {
        loadingBreedListJob?.cancel()
        loadingBreedListJob = viewModelScope.launch(dispatcherProvider.mainImmediate) {
            val hasCachedData = breedIdToName.isNotEmpty()
            breedUiState = BreedUiState.Loading(hasCachedData)
            try {
                val breedInfoList = withContext(dispatcherProvider.io) {
                    catgramApiRepository.getBreedList().also {
                        if (it.isEmpty()) throw EmptyBreedListException()
                    }
                }
                val currentSelections = choosedBreeds.ifEmpty { savedSelections }
                val updatedSelections = buildBreedSelection(breedInfoList, currentSelections)
                applyBreedList(breedInfoList, updatedSelections)
                breedUiState = BreedUiState.Ready
                try {
                    withContext(dispatcherProvider.io) {
                        updateBreedListCacheDataStore(breedInfoList)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.e("cache breed list failed: ${e.message}", e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("load breed list failed: ${e.message}", e)
                breedUiState = BreedUiState.Error(
                    reason = classifyBreedLoadError(e),
                    hasCachedData = hasCachedData,
                )
                if (e is SSLHandshakeException) {
                    snackbarMessage = context.getString(R.string.snackbar_check_date_time)
                }
            }
        }
    }

    private fun applyBreedList(
        breedInfoList: List<BreedInfo>,
        selectedBreeds: Map<String, Boolean>,
    ) {
        breedIdToName = breedInfoList.associate { it.id to it.name }
        choosedBreeds = buildBreedSelection(breedInfoList, selectedBreeds)
    }

    private fun buildBreedSelection(
        breedInfoList: List<BreedInfo>,
        selectedBreeds: Map<String, Boolean>,
    ): Map<String, Boolean> {
        return breedInfoList.associate { breed ->
            breed.id to (selectedBreeds[breed.id] == true)
        }
    }

    private fun classifyBreedLoadError(error: Throwable): BreedLoadError = when (error) {
        is CatApiConfigurationException -> BreedLoadError.CONFIGURATION
        is SSLHandshakeException -> BreedLoadError.DATE_TIME
        is HttpException -> when (error.code()) {
            401, 403 -> BreedLoadError.AUTHENTICATION
            in 500..599 -> BreedLoadError.SERVER
            else -> BreedLoadError.RESPONSE
        }
        is EmptyBreedListException,
        is SerializationException -> BreedLoadError.RESPONSE
        is IOException -> BreedLoadError.NETWORK
        else -> BreedLoadError.UNKNOWN
    }

    private fun loadCatsDataPage(page: Int, replace: Boolean) {
        logger.d("loadCatsDataPage $page")

        uiState = FeedUiState.Loading(page == 0)

        val currentFilterType = selectedFilterType
        val currentShowOnlyMyPosts = showOnlyMyPosts
        val currentChoosedBreeds = choosedBreeds.filter { it.value }.keys.toList()

        loadingDataPageJob = viewModelScope.launch(dispatcherProvider.mainImmediate) {
            try {
                val catsData = withContext(dispatcherProvider.io) {
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

                if (catsData.isEmpty()) {
                    isAllCatsDataLoaded = true
                    uiState = FeedUiState.Ready
                    return@launch
                }
                lastLoadedPage = page
                val newItems = catsData.filter { !itemsIds.contains(it.id) }
                if (newItems.isEmpty()) {
                    duplicateOnlyPageCount++
                    if (duplicateOnlyPageCount >= MAX_CONSECUTIVE_DUPLICATE_PAGES) {
                        isAllCatsDataLoaded = true
                        uiState = FeedUiState.Ready
                    } else {
                        loadCatsDataPage(page + 1, replace)
                    }
                    return@launch
                }
                duplicateOnlyPageCount = 0
                itemsIds.addAll(newItems.map { it.id })
                items = if (replace) newItems else items + newItems
                logger.d("items: ${items.size}")
                uiState = FeedUiState.Ready
                if (page == 0) {
                    shouldScrollToTop = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.e("load cats data failed: ${error.message}", error)
                uiState = FeedUiState.Error
                snackbarMessage = context.getString(R.string.snackbar_load_data_failed)
            }
        }
    }

    private suspend fun updateBreedsDataStore(
        newValue: Map<String, Boolean>,
        userUid: String,
    ) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(breedsKey(userUid), json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private suspend fun updateBreedListCacheDataStore(breedInfoList: List<BreedInfo>) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(breedListCacheKey, json.encodeToJsonElement(breedInfoList).toString())
            }
        }
    }

    private fun getBreedListFromStore(prefs: Preferences): List<BreedInfo> {
        return prefs[breedListCacheKey]?.let {
            try {
                json.decodeFromString<List<BreedInfo>>(it)
            } catch (_: Throwable) {
                null
            }
        }.orEmpty()
    }

    private fun getBreedsFromStore(
        prefs: Preferences,
        userUid: String,
    ): Map<String, Boolean> {
        return prefs[breedsKey(userUid)]?.let {
            try {
                json.decodeFromString<Map<String, Boolean>>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: defaultFilterState.choosedBreeds
    }

    private suspend fun updateFilterTypeDataStore(
        newValue: FilterType,
        userUid: String,
    ) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(filterTypeKey(userUid), json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getFilterTypeFromStore(
        prefs: Preferences,
        userUid: String,
    ): FilterType {
        return prefs[filterTypeKey(userUid)]?.let {
            try {
                json.decodeFromString<FilterType>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: defaultFilterState.filterType
    }

    private suspend fun updateShowOnlyMyPostsDataStore(
        newValue: Boolean,
        userUid: String,
    ) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(showOnlyMyPostsKey(userUid), json.encodeToJsonElement(newValue).toString())
            }
        }
    }

    private fun getShowOnlyMyPostsFromStore(
        prefs: Preferences,
        userUid: String,
    ): Boolean {
        return prefs[showOnlyMyPostsKey(userUid)]?.let {
            try {
                json.decodeFromString<Boolean>(it)
            } catch (_: Throwable) {
                null
            }
        } ?: defaultFilterState.showOnlyMyPosts
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
        private const val MAX_CONSECUTIVE_DUPLICATE_PAGES = 3
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as CatgramApplication
                val container = application.container
                FeedViewModel(
                    catgramApiRepository = container.catgramApiRepository,
                    userPostsRepository = container.userPostsRepository,
                    imageUploader = container.imageUploader,
                    catDetector = container.catDetector,
                    authProvider = container.authProvider,
                    dataStore = container.feedDataStore,
                    dispatcherProvider = container.dispatcherProvider,
                    context = application,
                    defaultFilterState = DefaultFilterState(mapOf(), FilterType.USERS_POSTS, false)
                )
            }
        }
    }

    sealed interface FeedUiState {
        data object Ready : FeedUiState
        data class Loading(val isFirstPage: Boolean) : FeedUiState
        data object Error : FeedUiState
    }

    sealed interface BreedUiState {
        data object Ready : BreedUiState
        data class Loading(val hasCachedData: Boolean) : BreedUiState
        data class Error(
            val reason: BreedLoadError,
            val hasCachedData: Boolean,
        ) : BreedUiState
    }

    enum class BreedLoadError {
        CONFIGURATION,
        AUTHENTICATION,
        DATE_TIME,
        NETWORK,
        SERVER,
        RESPONSE,
        UNKNOWN,
    }

    enum class FilterType {
        USERS_POSTS,
        CATS_BY_BREED
    }

    data class DefaultFilterState(
        val choosedBreeds: Map<String, Boolean>,
        val filterType: FilterType,
        val showOnlyMyPosts: Boolean,
    )

    private data class StoredFilterState(
        val choosedBreeds: Map<String, Boolean>,
        val filterType: FilterType,
        val showOnlyMyPosts: Boolean,
        val cachedBreedInfoList: List<BreedInfo>,
    )

    private class EmptyBreedListException : IOException("The Cat API returned an empty breed list")
}
